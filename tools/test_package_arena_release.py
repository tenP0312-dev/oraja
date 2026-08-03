from pathlib import Path
import plistlib
import tempfile
import unittest
import zipfile

from package_arena_release import (
    CONFIGURED_LAUNCHER_MARKER,
    PLUGIN_FILENAME,
    build_release,
)


class ArenaReleasePackageTest(unittest.TestCase):
    def write_jar(self, path: Path) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")

    def fixture(self, root: Path) -> dict[str, Path]:
        body = root / "BMS-IR-Arena-oraja-0.4.14-macos-aarch64.jar"
        plugin = root / PLUGIN_FILENAME
        self.write_jar(body)
        self.write_jar(plugin)
        assets = root / "assets"
        (assets / "skin" / "default").mkdir(parents=True)
        (assets / "skin" / "default" / "select.json").write_text("{}", encoding="utf-8")
        (assets / "sound" / "default").mkdir(parents=True)
        (assets / "sound" / "default" / "README.txt").write_text("sound", encoding="utf-8")
        runtime = root / "java-home"
        (runtime / "bin").mkdir(parents=True)
        (runtime / "bin" / "java").write_text("java", encoding="utf-8")
        (runtime / "legal" / "java.base").mkdir(parents=True)
        (runtime / "legal" / "java.base" / "LICENSE").write_text("runtime", encoding="utf-8")
        (runtime / "release").write_text(
            'JAVA_VERSION="21.0.1"\nOS_NAME="Darwin"\nOS_ARCH="aarch64"\n',
            encoding="utf-8",
        )
        license_path = root / "LICENSE"
        license_path.write_text("GPL", encoding="utf-8")
        launcher_app = root / "BMS-IR Arena Test.app"
        executable = launcher_app / "Contents" / "MacOS" / "bmsir-arena-launcher"
        executable.parent.mkdir(parents=True)
        executable.write_bytes(b"\xcf\xfa\xed\xfe" + CONFIGURED_LAUNCHER_MARKER)
        executable.chmod(0o755)
        resources = launcher_app / "Contents" / "Resources"
        resources.mkdir(parents=True)
        (resources / "icon.icns").write_bytes(b"icon")
        (launcher_app / "Contents" / "Info.plist").write_bytes(
            plistlib.dumps(
                {
                    "CFBundleExecutable": "bmsir-arena-launcher",
                    "CFBundleIdentifier": "org.bms-ir.arena.launcher.test",
                    "CFBundleName": "BMS-IR Arena Test",
                }
            )
        )
        return {
            "body": body,
            "plugin": plugin,
            "assets": assets,
            "runtime": runtime,
            "license": license_path,
            "launcher_app": launcher_app,
        }

    def test_builds_self_contained_archive_without_mutable_player_data(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            (fixture["assets"] / "player").mkdir()
            (fixture["assets"] / "player" / "secret.json").write_text("secret", encoding="utf-8")
            output = build_release(
                platform="macos-aarch64",
                body_jar=fixture["body"],
                plugin_jar=fixture["plugin"],
                base_assets=fixture["assets"],
                java_home=fixture["runtime"],
                project_license=fixture["license"],
                output_dir=root / "dist",
                confirmed=True,
                launcher_app=fixture["launcher_app"],
                test_build=True,
            )
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                launcher = archive.read(
                    "BMS-IR-Arena-config.command"
                ).decode("utf-8")
                version = archive.read("bmsir-arena-version.txt").decode("ascii")
            self.assertIn("beatoraja.jar", names)
            self.assertIn(f"ir/{PLUGIN_FILENAME}", names)
            self.assertIn("runtime/bin/java", names)
            self.assertIn("runtime/legal/java.base/LICENSE", names)
            self.assertIn("BMS-IR-Arena-config.command", names)
            self.assertIn("BMS-IR Arena Test.app/Contents/Info.plist", names)
            self.assertIn(
                "BMS-IR Arena Test.app/Contents/MacOS/bmsir-arena-launcher", names
            )
            self.assertIn("release-manifest.json", names)
            self.assertEqual("0.4.14\n", version)
            self.assertIn("-DcustomIRDirectory=$PWD/ir", launcher)
            self.assertFalse(any(name.startswith("player/") for name in names))

    def test_requires_explicit_asset_redistribution_confirmation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "unreviewed assets"):
                build_release(
                    platform="macos-aarch64",
                    body_jar=fixture["body"],
                    plugin_jar=fixture["plugin"],
                    base_assets=fixture["assets"],
                    java_home=fixture["runtime"],
                    project_license=fixture["license"],
                    output_dir=root / "dist",
                    confirmed=False,
                    launcher_app=fixture["launcher_app"],
                )

    def test_windows_test_archive_contains_portable_test_launcher(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            body = root / "BMS-IR-Arena-oraja-0.4.14-windows-x86-64.jar"
            fixture["body"].rename(body)
            fixture["body"] = body
            (fixture["runtime"] / "bin" / "java").unlink()
            (fixture["runtime"] / "bin" / "java.exe").write_text("java", encoding="utf-8")
            (fixture["runtime"] / "release").write_text(
                'JAVA_VERSION="21.0.1"\nOS_NAME="Windows"\nOS_ARCH="amd64"\n',
                encoding="utf-8",
            )
            launcher = root / "launcher.exe"
            launcher.write_bytes(b"MZportable" + CONFIGURED_LAUNCHER_MARKER)
            output = build_release(
                platform="windows-x86-64",
                body_jar=fixture["body"],
                plugin_jar=fixture["plugin"],
                base_assets=fixture["assets"],
                java_home=fixture["runtime"],
                project_license=fixture["license"],
                output_dir=root / "dist",
                confirmed=True,
                launcher_exe=launcher,
                test_build=True,
            )
            self.assertIn("-test-java21.zip", output.name)
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                manifest = archive.read("release-manifest.json").decode("utf-8")
            self.assertIn("BMS-IR Arena Test.exe", names)
            self.assertIn("BMS-IR-Arena-config.bat", names)
            self.assertIn('"channel": "test"', manifest)

    def test_rejects_launcher_without_online_update_configuration(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            body = root / "BMS-IR-Arena-oraja-0.4.14-windows-x86-64.jar"
            fixture["body"].rename(body)
            fixture["body"] = body
            (fixture["runtime"] / "bin" / "java").unlink()
            (fixture["runtime"] / "bin" / "java.exe").write_text("java", encoding="utf-8")
            (fixture["runtime"] / "release").write_text(
                'JAVA_VERSION="21.0.1"\nOS_NAME="Windows"\nOS_ARCH="amd64"\n',
                encoding="utf-8",
            )
            launcher = root / "launcher.exe"
            launcher.write_bytes(b"MZvalidation-only")
            with self.assertRaisesRegex(ValueError, "update endpoint"):
                build_release(
                    platform="windows-x86-64",
                    body_jar=fixture["body"],
                    plugin_jar=fixture["plugin"],
                    base_assets=fixture["assets"],
                    java_home=fixture["runtime"],
                    project_license=fixture["license"],
                    output_dir=root / "dist",
                    confirmed=True,
                    launcher_exe=launcher,
                    test_build=True,
                )

    def test_distribution_revision_updates_archive_and_version_marker(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            output = build_release(
                platform="macos-aarch64",
                body_jar=fixture["body"],
                plugin_jar=fixture["plugin"],
                base_assets=fixture["assets"],
                java_home=fixture["runtime"],
                project_license=fixture["license"],
                output_dir=root / "dist",
                confirmed=True,
                launcher_app=fixture["launcher_app"],
                distribution_version="0.4.14.2",
            )
            self.assertIn("0.4.14.2-macos-aarch64", output.name)
            with zipfile.ZipFile(output) as archive:
                marker = archive.read("bmsir-arena-version.txt").decode("ascii")
                manifest = archive.read("release-manifest.json").decode("utf-8")
            self.assertEqual("0.4.14.2\n", marker)
            self.assertIn('"version": "0.4.14.2"', manifest)

    def test_rejects_macos_launcher_without_online_update_configuration(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            executable = (
                fixture["launcher_app"]
                / "Contents"
                / "MacOS"
                / "bmsir-arena-launcher"
            )
            executable.write_bytes(b"\xcf\xfa\xed\xfevalidation-only")
            executable.chmod(0o755)
            with self.assertRaisesRegex(ValueError, "update endpoint"):
                build_release(
                    platform="macos-aarch64",
                    body_jar=fixture["body"],
                    plugin_jar=fixture["plugin"],
                    base_assets=fixture["assets"],
                    java_home=fixture["runtime"],
                    project_license=fixture["license"],
                    output_dir=root / "dist",
                    confirmed=True,
                    launcher_app=fixture["launcher_app"],
                    test_build=True,
                )


if __name__ == "__main__":
    unittest.main()
