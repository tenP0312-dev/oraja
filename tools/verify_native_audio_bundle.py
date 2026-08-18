#!/usr/bin/env python3
"""Validate the exact Windows native-audio bundle before packaging."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct


PORTAUDIO_COMMIT = "147dd722548358763a8b649b3e4b41dfffbcfbb6"
PORTAUDIO_ARCHIVE_SHA256 = (
    "95457b809ce60d4d4790f84bb692e271f644e59d8adf96feb988c89ab52a506a"
)
ASIO_ARCHIVE_SHA256 = (
    "d5ebf0c20dd2c5f43771fd0c1418f4b361bf52434ee670097cfa6b3a335e2eca"
)
REQUIRED_FILES = {
    "licenses/PORTAUDIO-19.7.0-MIT.txt",
    "licenses/STEINBERG-ASIO-SDK-2.3.4-BSD-3-CLAUSE.txt",
    "licenses/STEINBERG-ASIO-SDK-2.3.4.txt",
    "native-audio-SOURCE_INFO.md",
    "native-audio.spdx.json",
    "natives/jportaudio_x64.dll",
    "natives/portaudio_x64.dll",
    f"source/native-audio/portaudio-{PORTAUDIO_COMMIT}.tar.gz",
    "source/native-audio/ASIO-SDK_2.3.4_2025-10-15.zip",
    "source/native-audio/CMakeLists.txt",
    "source/native-audio/SOURCE_INFO.md",
    "source/native-audio/STEINBERG-ASIO-SDK-2.3.4-BSD-3-CLAUSE.txt",
    "source/native-audio/build-native-audio.ps1",
    "source/native-audio/inputs.json",
}
EXPECTED_DLL_MARKERS = {
    "natives/portaudio_x64.dll": (
        b"Pa_GetVersion",
        b"PaAsio_ShowControlPanel",
        b"PaWasapi_GetDeviceDefaultFormat",
        PORTAUDIO_COMMIT.encode("ascii"),
    ),
    "natives/jportaudio_x64.dll": (
        b"Java_com_portaudio_PortAudio_initialize",
        b"Java_com_portaudio_BlockingStream_writeFloats",
    ),
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _validate_pe_x64(path: Path) -> bytes:
    data = path.read_bytes()
    if len(data) < 256 or data[:2] != b"MZ":
        raise ValueError(f"Native audio file is not a PE executable: {path.name}")
    pe_offset = struct.unpack_from("<I", data, 0x3C)[0]
    if pe_offset + 6 > len(data) or data[pe_offset : pe_offset + 4] != b"PE\0\0":
        raise ValueError(f"Native audio file has no valid PE header: {path.name}")
    machine = struct.unpack_from("<H", data, pe_offset + 4)[0]
    if machine != 0x8664:
        raise ValueError(f"Native audio file is not x86-64 PE: {path.name}")
    return data


def validate_native_audio_bundle(root: Path) -> dict[str, object]:
    root = root.resolve()
    manifest_path = root / "native-audio-manifest.json"
    if not manifest_path.is_file():
        raise ValueError("Native audio manifest is missing")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError("Native audio manifest is invalid") from exc
    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported native audio manifest schema")
    if manifest.get("target") != "windows-x86_64":
        raise ValueError("Native audio bundle has the wrong target")
    if manifest.get("license_route") != "GPL-3.0-only":
        raise ValueError("Native audio bundle did not select the ASIO GPLv3 route")
    if set(manifest.get("features", ())) < {"ASIO", "WASAPI"}:
        raise ValueError("Native audio bundle must contain both ASIO and WASAPI")
    if not manifest.get("toolchain", {}).get("double_build_verified"):
        raise ValueError("Native audio bundle lacks the double-build reproducibility check")

    sources = manifest.get("sources", {})
    portaudio = sources.get("portaudio", {})
    asio = sources.get("asio_sdk", {})
    jna = sources.get("jna", {})
    if (
        portaudio.get("commit") != PORTAUDIO_COMMIT
        or portaudio.get("sha256") != PORTAUDIO_ARCHIVE_SHA256
        or portaudio.get("license") != "MIT"
    ):
        raise ValueError("PortAudio source identity does not match the reviewed input")
    if (
        asio.get("version") != "2.3.4"
        or asio.get("sha256") != ASIO_ARCHIVE_SHA256
        or asio.get("license_selected") != "GPL-3.0-only"
    ):
        raise ValueError("ASIO SDK source or license route does not match the reviewed input")
    if jna.get("version") != "5.13.0" or jna.get("license_selected") != "Apache-2.0":
        raise ValueError("JNA license selection does not match the reviewed input")

    records = manifest.get("files")
    if not isinstance(records, list) or not records:
        raise ValueError("Native audio manifest has no file inventory")
    recorded: dict[str, dict[str, object]] = {}
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("Native audio manifest contains an invalid file record")
        relative = record.get("path")
        if (
            not isinstance(relative, str)
            or not relative
            or relative.startswith("/")
            or ".." in Path(relative).parts
            or "\\" in relative
            or relative in recorded
        ):
            raise ValueError(f"Unsafe or duplicate native audio path: {relative!r}")
        recorded[relative] = record
    if not REQUIRED_FILES.issubset(recorded):
        missing = sorted(REQUIRED_FILES - set(recorded))
        raise ValueError(f"Native audio bundle is incomplete: {missing}")

    actual_files: set[str] = set()
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ValueError(f"Native audio bundle must not contain symlinks: {path}")
        if not path.is_file() or path == manifest_path:
            continue
        relative = path.relative_to(root).as_posix()
        actual_files.add(relative)
        record = recorded.get(relative)
        if record is None:
            raise ValueError(f"Unmanifested native audio file: {relative}")
        if record.get("size") != path.stat().st_size:
            raise ValueError(f"Native audio file size mismatch: {relative}")
        if record.get("sha256") != sha256_file(path):
            raise ValueError(f"Native audio file SHA-256 mismatch: {relative}")
    if actual_files != set(recorded):
        missing = sorted(set(recorded) - actual_files)
        raise ValueError(f"Manifest references missing native audio files: {missing}")
    if any(path.lower().endswith("_x86.dll") for path in actual_files):
        raise ValueError("The x86-64 distribution must not contain x86 native DLLs")

    for relative, markers in EXPECTED_DLL_MARKERS.items():
        data = _validate_pe_x64(root / relative)
        missing_markers = [marker.decode("ascii") for marker in markers if marker not in data]
        if missing_markers:
            raise ValueError(f"{relative} lacks required exports/identity: {missing_markers}")

    try:
        spdx = json.loads((root / "native-audio.spdx.json").read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError("Native audio SPDX document is invalid") from exc
    declared = {
        package.get("name"): package.get("licenseDeclared")
        for package in spdx.get("packages", ())
        if isinstance(package, dict)
    }
    if declared.get("PortAudio") != "MIT" or declared.get("JPortAudio") != "MIT":
        raise ValueError("SPDX document lacks the PortAudio/JPortAudio MIT declarations")
    if declared.get("Steinberg ASIO SDK") != "GPL-3.0-only AND BSD-3-Clause":
        raise ValueError("SPDX document lacks the selected ASIO/BSD declarations")
    if declared.get("JNA and JNA Platform") != "Apache-2.0":
        raise ValueError("SPDX document lacks the selected JNA Apache-2.0 declaration")
    return manifest


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    validate_native_audio_bundle(args.bundle)
    print(f"Native audio bundle verified: {args.bundle.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
