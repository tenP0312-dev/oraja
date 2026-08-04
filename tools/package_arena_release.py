#!/usr/bin/env python3
"""Build a self-contained BMS-IR Arena oraja archive with a Java runtime.

The ordinary non-bundled release remains the platform JAR plus the BMS-IR
plugin JAR.  This tool builds the additional Java-bundled ZIP without copying
player data, song databases, logs, credentials, or other mutable files from a
working beatoraja installation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import stat
import tempfile
import zipfile


VERSION = "0.4.14.16"
BODY_FILENAME = "Arena-oraja.jar"
PLUGIN_FILENAME = "bms_ir_arena_oraja_0.0.69.jar"
PLATFORM_SPECS = {
    "windows-x86-64": {
        "runtime_os": ("windows",),
        "runtime_arch": ("amd64", "x86_64", "x64"),
        "java": "bin/java.exe",
    },
    "macos-aarch64": {
        "runtime_os": ("mac", "darwin"),
        "runtime_arch": ("aarch64", "arm64"),
        "java": "bin/java",
    },
}
BASE_ASSET_DIRECTORIES = (
    "bgm",
    "defaultsound",
    "folder",
    "font",
    "manual",
    "random",
    "skin",
    "sound",
)
FORBIDDEN_NAMES = {
    ".ds_store",
    ".temp",
    "config",
    "course",
    "db-backups",
    "favorite",
    "http_download",
    "ipfs",
    "layout.ini",
    "log",
    "player",
    "practice",
    "rival",
    "songdata.db",
    "songinfo.db",
    "table",
}
ZIP_EPOCH = (2026, 1, 1, 0, 0, 0)
CONFIGURED_LAUNCHER_MARKER = b"BMSIR_ARENA_UPDATE_CONFIGURED_V1"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_tree(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Launcher app must not contain symlinks: {path}")
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix().encode("utf-8")
        digest.update(relative)
        digest.update(b"\0")
        digest.update(bytes([1 if path.stat().st_mode & 0o111 else 0]))
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
    return digest.hexdigest()


def macos_launcher_executable(launcher_app: Path) -> Path:
    if launcher_app.suffix != ".app" or not launcher_app.is_dir():
        raise ValueError("macOS packages require a reviewed .app launcher bundle")
    info_path = launcher_app / "Contents" / "Info.plist"
    try:
        info = plistlib.loads(info_path.read_bytes())
    except (OSError, plistlib.InvalidFileException) as exc:
        raise ValueError("macOS launcher has no valid Contents/Info.plist") from exc
    executable_name = str(info.get("CFBundleExecutable") or "").strip()
    executable = launcher_app / "Contents" / "MacOS" / executable_name
    if not executable_name or not executable.is_file():
        raise ValueError("macOS launcher bundle executable is missing")
    return executable


def parse_runtime_release(java_home: Path) -> dict[str, str]:
    release_path = java_home / "release"
    if not release_path.is_file():
        raise ValueError(f"Java runtime has no release file: {release_path}")
    values: dict[str, str] = {}
    for raw_line in release_path.read_text(encoding="utf-8").splitlines():
        key, separator, value = raw_line.partition("=")
        if separator:
            values[key.strip()] = value.strip().strip('"')
    return values


def java_major(version: str) -> int:
    match = re.match(r"(?:1\.)?(\d+)", version.strip())
    if not match:
        raise ValueError(f"Cannot parse Java version: {version!r}")
    return int(match.group(1))


def validate_inputs(
    *,
    platform: str,
    body_jar: Path,
    plugin_jar: Path,
    base_assets: Path,
    java_home: Path,
    project_license: Path,
    launcher_exe: Path | None,
    launcher_app: Path | None,
    confirmed: bool,
) -> dict[str, str]:
    if not confirmed:
        raise ValueError(
            "Refusing to package unreviewed assets; pass "
            "--confirm-base-assets-redistributable after checking their licenses"
        )
    spec = PLATFORM_SPECS[platform]
    expected_body_name = f"BMS-IR-Arena-oraja-{VERSION}-{platform}.jar"
    if body_jar.name != expected_body_name or not body_jar.is_file():
        raise ValueError(f"Expected reviewed body JAR named {expected_body_name}")
    if plugin_jar.name != PLUGIN_FILENAME or not plugin_jar.is_file():
        raise ValueError(f"Expected reviewed plugin JAR named {PLUGIN_FILENAME}")
    if not zipfile.is_zipfile(body_jar) or not zipfile.is_zipfile(plugin_jar):
        raise ValueError("Body and plugin inputs must both be valid JAR/ZIP files")
    if not (base_assets / "skin").is_dir():
        raise ValueError("Base assets must contain a distributable skin directory")
    if not project_license.is_file():
        raise ValueError(f"Project license is missing: {project_license}")
    release = parse_runtime_release(java_home)
    version = release.get("JAVA_VERSION", "")
    if java_major(version) < 21:
        raise ValueError(f"Java 21 or later is required; runtime reports {version!r}")
    runtime_os = release.get("OS_NAME", "").lower()
    runtime_arch = release.get("OS_ARCH", "").lower()
    if not any(token in runtime_os for token in spec["runtime_os"]):
        raise ValueError(f"Runtime OS {runtime_os!r} does not match {platform}")
    if runtime_arch not in spec["runtime_arch"]:
        raise ValueError(f"Runtime architecture {runtime_arch!r} does not match {platform}")
    if not (java_home / str(spec["java"])).is_file():
        raise ValueError(f"Runtime executable is missing: {spec['java']}")
    if not (java_home / "legal").is_dir():
        raise ValueError("Runtime legal notices are missing")
    if platform == "windows-x86-64":
        if launcher_app is not None:
            raise ValueError("Portable launcher app is only valid for macOS packages")
        if launcher_exe is None or not launcher_exe.is_file():
            raise ValueError("Windows packages require a reviewed portable launcher EXE")
        launcher_bytes = launcher_exe.read_bytes()
        if not launcher_bytes.startswith(b"MZ"):
            raise ValueError("Portable launcher input is not a Windows executable")
        if CONFIGURED_LAUNCHER_MARKER not in launcher_bytes:
            raise ValueError(
                "Portable launcher was built without the update endpoint or release verification key"
            )
    else:
        if launcher_exe is not None:
            raise ValueError("Portable launcher EXE is only valid for Windows packages")
        if launcher_app is None:
            raise ValueError("macOS packages require a reviewed portable launcher app")
        launcher_binary = macos_launcher_executable(launcher_app)
        launcher_bytes = launcher_binary.read_bytes()
        if not launcher_bytes.startswith(b"\xcf\xfa\xed\xfe"):
            raise ValueError("Portable launcher input is not a 64-bit macOS executable")
        if CONFIGURED_LAUNCHER_MARKER not in launcher_bytes:
            raise ValueError(
                "Portable launcher was built without the update endpoint or release verification key"
            )
        sha256_tree(launcher_app)
    return release


def safe_copy_tree(source: Path, destination: Path) -> None:
    for path in sorted(source.rglob("*")):
        relative = path.relative_to(source)
        target = destination / relative
        if path.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        elif path.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target, follow_symlinks=True)


def assert_safe_package_root(root: Path) -> None:
    forbidden = sorted(
        path.name for path in root.iterdir() if path.name.lower() in FORBIDDEN_NAMES
    )
    if forbidden:
        raise ValueError(f"Forbidden mutable paths entered package root: {forbidden}")


def write_launchers(root: Path, platform: str) -> None:
    if platform == "windows-x86-64":
        (root / "BMS-IR-Arena-config.bat").write_text(
            "@echo off\r\n"
            "pushd %~dp0\r\n"
            '"runtime\\bin\\java.exe" "-DcustomIRDirectory=%CD%\\ir" '
            f'-Xms1g -Xmx4g -jar {BODY_FILENAME}\r\n'
            "popd\r\n",
            encoding="utf-8",
        )
        return
    launcher = root / "BMS-IR-Arena-config.command"
    launcher.write_text(
        "#!/bin/sh\n"
        'cd "$(dirname "$0")" || exit 1\n'
        'exec "./runtime/bin/java" "-DcustomIRDirectory=$PWD/ir" '
        f'-Xms1g -Xmx4g -jar {BODY_FILENAME}\n',
        encoding="utf-8",
    )
    launcher.chmod(0o755)


def write_readme(
    root: Path,
    platform: str,
    launcher_filename: str | None,
    distribution_version: str,
) -> None:
    launcher = launcher_filename or "BMS-IR-Arena-config.command"
    fallback_launcher = (
        "BMS-IR-Arena-config.bat"
        if platform == "windows-x86-64"
        else "BMS-IR-Arena-config.command"
    )
    fallback = (
        f"\n従来どおり {fallback_launcher} から起動することもできます。\n"
        if launcher_filename
        else ""
    )
    (root / "README-BMS-IR-Arena.txt").write_text(
        f"Arena oraja {distribution_version} / Java 21 bundled\n"
        "================================================\n\n"
        f"1. {launcher} を起動します。\n"
        "2. BMS-IRのIR ID (190xxx) と登録時パスワードをIR設定へ入力します。\n"
        "3. IR設定の BMS-IR Arena をONにします。\n"
        "4. 本体起動後、Arenaオーバーレイの対戦タブからエントリーします。\n\n"
        f"同梱runtimeを使うため、システム側へのJava導入は不要です。{fallback}"
        "曲データ、プレイヤー設定、スコアDBは同梱していません。\n"
        "Arenaの詳細: https://www.bms-ir.org/new/arena\n\n"
        "English\n"
        f"Launch {launcher}, configure BMS-IR with your 190xxx ID and password, "
        "enable BMS-IR Arena, then enter from the Battle tab in the Arena overlay.\n"
        "The bundled runtime is used; no system Java installation is required.\n",
        encoding="utf-8",
    )


def zip_directory(root: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            relative = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(relative, ZIP_EPOCH)
            mode = path.stat().st_mode
            info.create_system = 3
            info.external_attr = (
                (stat.S_IFREG | stat.S_IMODE(mode)) & 0xFFFF
            ) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_release(
    *,
    platform: str,
    body_jar: Path,
    plugin_jar: Path,
    base_assets: Path,
    java_home: Path,
    project_license: Path,
    output_dir: Path,
    confirmed: bool,
    launcher_exe: Path | None = None,
    launcher_app: Path | None = None,
    test_build: bool = False,
    distribution_version: str | None = None,
) -> Path:
    distribution_version = (distribution_version or VERSION).strip()
    if not re.fullmatch(r"\d+(?:\.\d+){2,3}", distribution_version):
        raise ValueError("Distribution version must contain three or four numeric components")
    release = validate_inputs(
        platform=platform,
        body_jar=body_jar,
        plugin_jar=plugin_jar,
        base_assets=base_assets,
        java_home=java_home,
        project_license=project_license,
        launcher_exe=launcher_exe,
        launcher_app=launcher_app,
        confirmed=confirmed,
    )
    channel_suffix = "-test" if test_build else ""
    archive_name = (
        f"BMS-IR-Arena-oraja-{distribution_version}-{platform}"
        f"{channel_suffix}-java21.zip"
    )
    with tempfile.TemporaryDirectory(prefix="bmsir-arena-release-") as temporary:
        root = Path(temporary) / archive_name.removesuffix(".zip")
        root.mkdir()
        for directory in BASE_ASSET_DIRECTORIES:
            source = base_assets / directory
            if source.is_dir():
                safe_copy_tree(source, root / directory)
        shutil.copy2(body_jar, root / BODY_FILENAME)
        plugin_dir = root / "ir"
        plugin_dir.mkdir()
        shutil.copy2(plugin_jar, plugin_dir / PLUGIN_FILENAME)
        shutil.copy2(project_license, root / "LICENSE")
        safe_copy_tree(java_home, root / "runtime")
        launcher_filename = None
        if launcher_exe is not None:
            launcher_filename = "BMS-IR Arena Test.exe" if test_build else "BMS-IR Arena.exe"
            shutil.copy2(launcher_exe, root / launcher_filename)
        elif launcher_app is not None:
            launcher_filename = "BMS-IR Arena Test.app" if test_build else "BMS-IR Arena.app"
            safe_copy_tree(launcher_app, root / launcher_filename)
        (root / "bmsir-arena-version.txt").write_text(
            f"{distribution_version}\n",
            encoding="ascii",
        )
        write_launchers(root, platform)
        write_readme(root, platform, launcher_filename, distribution_version)
        manifest = {
            "product": "Arena oraja",
            "version": distribution_version,
            "channel": "test" if test_build else "stable",
            "platform": platform,
            "java_version": release.get("JAVA_VERSION", ""),
            "body_filename": body_jar.name,
            "body_sha256": sha256_file(body_jar),
            "plugin_filename": plugin_jar.name,
            "plugin_sha256": sha256_file(plugin_jar),
            "launcher_filename": launcher_filename,
            "launcher_sha256": (
                sha256_file(launcher_exe)
                if launcher_exe is not None
                else sha256_tree(launcher_app) if launcher_app is not None else None
            ),
        }
        (root / "release-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        assert_safe_package_root(root)
        output = output_dir / archive_name
        zip_directory(root, output)
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", choices=tuple(PLATFORM_SPECS), required=True)
    parser.add_argument("--body-jar", type=Path, required=True)
    parser.add_argument("--plugin-jar", type=Path, required=True)
    parser.add_argument("--base-assets", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--launcher-exe", type=Path)
    parser.add_argument("--launcher-app", type=Path)
    parser.add_argument("--test-build", action="store_true")
    parser.add_argument("--distribution-version")
    parser.add_argument("--project-license", type=Path, default=Path(__file__).resolve().parents[1] / "LICENSE")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--confirm-base-assets-redistributable", action="store_true")
    args = parser.parse_args()
    output = build_release(
        platform=args.platform,
        body_jar=args.body_jar.resolve(),
        plugin_jar=args.plugin_jar.resolve(),
        base_assets=args.base_assets.resolve(),
        java_home=args.java_home.resolve(),
        project_license=args.project_license.resolve(),
        output_dir=args.output_dir.resolve(),
        confirmed=args.confirm_base_assets_redistributable,
        launcher_exe=args.launcher_exe.resolve() if args.launcher_exe else None,
        launcher_app=args.launcher_app.resolve() if args.launcher_app else None,
        test_build=args.test_build,
        distribution_version=args.distribution_version,
    )
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
