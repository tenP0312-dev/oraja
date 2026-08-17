# BMS-IR Arena oraja

BMS-IR向けの専用LR2oraja Endless Dreamクライアントです。通常プレイに加え、
BMS-IR Arena、BMS-IR固有設定、専用ランチャーなどを収録しています。

This repository is the canonical source repository for the dedicated BMS-IR
Arena oraja client. It is based on
[LR2oraja Endless Dream](https://github.com/Catizard/lr2oraja-endlessdream)
and ultimately on [beatoraja](https://github.com/exch-bms2/beatoraja).

## Current Version

The current client source version is **0.4.14.51**. Windows PortAudio users can
select WASAPI Shared or Exclusive mode in the Audio tab; Shared remains the
default and the selector is available only for a Windows WASAPI device.
Windows users can also choose `ASIO` as a separate driver that lists only ASIO
output devices, persists the exact selection, and never silently changes an
unavailable ASIO path to another audio driver. Existing generic PortAudio and
OpenAL choices remain available.
Reviewed Windows and macOS packages are distributed from the
[BMS-IR Arena page](https://www.bms-ir.org/new/arena).

The current development source also uses each difficulty-table entry's first
decimal integer as its Music Select display level inside that table. This
applies to ordinary level folders and aggregate folders such as `全曲`, while
ordinary folders and the chart's stored `#PLAYLEVEL` remain unchanged.

The Resource built-in-table picker now keeps configured built-in tables
visible as checked, read-only choices. The add action becomes available only
after selecting at least one new table, so the picker reflects the saved
configuration without duplicating or removing an active table.

GitHub pushes do not publish official binaries automatically. Public packages
are built, signed where applicable, verified, and released through the BMS-IR
release procedure.

Every BMS-IR-built body or plugin made downloadable through the launcher is
covered by that procedure, including internal test and prerelease updates. A
distribution is not complete until both ordinary-score acceptance and the
Arena client-version/build gate are activated and verified where applicable.

## Arena oraja 0.4.14.51

The Audio tab adds WASAPI Shared / Exclusive mode selection for Windows
WASAPI output devices. Existing configurations stay on Shared mode. Exclusive
mode requests direct access through the bundled PortAudio WASAPI backend and
requires a sample rate supported by the selected device.

`ASIO` is now an independent Windows audio driver. Its device list contains
only ASIO Host API endpoints with output channels, stores the selected Host
API identity, disables the WASAPI mode selector, and reports unavailable ASIO
support without silently switching the saved driver to OpenAL or WASAPI.

## Arena oraja 0.4.14.50

Select a configured BMS Path and click `Set Work Directory` /
`作業フォルダに設定` to make that exact root and its descendants disposable
authoring content. The action does not create, move, delete, or open a folder.
The selected row is labeled as the work folder and cannot be removed from BMS
Path until another root is selected. The `_BMSIR_TESTPLAY` compatibility marker
from 0.4.14.49 remains active.

## Arena oraja 0.4.14.49

The Resource tab adds `Work folder` / `作業フォルダ` below
`Set DL Directory`. It creates and opens `_BMSIR_TESTPLAY` under the selected
BMS Path. Charts anywhere below that directory do not save scores, lamps,
play counts, histories, or replays, and are not submitted to ordinary IR,
MANIAC IR, course IR, or Arena.

## Arena oraja 0.4.14.48

The Input tab can opt in to reading dedicated game-controller/HID buttons,
hats, and axes while the Arena oraja window is unfocused. Keyboard, mouse, and
mouse-scratch input remain focus-bound, and existing installations keep the
option off until the player enables it.

PRELOAD and READY now share one live lane-setting state. HI-SPEED, green
number, SUD+, LIFT, and HIDDEN remain adjustable until play starts, and the
final pre-start state becomes the next chart's initial state.

## Arena oraja 0.4.14.47

One-bass handles every borrowed standard-RANDOM entry path. Seedless
RIVALOPTION/REPLAYOPTION borrowing selects a new replayable seed instead of
disabling one-bass, LR2IR G-BATTLE resolves its borrowed lane order before the
two-lane swap, and DP keeps missing-seed state independent on each side.

## Arena oraja 0.4.14.46

This release removes the standalone optional IIDX FHS while its replacement
behavior is unspecified, preserves fixed HI-SPEED and cover settings when the
first-timing preview enters READY, and keeps a borrowed RANDOM placement when
one-bass is applied. Music Select also accepts loose difference charts dropped
onto a selected physical song. On macOS, Arena overlay IME fields now run their
native text control in an isolated helper JVM and automatically unlock body
input if that helper cannot start or exits unexpectedly.

## Arena oraja 0.4.14.45

The startup launcher's BMS-IR-specific settings now include one shared switch
to hide unavailable songs across every difficulty table. It remains off by
default, also applies inside tables when HTTP downloads are enabled, and leaves
ordinary folders, searches, and Arena candidate lists unchanged.

The signed internal channel used `0.4.14.44` for a launcher/plugin-only sparse
update while the body stayed on `0.4.14.43`; the body therefore advances to
`0.4.14.45` so its identity and the next channel version are aligned again.

## Arena oraja 0.4.14.43

Music Select now merges indexed charts from immediate ZIP/RAR/7z virtual
containers into the physical parent's song list. Ordinary per-song container
folders stay hidden as before, the extra archive-folder level is removed, and
already-scanned databases work without a rescan.

## Arena oraja 0.4.14.42

Music Select now combines directly indexed song bars with child folder bars.
Previously, finding any direct song caused every child folder to be discarded,
which could hide a successfully scanned ZIP/RAR/7z package before its chart's
key-mode filter was evaluated.

## Arena oraja 0.4.14.41

Direct song-archive support now includes RAR5 and 7z in addition to ZIP and
RAR4. The reader uses the archive content signature when a supported suffix is
mislabeled, normalizes Unicode lookup safely, keeps deeply nested charts
reachable, discovers previews per chart directory, and reloads an archive when
its bytes are replaced even if simple file metadata is unchanged.

Rejected or unreadable archive refreshes fail closed and preserve the last
indexed songs. Temporary decoder files have bounded capacity and stale-file
cleanup, and song loading reports loaded/rejected archive totals with the
causal rejection reason in the diagnostic log.

## Arena oraja 0.4.14.40

The complete BMS-IR Primary IR table response no longer blocks startup. The
client restores the last valid per-player selection-table cache first, opens
Music Select, and performs one background refresh. A successful refresh is
saved for the next startup and applied on the render thread; if the player is
inside another folder, root-table replacement waits until the selector returns
to its root. Empty, invalid, and failed refreshes keep the previous cache.

The startup display name is now `Primary IR選曲テーブル`, which distinguishes
these server-provided selection tables from ordinary Web difficulty-table
updates.

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

Song roots can opt in to scanning `.zip`, `.rar` (including RAR5), and `.7z`
song archives from
the Resource settings. BMS/BMSON charts, key sounds, preview music, stage,
banner and back images, and image or movie BGA are read through stable archive
virtual paths without expanding the archive into the song library. Existing
chart hashes, IR records, replays, tables, courses, and Arena ownership checks
continue to use the decoded chart identity. Archive content signatures select
the reader, so a ZIP/RAR/7z file with another supported archive suffix is still
read correctly.

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

## Startup configuration layout

The `Other` tab can switch the startup configuration screen between
`Classic` and `Sidebar`. Existing and new installations default to Classic.
Sidebar keeps the same setting controls and save paths, moves the category
navigation to the left, and keeps the explanation for each setting directly
below that setting. The player ID, display name, and active rule
profile are collapsed into a sidebar summary and can be expanded when needed.
The navigation rail has a stable width, searchable destinations, restrained
single-color icons, and grouped settings cards so changing pages does not shift
the content boundary. Every scalar row uses one stable two-column layout: its
label stays at the left and its editor or ON/OFF switch uses the same trailing
column. Standalone switches sit at the row's far-right edge, with a persistent
plain-language explanation below. Folder lists,
tables, skin previews, Webhook lists, and OBS scene mappings keep their useful
shape inside full-width explained workspace cards. Sidebar editors remain
connected to the original controls, so Classic layout, controller behavior,
and configuration persistence remain unchanged.

## 作業フォルダ / Work folder

BMS Pathから作りかけ譜面用のルートを選択し、`Set DL Directory` の下にある
`作業フォルダに設定` を押すと、選んだルートそのものが作業フォルダになります。
フォルダの作成・移動・削除は行いません。開く場合はBMS Pathを右クリックして
`フォルダを開く` を使います。このフォルダ以下の
BMS/BMSONは、スコア、ランプ、プレイ回数、履歴、リプレイを保存せず、通常IR、
MANIAC IR、コースIR、Arenaにも送信されません。さらに下のサブフォルダにも適用
されます。

Select the authoring root in BMS Path and click `Set Work Directory` below
`Set DL Directory`. The selected root itself becomes the work folder; the
action does not create, move, delete, or open a directory. Use the existing
right-click `Open folder` action when needed. BMS/BMSON charts
anywhere below it do not save scores, lamps, play counts, histories, or
replays, and are not submitted to ordinary IR, MANIAC IR, course IR, or Arena.

For compatibility with 0.4.14.49, an exact `_BMSIR_TESTPLAY` directory
component remains a case-insensitive no-save/no-submit safety marker even when
another root is selected as the work folder.

If a course contains a work-folder chart (including one matched by the legacy
marker), its aggregate course record is also discarded. Released charts
elsewhere in that course keep their existing per-chart behavior. This source
feature becomes available in a distributed client only after a separately
reviewed release; merging it does not publish a new binary.

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

For the final internal-release pass, keep two clean worktrees at the same
reviewed commit with submodules initialized, then run both platform lanes in
parallel. The helper validates the commit, version constants, submodules, and
JDK 17 once; each lane runs in its own worktree to avoid Gradle output races.

```sh
python3 tools/build_arena_release.py \
  --windows-worktree /release/oraja-windows \
  --macos-worktree /release/oraja-macos \
  --java-home /release/jdk-17 \
  --output-dir /release/build-0.4.14.51
```

`build-state.json` records both commands, durations, logs, source commit, and
artifact hashes. Do not recreate the worktrees, download the JDK, or initialize
submodules during every release; refresh the prepared worktrees to the reviewed
commit before invoking the helper.

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
