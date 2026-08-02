from pathlib import Path
import tempfile
import unittest
import zipfile

from package_arena_release import PLUGIN_FILENAME, build_release


class ArenaReleasePackageTest(unittest.TestCase):
    def write_jar(self, path: Path) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")

    def fixture(self, root: Path) -> dict[str, Path]:
        body = root / "BMS-IR-Arena-oraja-0.4.13-macos-aarch64.jar"
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
        return {
            "body": body,
            "plugin": plugin,
            "assets": assets,
            "runtime": runtime,
            "license": license_path,
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
            )
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                launcher = archive.read(
                    "BMS-IR-Arena-config.command"
                ).decode("utf-8")
            self.assertIn("beatoraja.jar", names)
            self.assertIn(f"ir/{PLUGIN_FILENAME}", names)
            self.assertIn("runtime/bin/java", names)
            self.assertIn("runtime/legal/java.base/LICENSE", names)
            self.assertIn("BMS-IR-Arena-config.command", names)
            self.assertIn("release-manifest.json", names)
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
                )


if __name__ == "__main__":
    unittest.main()
