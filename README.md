# BMS-IR Arena oraja

BMS-IR向けの専用LR2oraja Endless Dreamクライアントです。通常プレイに加え、
BMS-IR Arena、BMS-IR固有設定、専用ランチャーなどを収録しています。

This repository is the canonical source repository for the dedicated BMS-IR
Arena oraja client. It is based on
[LR2oraja Endless Dream](https://github.com/Catizard/lr2oraja-endlessdream)
and ultimately on [beatoraja](https://github.com/exch-bms2/beatoraja).

## Current Version

The current client source version is **0.4.14.37**. This source adds an
owner-only My Difficulty Table editor to the Arena overlay and hot-reloads its
server-confirmed table bar in Music Select without restarting the game. When
the authenticated BMS-IR system owner has multiple tables, the overlay lists
them and requires an explicit edit target before any mutation. Existing-table
metadata and up to 64 selected-chart changes can be staged, reviewed, undone,
and saved together for one authoritative in-game refresh.
Reviewed Windows and macOS packages are distributed from the
[BMS-IR Arena page](https://www.bms-ir.org/new/arena).

GitHub pushes do not publish official binaries automatically. Public packages
are built, signed where applicable, verified, and released through the BMS-IR
release procedure.

## Unreleased Source Changes

The current development source selectively incorporates conflict-free fixes
from beatoraja without replacing Arena-specific rendering, rules, or IR plugin
loading behavior. The synchronized fixes cover score/BPM graph arithmetic,
JPEG crash avoidance, thick-note placement, preview-path refreshes, controller
hat input, Practice controls and gauge handling, IR class discovery, and full
local play-history retention with average-judge history. Manual full/differential
song-database updates now require confirmation, and new audio configurations
default to 256 simultaneous sources. Skin Select now renders a live preview of
the selected supported skin and refreshes it after option, file, or offset
changes. Music Select previews use an in-memory folder/song catalog; DECIDE
receives a virtual selected chart; play previews run a silent in-memory chart
through loading, READY, play, music-end, and fadeout; and RESULT / COURSE
RESULT receive representative scores, gauge histories, timing data, and course
content. Play-preview loops reset their lane scan and simulated input state;
notes enter from the lane top, tap beams release, and held charge notes expose
their active body animation on every loop. Lua, JSON, and LR2 Skin Select skins
can opt in to the preview object.

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

## Diagnostic logs

The client creates its Java/JUL log as `logs/beatoraja_log.xml` and its
bounded Arena diagnostic log as `logs/bmsir-arena.log`. The `logs/` directory
is created automatically beside the client data directories when either the
configuration UI or gameplay starts. Existing logs from older releases are
left in place and are not migrated automatically.

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
