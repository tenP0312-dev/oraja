import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest

from verify_native_audio_bundle import (
    ASIO_ARCHIVE_SHA256,
    EXPECTED_DLL_MARKERS,
    PORTAUDIO_ARCHIVE_SHA256,
    PORTAUDIO_COMMIT,
    REQUIRED_FILES,
    validate_native_audio_bundle,
)


class NativeAudioBundleTest(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        for relative in REQUIRED_FILES:
            path = root / relative
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
        (root / "native-audio.spdx.json").write_text(json.dumps(spdx), encoding="utf-8")
        files = []
        for path in sorted(root.rglob("*")):
            if path.is_file():
                files.append(
                    {
                        "path": path.relative_to(root).as_posix(),
                        "size": path.stat().st_size,
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                    }
                )
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
        (root / "native-audio-manifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )

    def test_accepts_complete_reviewed_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            validate_native_audio_bundle(root)

    def test_rejects_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            path = root / "licenses/PORTAUDIO-19.7.0-MIT.txt"
            data = bytearray(path.read_bytes())
            data[0] ^= 1
            path.write_bytes(data)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                validate_native_audio_bundle(root)

    def test_rejects_missing_reproducibility_check(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            path = root / "native-audio-manifest.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            manifest["toolchain"]["double_build_verified"] = False
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "reproducibility"):
                validate_native_audio_bundle(root)


if __name__ == "__main__":
    unittest.main()
