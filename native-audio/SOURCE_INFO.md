# Windows native audio source and build information

The Windows x86-64 Arena oraja package uses a clean build of PortAudio and
JPortAudio. The previous repository DLLs are not inputs to this build.

## Sources

- PortAudio 19.7.0, commit
  `147dd722548358763a8b649b3e4b41dfffbcfbb6`, from the PortAudio project.
  PortAudio and JPortAudio use the MIT license.
- Steinberg ASIO SDK 2.3.4 dated 2025-10-15, downloaded from Steinberg's
  official SDK endpoint. Arena oraja selects the SDK's GPL-3.0-only route for
  this GPL-3.0-only distribution. The SDK host helper sources linked into the
  Windows DLL separately carry BSD 3-Clause terms; their notice is included in
  the bundle.
- JNA and JNA Platform 5.13.0, commit
  `4962fd7758493b7395e86578705d8a32f6238872`. Arena oraja selects the
  Apache-2.0 route offered by JNA.

The exact URLs and SHA-256 values are in `inputs.json` and the generated
`native-audio-manifest.json`. Original PortAudio and ASIO SDK archives are
included under `source/native-audio/` in the Windows package together with the
build scripts. The complete Arena oraja source is published from
https://github.com/tenP0312-dev/oraja.

## Build

`build-native-audio.ps1` verifies both upstream archives before extraction,
configures CMake for Visual Studio 2022 x86-64, and requires ASIO and WASAPI.
It builds a statically linked MSVC runtime and produces only:

- `natives/portaudio_x64.dll`
- `natives/jportaudio_x64.dll`

The release build uses `/Brepro`, stable source path mappings, and no
incremental link. CI performs two independent builds and rejects different DLL
hashes. The generated manifest records the exact runner/toolchain and output
hashes. This documents the inputs and checks used; it is not a legal opinion.
