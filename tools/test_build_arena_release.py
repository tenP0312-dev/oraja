from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch

from build_arena_release import ReleaseBuildError, build_release, release_tag
from check_feature_parity import FeatureParityError


class ParallelArenaBuildTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.windows = self._worktree("windows")
        self.macos = self._worktree("macos")
        self.jdk = self.root / "jdk"
        (self.jdk / "bin").mkdir(parents=True)
        (self.jdk / "bin/java").write_text("java", encoding="utf-8")
        (self.jdk / "release").write_text('JAVA_VERSION="17.0.12"\n', encoding="utf-8")
        self.barrier = threading.Barrier(2)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _worktree(self, name: str) -> Path:
        root = self.root / name
        (root / "core/src/bms/player/beatoraja").mkdir(parents=True)
        (root / "core/src/bms/player/beatoraja/Version.java").write_text(
            'public static final String ARENA_CLIENT_VERSION = "1.2.3";\n',
            encoding="utf-8",
        )
        (root / "core/build.gradle.kts").write_text(
            'archiveVersion.set("1.2.3")\n', encoding="utf-8"
        )
        (root / "gradlew").write_text("gradle", encoding="utf-8")
        return root

    def _runner(self, command, *, cwd, **_kwargs):
        if command[:3] == ["git", "rev-parse", "HEAD"]:
            return subprocess.CompletedProcess(command, 0, "abcdef1234567890\n")
        if command[:2] == ["git", "status"]:
            return subprocess.CompletedProcess(command, 0, "")
        if command[:3] == ["git", "submodule", "status"]:
            return subprocess.CompletedProcess(command, 0, "")
        self.barrier.wait(timeout=2)
        lane = "windows-x86-64" if "-Dplatform=windows" in command else "macos-aarch64"
        artifact = Path(cwd) / "dist" / f"BMS-IR-Arena-oraja-1.2.3-{lane}.jar"
        artifact.parent.mkdir()
        artifact.write_bytes(lane.encode())
        return subprocess.CompletedProcess(command, 0, f"built {lane}\n")

    def test_builds_two_lanes_concurrently_and_writes_state(self) -> None:
        output = self.root / "output"
        with patch("build_arena_release.validate_release_repository"):
            state_path = build_release(
                windows_worktree=self.windows,
                macos_worktree=self.macos,
                java_home=self.jdk,
                output_dir=output,
                runner=self._runner,
            )
        state = json.loads(state_path.read_text(encoding="utf-8"))
        self.assertEqual(2, state["schema_version"])
        self.assertEqual("built", state["status"])
        self.assertEqual("abcdef1234567890", state["source_commit"])
        self.assertEqual(2, len(state["lanes"]))
        self.assertTrue((output / "artifacts/BMS-IR-Arena-oraja-1.2.3-windows-x86-64.jar").is_file())
        self.assertTrue((output / "logs/macos-aarch64.log").is_file())
        self.assertEqual(
            [
                "test-1.2.3-windows-x86-64",
                "test-1.2.3-macos-aarch64",
            ],
            [release["tag"] for release in state["github_releases"]],
        )
        for lane in ("windows-x86-64", "macos-aarch64"):
            asset = output / "github-releases" / lane / "Arena-oraja.jar"
            self.assertTrue(asset.is_file())
            release = next(
                item for item in state["github_releases"]
                if item["tag"] == release_tag("1.2.3", lane)
            )
            self.assertEqual("tenP0312-dev/oraja", release["repository"])
            self.assertEqual("Arena-oraja.jar", release["asset_name"])
            self.assertEqual(asset.stat().st_size, release["asset"]["size"])

    def test_release_tags_keep_same_named_platform_assets_separate(self) -> None:
        self.assertEqual(
            "test-1.2.3-windows-x86-64",
            release_tag("1.2.3", "windows-x86-64"),
        )
        self.assertEqual(
            "test-1.2.3-macos-aarch64",
            release_tag("1.2.3", "macos-aarch64"),
        )
        with self.assertRaisesRegex(ReleaseBuildError, "unsupported release lane"):
            release_tag("1.2.3", "linux-x86-64")

    def test_rejects_mismatched_worktree_commit_before_build(self) -> None:
        calls = 0

        def mismatch_runner(command, *, cwd, **kwargs):
            nonlocal calls
            calls += 1
            result = self._runner(command, cwd=cwd, **kwargs)
            if (
                command[:3] == ["git", "rev-parse", "HEAD"]
                and Path(cwd).resolve() == self.macos.resolve()
            ):
                return subprocess.CompletedProcess(command, 0, "1234567890abcdef\n")
            return result

        with self.assertRaisesRegex(ReleaseBuildError, "same commit"):
            with patch("build_arena_release.validate_release_repository"):
                build_release(
                    windows_worktree=self.windows,
                    macos_worktree=self.macos,
                    java_home=self.jdk,
                    output_dir=self.root / "unused",
                    runner=mismatch_runner,
                )
        self.assertGreater(calls, 0)

    def test_rejects_release_when_feature_parity_fails(self) -> None:
        with patch(
            "build_arena_release.validate_release_repository",
            side_effect=FeatureParityError("feature parity validation failed"),
        ):
            with self.assertRaisesRegex(ReleaseBuildError, "feature parity validation failed"):
                build_release(
                    windows_worktree=self.windows,
                    macos_worktree=self.macos,
                    java_home=self.jdk,
                    output_dir=self.root / "unused-parity",
                    runner=self._runner,
                )


if __name__ == "__main__":
    unittest.main()
