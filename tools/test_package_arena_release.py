from pathlib import Path
import hashlib
import json
import plistlib
import stat
import struct
import tempfile
import unittest
import zipfile

from package_arena_release import (
    BODY_FILENAME,
    CONFIGURED_LAUNCHER_MARKER,
    PLUGIN_FILENAME,
    VERSION,
    build_release,
)
from verify_native_audio_bundle import (
    ASIO_ARCHIVE_SHA256,
    EXPECTED_DLL_MARKERS,
    PORTAUDIO_ARCHIVE_SHA256,
    PORTAUDIO_COMMIT,
    REQUIRED_FILES,
)


class ArenaReleasePackageTest(unittest.TestCase):
    def write_jar(self, path: Path) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")

    def write_native_audio_bundle(self, root: Path) -> Path:
        bundle = root / "native-audio-bundle"
        for relative in REQUIRED_FILES:
            path = bundle / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if relative in EXPECTED_DLL_MARKERS:
                data = bytearray(256)
                data[:2] = b"MZ"
                struct.pack_into("<I", data, 0x3C, 128)
                data[128:132] = b"PE\0\0"
                struct.pack_into("<H", data, 132, 0x8664)
                data.extend(b"\0".join(EXPECTED_DLL_MARKERS[relative]))
                path.write_bytes(data)
            else:
                path.write_text(relative, encoding="utf-8")
        spdx = {
            "packages": [
                {"name": "PortAudio", "licenseDeclared": "MIT"},
                {"name": "JPortAudio", "licenseDeclared": "MIT"},
                {
                    "name": "Steinberg ASIO SDK",
                    "licenseDeclared": "GPL-3.0-only AND BSD-3-Clause",
                },
                {"name": "JNA and JNA Platform", "licenseDeclared": "Apache-2.0"},
            ]
        }
        (bundle / "native-audio.spdx.json").write_text(json.dumps(spdx), encoding="utf-8")
        files = [
            {
                "path": path.relative_to(bundle).as_posix(),
                "size": path.stat().st_size,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
            for path in sorted(bundle.rglob("*"))
            if path.is_file()
        ]
        manifest = {
            "schema_version": 1,
            "target": "windows-x86_64",
            "license_route": "GPL-3.0-only",
            "features": ["ASIO", "WASAPI"],
            "toolchain": {"double_build_verified": True},
            "sources": {
                "portaudio": {
                    "commit": PORTAUDIO_COMMIT,
                    "sha256": PORTAUDIO_ARCHIVE_SHA256,
                    "license": "MIT",
                },
                "asio_sdk": {
                    "version": "2.3.4",
                    "sha256": ASIO_ARCHIVE_SHA256,
                    "license_selected": "GPL-3.0-only",
                },
                "jna": {"version": "5.13.0", "license_selected": "Apache-2.0"},
            },
            "files": files,
        }
        (bundle / "native-audio-manifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        return bundle

    def fixture(self, root: Path) -> dict[str, Path]:
        body = root / f"BMS-IR-Arena-oraja-{VERSION}-macos-aarch64.jar"
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
        (runtime / "bin" / "java").chmod(0o755)
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
                executable_modes = [
                    archive.getinfo(name).external_attr >> 16
                    for name in (
                        "runtime/bin/java",
                        "BMS-IR Arena Test.app/Contents/MacOS/bmsir-arena-launcher",
                        "BMS-IR-Arena-config.command",
                    )
                ]
            self.assertIn(BODY_FILENAME, names)
            self.assertNotIn("beatoraja.jar", names)
            self.assertIn(f"ir/{PLUGIN_FILENAME}", names)
            self.assertIn("runtime/bin/java", names)
            self.assertIn("runtime/legal/java.base/LICENSE", names)
            self.assertIn("BMS-IR-Arena-config.command", names)
            self.assertIn("BMS-IR Arena Test.app/Contents/Info.plist", names)
            self.assertIn(
                "BMS-IR Arena Test.app/Contents/MacOS/bmsir-arena-launcher", names
            )
            self.assertIn("release-manifest.json", names)
            self.assertEqual(f"{VERSION}\n", version)
            self.assertIn(f"-jar {BODY_FILENAME}", launcher)
            self.assertIn("-DcustomIRDirectory=$PWD/ir", launcher)
            self.assertFalse(any(name.startswith("player/") for name in names))
            self.assertTrue(all(stat.S_ISREG(mode) for mode in executable_modes))
            self.assertTrue(all(mode & stat.S_IXUSR for mode in executable_modes))

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
            body = root / f"BMS-IR-Arena-oraja-{VERSION}-windows-x86-64.jar"
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
            native_audio_bundle = self.write_native_audio_bundle(root)
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
                native_audio_bundle=native_audio_bundle,
                test_build=True,
            )
            self.assertIn("-test-java21.zip", output.name)
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                manifest = json.loads(archive.read("release-manifest.json"))
                launcher = archive.read("BMS-IR-Arena-config.bat").decode("utf-8")
            self.assertIn("BMS-IR Arena Test.exe", names)
            self.assertIn("BMS-IR-Arena-config.bat", names)
            self.assertIn(BODY_FILENAME, names)
            self.assertNotIn("beatoraja.jar", names)
            self.assertIn("natives/portaudio_x64.dll", names)
            self.assertIn("natives/jportaudio_x64.dll", names)
            self.assertIn("native-audio.spdx.json", names)
            self.assertIn("licenses/JNA-5.13.0-APACHE-2.0.txt", names)
            self.assertIn("THIRD_PARTY_NOTICES.txt", names)
            self.assertIn(f"-jar {BODY_FILENAME}", launcher)
            self.assertIn("-Djava.library.path=%CD%\\natives", launcher)
            self.assertEqual("test", manifest["channel"])
            self.assertTrue(manifest["native_audio_manifest_sha256"])
            self.assertTrue(any(item["path"] == "natives/portaudio_x64.dll" for item in manifest["files"]))

    def test_windows_archive_requires_native_audio_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            body = root / f"BMS-IR-Arena-oraja-{VERSION}-windows-x86-64.jar"
            fixture["body"].rename(body)
            (fixture["runtime"] / "bin" / "java").unlink()
            (fixture["runtime"] / "bin" / "java.exe").write_text("java", encoding="utf-8")
            (fixture["runtime"] / "release").write_text(
                'JAVA_VERSION="21.0.1"\nOS_NAME="Windows"\nOS_ARCH="amd64"\n',
                encoding="utf-8",
            )
            launcher = root / "launcher.exe"
            launcher.write_bytes(b"MZportable" + CONFIGURED_LAUNCHER_MARKER)
            with self.assertRaisesRegex(ValueError, "native audio bundle"):
                build_release(
                    platform="windows-x86-64",
                    body_jar=body,
                    plugin_jar=fixture["plugin"],
                    base_assets=fixture["assets"],
                    java_home=fixture["runtime"],
                    project_license=fixture["license"],
                    output_dir=root / "dist",
                    confirmed=True,
                    launcher_exe=launcher,
                    test_build=True,
                )

    def test_rejects_launcher_without_online_update_configuration(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = self.fixture(root)
            body = root / f"BMS-IR-Arena-oraja-{VERSION}-windows-x86-64.jar"
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
            # Exercise a distribution_version that differs from the module
            # default: the reviewed body JAR must be named for that explicit
            # revision, not for VERSION.
            revised_body = root / "BMS-IR-Arena-oraja-0.4.14.18-macos-aarch64.jar"
            fixture["body"].rename(revised_body)
            fixture["body"] = revised_body
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
                distribution_version="0.4.14.18",
            )
            self.assertIn("0.4.14.18-macos-aarch64", output.name)
            with zipfile.ZipFile(output) as archive:
                marker = archive.read("bmsir-arena-version.txt").decode("ascii")
                manifest = archive.read("release-manifest.json").decode("utf-8")
            self.assertEqual("0.4.14.18\n", marker)
            self.assertIn('"version": "0.4.14.18"', manifest)

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
