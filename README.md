# BMS-IR Arena oraja

BMS-IR向けの専用LR2oraja Endless Dreamクライアントです。通常プレイに加え、
BMS-IR Arena、BMS-IR固有設定、専用ランチャーなどを収録しています。

This repository is the canonical source repository for the dedicated BMS-IR
Arena oraja client. It is based on
[LR2oraja Endless Dream](https://github.com/Catizard/lr2oraja-endlessdream)
and ultimately on [beatoraja](https://github.com/exch-bms2/beatoraja).

## Current Version

The current client source version is **0.4.14.39**. The Resource settings now
group song-library maintenance with the BMS roots, keep those controls hidden
on unrelated tabs, and provide a compact difficulty-table editor. A song-root
context menu can update only the selected root without rescanning unrelated
roots. On the Music Select root, a short F2 press on a table supplied by the
`BMS-IR` Primary IR now reloads the recommendation and registered-rival tables
without restarting the client. Startup now opens from a per-player last-good
Primary IR selection-table cache and refreshes it after Music Select is usable,
instead of blocking the startup screen on the complete server response.
Reviewed Windows and macOS packages are distributed from the
[BMS-IR Arena page](https://www.bms-ir.org/new/arena).

GitHub pushes do not publish official binaries automatically. Public packages
are built, signed where applicable, verified, and released through the BMS-IR
release procedure.

Every BMS-IR-built body or plugin made downloadable through the launcher is
covered by that procedure, including internal test and prerelease updates. A
distribution is not complete until both ordinary-score acceptance and the
Arena client-version/build gate are activated and verified where applicable.

## Arena oraja 0.4.14.39

The Resource settings now explain ordinary and full song-library updates next
to their buttons. Ordinary loading checks only added or changed songs without
a confirmation step; full loading warns before rebuilding every registered
song. The controls stay inside the Resource tab rather than appearing on every
configuration tab.

Each BMS root has right-click actions to update only that root, open or copy
its path, or remove it from configuration without deleting the source folder.
The configured download root remains protected. Difficulty tables use one
active list with row actions for update, URL editing, ordering, and removal.
The compact built-in-table picker supports selecting and adding several tables
at once, while custom URLs remain available through a separate dialog.

## Arena oraja 0.4.14.38

The current development source selectively incorporates conflict-free fixes
from beatoraja without replacing Arena-specific rendering, rules, or IR plugin
loading behavior. The synchronized fixes cover score/BPM graph arithmetic,
JPEG crash avoidance, thick-note placement, preview-path refreshes, controller
hat input, Practice controls and gauge handling, IR class discovery, and full
local play-history retention with average-judge history. Manual full/differential
song-database updates required confirmation, and new audio configurations
default to 256 simultaneous sources. Skin Select now renders a live preview of
the selected supported skin and refreshes it after option, file, or offset
changes. Music Select previews use an in-memory folder/song catalog; DECIDE
receives a virtual selected chart; play previews run a silent in-memory chart
through loading, READY, play, music-end, and fadeout; and RESULT / COURSE
RESULT receive representative scores, gauge histories, timing data, and course
content. Play-preview loops reset their lane scan and simulated input state;
notes enter from the lane top, tap beams release, and held charge notes expose
their active body animation on every loop. Double-play sample sessions advance
1P and 2P notes, judgement, combo, key-beam, and end timers independently, as
normal 14-key autoplay does. Every data-backed preview rewinds its untimed
destination animations, event/timer cache, and movie sources at the loop
boundary, so DECIDE, PLAY, RESULT, and COURSE RESULT replay their intro and
fade sequences on later iterations. Lua and JSON Skin Select skins can
declare an exact preview destination, LR2 skins retain the GR 105 contract,
and legacy Lua/JSON skins safely prefer a contained preview background over
the larger skin-change click target. The preview is drawn at that background's
position instead of being covered by an old thumbnail.

Song roots can opt in to scanning `.zip` and `.rar` song archives from
the Resource settings. BMS/BMSON charts, key sounds, preview music, stage,
banner and back images, and image or movie BGA are read through stable archive
virtual paths without expanding the archive into the song library. Existing
chart hashes, IR records, replays, tables, courses, and Arena ownership checks
continue to use the decoded chart identity.

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
