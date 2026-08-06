# BMS-IR Arena oraja

BMS-IR向けの専用LR2oraja Endless Dreamクライアントです。通常プレイに加え、
BMS-IR Arena、BMS-IR固有設定、専用ランチャーなどを収録しています。

This repository is the canonical source repository for the dedicated BMS-IR
Arena oraja client. It is based on
[LR2oraja Endless Dream](https://github.com/Catizard/lr2oraja-endlessdream)
and ultimately on [beatoraja](https://github.com/exch-bms2/beatoraja).

## Current Version

The current client source version is **0.4.14.18**. Reviewed Windows and macOS
packages are distributed from the
[BMS-IR Arena page](https://www.bms-ir.org/new/arena).

GitHub pushes do not publish official binaries automatically. Public packages
are built, signed where applicable, verified, and released through the BMS-IR
release procedure.

## Repository Scope

- Client source, launcher source, client Issues, and client pull requests live
  in this repository.
- The Arena server, BMS-IR Web service, and BMS-IR plugin live in
  [BMS-Mania/IR](https://github.com/BMS-Mania/IR).
- Changes to the client/server protocol require paired changes and validation
  in both repositories.
- Upstream repositories remain credited and are used for comparison and
  selective synchronization.

See [the Arena client documentation](doc/BMSIR_ARENA.md) for behavior and
configuration details.

## Building

A JDK 17 distribution with JavaFX is required. Clone with submodules:

```sh
git clone --recurse-submodules https://github.com/tenP0312-dev/oraja.git
cd oraja
```

Build the client for the target platform:

```sh
./gradlew core:shadowJar -Dplatform=windows
./gradlew core:shadowJar -Dplatform=linux
./gradlew core:shadowJar -Dplatform=macos
./gradlew core:shadowJar -Dplatform=macos -Darch=aarch64
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Generated jars are
written under `dist/`.

The desktop Arena launcher moved to its own repository,
[`tenP0312-dev/oraja-Rancher`](https://github.com/tenP0312-dev/oraja-Rancher).
Its Rust tests can be run there with:

```sh
cd src-tauri
cargo test --locked
```

## Development

Open client bugs and feature requests in this repository. Use a scoped branch
and pull request for changes to `main`. Do not commit local player data,
downloaded BMS assets, logs, databases, generated archives, signing material,
or private release configuration.

## License

This project retains its upstream licensing and notices. See [LICENSE](LICENSE).
