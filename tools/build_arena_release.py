#!/usr/bin/env python3
"""Run the final Windows/macOS Arena body builds concurrently.

Each lane uses a separate clean worktree at the same reviewed commit. This
avoids Gradle output races while sharing the host's dependency cache and JDK.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from typing import Callable, Sequence


VERSION_JAVA_RE = re.compile(r'ARENA_CLIENT_VERSION\s*=\s*"([0-9.]+)"')
VERSION_GRADLE_RE = re.compile(r'archiveVersion\.set\("([0-9.]+)"\)')
JDK_VERSION_RE = re.compile(r'JAVA_VERSION="(?:1\.)?([0-9]+)')
LANES = {
    "windows-x86-64": ("windows", "x86-64"),
    "macos-aarch64": ("macos", "aarch64"),
}
Runner = Callable[..., subprocess.CompletedProcess[str]]


class ReleaseBuildError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _run_text(
    runner: Runner,
    command: Sequence[str],
    *,
    cwd: Path,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    return runner(
        list(command),
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def source_version(root: Path) -> str:
    version_source = root / "core/src/bms/player/beatoraja/Version.java"
    gradle_source = root / "core/build.gradle.kts"
    try:
        java_match = VERSION_JAVA_RE.search(version_source.read_text(encoding="utf-8"))
        gradle_match = VERSION_GRADLE_RE.search(gradle_source.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ReleaseBuildError(f"release version source is missing in {root}") from exc
    if not java_match or not gradle_match or java_match.group(1) != gradle_match.group(1):
        raise ReleaseBuildError(
            f"Arena version constants do not match in {root}: "
            f"java={java_match.group(1) if java_match else '-'} "
            f"gradle={gradle_match.group(1) if gradle_match else '-'}"
        )
    return java_match.group(1)


def validate_jdk(java_home: Path) -> None:
    java = java_home / "bin/java"
    release = java_home / "release"
    if not java.is_file() or not release.is_file():
        raise ReleaseBuildError(f"JDK is incomplete: {java_home}")
    match = JDK_VERSION_RE.search(release.read_text(encoding="utf-8"))
    if not match or int(match.group(1)) != 17:
        raise ReleaseBuildError("the final Gradle build requires a JDK 17 toolchain")


def validate_worktree(root: Path, runner: Runner) -> tuple[str, str]:
    if not (root / "gradlew").is_file():
        raise ReleaseBuildError(f"not an Arena oraja worktree: {root}")
    commit_result = _run_text(runner, ["git", "rev-parse", "HEAD"], cwd=root)
    if commit_result.returncode:
        raise ReleaseBuildError(f"cannot resolve worktree commit: {root}")
    commit = commit_result.stdout.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{7,64}", commit):
        raise ReleaseBuildError(f"invalid worktree commit: {root}")
    status_result = _run_text(
        runner, ["git", "status", "--porcelain", "--untracked-files=normal"], cwd=root
    )
    if status_result.returncode or status_result.stdout.strip():
        raise ReleaseBuildError(f"release worktree has tracked changes: {root}")
    submodule_result = _run_text(
        runner, ["git", "submodule", "status", "--recursive"], cwd=root
    )
    if submodule_result.returncode:
        raise ReleaseBuildError(f"cannot inspect submodules: {root}")
    invalid = [
        line
        for line in submodule_result.stdout.splitlines()
        if line and line[0] in {"-", "+", "U"}
    ]
    if invalid:
        raise ReleaseBuildError(f"release worktree submodules are not at reviewed commits: {root}")
    return commit, source_version(root)


def _artifact_identity(path: Path, *, display_path: str | None = None) -> dict[str, object]:
    if not path.is_file():
        raise ReleaseBuildError(f"expected release JAR was not built: {path}")
    return {
        "name": path.name,
        "path": display_path or str(path),
        "size": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def _run_lane(
    *,
    lane: str,
    root: Path,
    version: str,
    java_home: Path,
    staging: Path,
    runner: Runner,
    gradle_user_home: Path | None,
) -> dict[str, object]:
    platform, arch = LANES[lane]
    log_path = staging / "logs" / f"{lane}.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    command = [
        str(root / "gradlew"),
        "core:shadowJar",
        "--no-daemon",
        f"-Dplatform={platform}",
        f"-Darch={arch}",
    ]
    env = dict(os.environ)
    env["JAVA_HOME"] = str(java_home)
    env["ORG_GRADLE_JAVA_INSTALLATIONS_PATHS"] = str(java_home)
    if gradle_user_home is not None:
        env["GRADLE_USER_HOME"] = str(gradle_user_home)
    started = time.monotonic()
    result = _run_text(runner, command, cwd=root, env=env)
    elapsed = round(time.monotonic() - started, 3)
    log_path.write_text(result.stdout, encoding="utf-8")
    lane_state: dict[str, object] = {
        "lane": lane,
        "command": command,
        "elapsed_seconds": elapsed,
        "log": f"logs/{lane}.log",
        "returncode": result.returncode,
    }
    if result.returncode:
        return lane_state
    artifact = root / "dist" / f"BMS-IR-Arena-oraja-{version}-{lane}.jar"
    destination = staging / "artifacts" / artifact.name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(artifact, destination)
    try:
        lane_state["artifact"] = _artifact_identity(
            destination, display_path=f"artifacts/{destination.name}"
        )
    except ReleaseBuildError as exc:
        lane_state["returncode"] = 1
        lane_state["validation_error"] = str(exc)
    return lane_state


def build_release(
    *,
    windows_worktree: Path,
    macos_worktree: Path,
    java_home: Path,
    output_dir: Path,
    runner: Runner = subprocess.run,
    gradle_user_home: Path | None = None,
) -> Path:
    windows_worktree = windows_worktree.resolve()
    macos_worktree = macos_worktree.resolve()
    java_home = java_home.resolve()
    output_dir = output_dir.resolve()
    validate_jdk(java_home)
    windows_commit, windows_version = validate_worktree(windows_worktree, runner)
    macos_commit, macos_version = validate_worktree(macos_worktree, runner)
    if windows_commit != macos_commit:
        raise ReleaseBuildError("Windows and macOS worktrees are not at the same commit")
    if windows_version != macos_version:
        raise ReleaseBuildError("Windows and macOS worktrees do not declare the same version")
    if output_dir.exists():
        raise ReleaseBuildError(f"output directory already exists: {output_dir}")

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(
        tempfile.mkdtemp(prefix=f".{output_dir.name}-", dir=output_dir.parent)
    )
    try:
        roots = {
            "windows-x86-64": windows_worktree,
            "macos-aarch64": macos_worktree,
        }
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = {
                lane: executor.submit(
                    _run_lane,
                    lane=lane,
                    root=root,
                    version=windows_version,
                    java_home=java_home,
                    staging=staging,
                    runner=runner,
                    gradle_user_home=gradle_user_home,
                )
                for lane, root in roots.items()
            }
            lanes = [futures[lane].result() for lane in LANES]
        status = "built" if all(lane["returncode"] == 0 for lane in lanes) else "failed"
        state = {
            "schema_version": 1,
            "status": status,
            "version": windows_version,
            "source_commit": windows_commit,
            "java_home": str(java_home),
            "lanes": lanes,
        }
        state_path = staging / "build-state.json"
        state_path.write_text(
            json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        os.replace(staging, output_dir)
        if status != "built":
            raise ReleaseBuildError(f"one or more release lanes failed; see {output_dir / 'build-state.json'}")
        return output_dir / "build-state.json"
    except BaseException:
        if staging.exists():
            shutil.rmtree(staging, ignore_errors=True)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--windows-worktree", type=Path, required=True)
    parser.add_argument("--macos-worktree", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--gradle-user-home", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        print(
            build_release(
                windows_worktree=args.windows_worktree,
                macos_worktree=args.macos_worktree,
                java_home=args.java_home,
                gradle_user_home=args.gradle_user_home,
                output_dir=args.output_dir,
            )
        )
        return 0
    except (OSError, ReleaseBuildError) as exc:
        print(f"error: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
