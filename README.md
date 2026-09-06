# BMS-IR Arena oraja

BMS-IR向けの専用LR2oraja Endless Dreamクライアントです。通常プレイに加え、
BMS-IR Arena、BMS-IR固有設定、専用ランチャーなどを収録しています。

This repository is the canonical source repository for the dedicated BMS-IR
Arena oraja client. It is based on
[LR2oraja Endless Dream](https://github.com/Catizard/lr2oraja-endlessdream)
and ultimately on [beatoraja](https://github.com/exch-bms2/beatoraja).

## Current Version

The current client source version is **0.4.14.81**. Its Windows native-audio
runtime is rebuilt from pinned PortAudio 19.7.0 and the official Steinberg ASIO
SDK 2.3.4 under the GPLv3 route. The package carries the corresponding source,
licenses, source/build manifest, and SPDX SBOM; CI rejects an unverified or
non-reproducible native bundle.
Reviewed Windows and macOS packages are distributed from the
[BMS-IR Arena page](https://www.bms-ir.org/new/arena).

Version 0.4.14.81 adds the local-only NANTOKA MANIA MODE to MANIAC OPTIONS
for 5/7/10/14KEY solo play. It fixes judgment windows and integer gauge rules,
handles LN/CN/HCN/BSS and both DP scratches, and isolates scores and replays
from ordinary records. Arena, courses, practice, ordinary IR, and MANIAC IR do
not enable or accept this mode. Launcher 0.2.29, plugin 0.0.72, protocol v8,
and all 0.4.14.80 behavior remain unchanged.

Version 0.4.14.80 adds optional vertical color gradients to dynamic
TrueType/OpenType skin text in JSON and Lua skins. Both upper and lower
`RRGGBBAA` colors are required, and the active destination tint and alpha keep
working. Existing text without both properties remains on the legacy
single-color path. Launcher 0.2.29, plugin 0.0.72, protocol v8, and all
0.4.14.79 behavior remain unchanged.

Version 0.4.14.79 fixes Skin Scene projective shaders on macOS by ensuring the
GLSL 150 core-profile conversion rewrites every legacy `attribute` and
`varying` qualifier, including one at the start of the source. A regression
test covers the converted vertex and fragment contracts. Launcher 0.2.29,
plugin 0.0.72, protocol v8, Windows rendering, and all 0.4.14.78 behavior
remain unchanged.

Version 0.4.14.78 makes Skin Scene resources with `timer: 0` state-relative,
keeps compatible command leaves batched across unchanged hard rectangle mask
state, reloads textures after image-only edits, and keeps generated quads
upright for both canvas origins. Its documentation now describes the seven
implemented blend modes and nested hard rectangle masks precisely. Launcher
0.2.29, plugin 0.0.72, protocol v8, and all 0.4.14.77 behavior remain
unchanged.

Version 0.4.14.77 adds the optional neutral Skin Scene v1 resource to JSON and
Lua skins. It provides validated scene compilation, deterministic timelines,
nested transforms, 2D/3D textured meshes with perspective-correct UVs, color
transforms, seven named blend modes, nested hard rectangle masks, bounded
resource handling, metrics, and a self-authored sample. Skins without a scene
keep the existing SpriteBatch path. Launcher 0.2.29, plugin 0.0.72, protocol
v8, and all 0.4.14.76 behavior remain unchanged.

Version 0.4.14.76 lets the full and compact Arena overlays edit the selected
mode's actual LR2 HI-SPEED from 0.01 through 20.00 and the shared START+6/7
lane-cover step from 1 through 1000. The existing forced-LN conversion is now
named explicitly in Japanese settings, and a concrete Music Select key-mode
filter hides only difficulty tables that resolve entirely to incompatible
modes. Launcher 0.2.29, plugin 0.0.72, protocol v8, and all 0.4.14.75 behavior
remain unchanged.

Version 0.4.14.75 adds a default-on per-player switch that can disable Skin
Select live-preview work without affecting skin selection or custom options.
The full and compact Arena overlays can also follow the current chart mode or
pin the LR2 HI-SPEED editor to any supported play mode, then edit that mode's
normal HI-SPEED change step in a validated 0.00 through 10.00 numeric field.
Launcher 0.2.29, plugin 0.0.72, protocol v8, and all 0.4.14.74 behavior remain
unchanged.

Version 0.4.14.74 updates the 7z reader to Apache Commons Compress 1.26.2 so
overlapping movie-BGA materialization and solid-7z key-sound loading no longer
cause false checksum failures. When BMS-IR is the configured Primary IR, the
song context menu also keeps only the dedicated `BMS-IR Leaderboard`; another
Primary IR still keeps its separately labeled leaderboard. Launcher 0.2.29,
plugin 0.0.72, protocol v8, and all 0.4.14.73 behavior remain unchanged.

Version 0.4.14.73 combines the complete song-update result dialog, consistent
late `#BASE 62` ID mapping, and reliable solid-7z key-sound loading. The update
dialog keeps full-library chart totals primary and adds archive counts and the
last failure reason as one secondary breakdown. Base-dependent definitions now
use the file's effective `#BASE` regardless of declaration order. Referenced
key sounds in one solid 7z block are read sequentially before parallel decode;
per-chart temporary copies are removed after loading and the original archive
stays unchanged. Launcher 0.2.29, plugin 0.0.72, protocol v8, and all 0.4.14.72
behavior remain unchanged.

Version 0.4.14.72 restores the complete retained LR2oraja Endless Dream
behavior found by the exact 52-commit history audit. This includes sort-skin
visibility, Practice Random Trainer seeds, isolated BGA image textures,
controller hotplug, immediate rival refresh, Primary IR ranking, per-controller
JKOC, numeric volume inputs, authored LN mode, difficulty filtering, 5K/10K
result input, screenshots, score deletion, BACKBMP, IR player names, LITONE
play dates, `oraja_helper`, and Random Select presets. rian DX/X MODE and every
rianIR submission/replay-send path remain deliberately excluded. Launcher
0.2.29, plugin 0.0.72, protocol v8, and all `.59` through `.71` fixes remain
present.

Version 0.4.14.71 hides the Arena play cursor without capturing or confining
the OS pointer. Mouse movement, the Mod Menu, and leaving play restore cursor
visibility. Ordinary non-Arena cursor capture is unchanged. Launcher 0.2.29,
plugin 0.0.72, protocol, score behavior, native audio, and bundled default
skins are unchanged.

Version 0.4.14.70 restores top-row `8` as the fixed Music Select compatibility
shortcut for Show All Charts. Physical NUMPAD 8 remains separately
configurable. Launcher 0.2.29, plugin 0.0.72, protocol, score behavior, native
audio, and bundled default skins are unchanged.

Version 0.4.14.69 keeps the safe all-table refresh and adds the remaining
selected non-skin changes. Leaving Music Select now stops its BGM and preview
synchronously before ordinary or Arena gameplay. Explicit and discovered
OpenAL one-shot previews return to the default selector BGM at their decoded
duration. Default-OFF diagnostics now measure static-image BGA cache planning,
collisions, runtime misses, and texture upload time without changing the cache
or rendering policy. Bundled default skin assets and definitions are unchanged.

Version 0.4.14.68 repairs the Skin Widget Manager. The manager remains open
independently of the F5 parent menu, its visibility no longer captures gameplay
input by itself, and width-only or height-only edits are included in clipboard
export. It edits the current scene skin temporarily and does not edit the
separate BMS-IR Arena overlay or write skin source files. Launcher 0.2.29 and
plugin 0.0.72 are unchanged.

Version 0.4.14.61 restores the ordinary LN/CN/HCN launcher
selection and carries it through decoding, catalog keys, IR ranking, and score
submission. Casual/private Arena rooms lock the host's selected mode for every
participant; rated Arena explicitly remains LN. New BMS-IR body archives use
portable readable names such as `[Artist]Song-0123abcd.zip`; legacy full-MD5
archives and retained multi-chart packages remain reusable after restart.
The current development source forces the ordinary LN/CN/HCN launcher selection
onto every existing long note, including authored BMS `#LNMODE` / `#LNOBJ` and
bmson header/per-note types. Casual/private Arena rooms apply their locked mode;
rated Arena always applies LN. Judgment, rendering and total-note counts use the
same effective mode. Chart files and source hashes are unchanged. Older replays
retain their recorded authored-mode behavior; new replays record the forced
policy. Affected legacy local scores are preserved separately when a new score
first replaces them. See [the compatibility and validation guide](docs/FORCED_LONG_NOTE_MODE.md).
This source change does not publish a new body or plugin package.

Version 0.4.14.63 moves the 0.4.14.62 opt-in physical-folder filter from
BMS-IR Features to Resource directly below BMS Path. Classic and Sidebar now
expose it from the same Resource controller. Saved per-player values, the
default-OFF behavior, BMS Path ordering, and all Music Select filtering and
exclusion rules remain unchanged.

Version 0.4.14.66 republishes the Windows body as a complete repair baseline.
The same signed body release now carries the PortAudio/JPortAudio DLLs plus
their pinned sources, licenses, build manifest, and SPDX SBOM. Launcher 0.2.28
therefore restores any missing native-audio files while applying the current
body update. Client behavior remains the same as 0.4.14.65.

Version 0.4.14.65 refines the default-off LR2-style fixed-HI-SPEED
override. Each play mode keeps an independent base scroll value, where 100 is
1.00x, and a reference BPM adjustable from 50 through 400 with a default of
150. The selected legacy OFF/START/MAX/MAIN/MIN HI-SPEED FIX target supplies
the chart BPM used by the calculation. The BMS-IR settings page can switch the
mode being edited, and the full and compact Arena overlays can toggle the
override and edit the current mode's base and reference values during Music
Select. The overlays now also provide an AUTO/current-chart or explicit-mode
editor selector and a numeric per-mode HI-SPEED change-step field from 0.00
through 10.00. A separate numeric field edits the selected mode's actual
HI-SPEED value from 0.01 through 20.00 and applies it to the live lane when a
matching play is active. Both overlays also expose the global START+6/7
lane-cover step as a labeled integer from 1 through 1000; it remains separate
from the per-mode HI-SPEED change step. The override is disabled for NO SPEED
constraints and is snapshotted into replays. It does not add a pseudo-FHS
chord or an equivalent-green editor.

The launcher Skin settings provide a default-on switch for the live Skin
Select preview. Turning it off skips preview construction, reload, and render
work while leaving skin selection and all custom options available. The choice
is stored per player in the normal player configuration.

The current development source also provides an optional neutral Skin Scene v1
resource for JSON and Lua skins. A scene can mix nested timelines, 2D/3D
affine and focal-camera transforms, textured meshes with perspective-correct
UVs, pivoted animation, affine/HSL color, seven named blend modes, frame
overrides, and projective nested hard rectangle masks at an ordinary
destination position. Scene resources treat `timer: 0` as state-relative,
retain batching until render or mask state changes, reload image-only edits,
and keep their generated quad upright for either canvas origin.
Skins without a scene remain on the legacy SpriteBatch path. The public format,
safety limits, and self-authored sample are documented in
[the Skin Scene format guide](docs/SKIN_SCENE_FORMAT.md).

Dynamic TrueType/OpenType skin text can optionally interpolate an upper and
lower `RRGGBBAA` color while retaining destination tint and alpha animation.
The two opt-in properties and compatibility behavior are documented in
[the skin text gradient guide](docs/SKIN_TEXT_GRADIENT.md).

At the Music Select root, a concrete key-mode selection now hides a difficulty
table only when every chart and course entry has a resolved mode and none
matches the selection. Mixed tables remain visible for each mode they contain,
ALL shows every table, and any legacy or incomplete table with an unresolved
entry stays reachable instead of being guessed away.

The current development source keeps the last non-fullscreen WINDOW or
BORDERLESS mode separately from the active fullscreen setting. F4 therefore
returns to the same window style even when fullscreen was saved on shutdown
and restored at the next startup. Existing configurations without the added
return-mode value infer it from their saved non-fullscreen mode and otherwise
retain the legacy WINDOW fallback.

The BMS decoder now applies the file's effective `#BASE` setting consistently
to `#WAVxx`, `#BMPxx`, extended BPM/STOP/SCROLL IDs, and `#LNOBJ`, even when
`#BASE 62` appears after those definitions. Ordinary base-36 charts and
base-62 charts that already declare the setting first retain their existing
interpretation and chart-file hashes.

The current development source also retains per-chart difficulty-table
comments in Music Select. `[[BR]]`, CRLF, and CR are normalized to LF, and the
active table entry is exposed to skins as string property `tablecomment`
(`1004`). The default Music Select skin uses a wrapped text area; custom skins
control its width, font size, placement, and practical visible line count.
Comments are contextual to the selected table and do not alter local song
metadata.

When BMS-IR is the configured Primary IR, the song context menu shows only the
dedicated `BMS-IR Leaderboard` instead of a second generic Primary IR entry for
the same service. A different configured Primary IR keeps its separately
labeled leaderboard beside the BMS-IR ghost/G-BATTLE and MANIAC route.

The current development source also removes two render-thread blockers from
gameplay startup: the PRELOAD transition no longer invokes `System.gc()` or
waits on an unfinished loudness-analysis task. Static BGA texture disposal and
upload stay on the render thread but advance with a small per-frame budget
before READY. Default-OFF timing diagnostics now correlate stalls and PortAudio
underflows with play-session/state IDs, maximum timestamps, direct-buffer
usage, and a safe stack sample for render stalls over 50 ms. See
[the timing diagnostics guide](docs/TIMING_DIAGNOSTICS.md).

The current development source keeps the Skin Widget Manager open independently
of the F5 parent menu and captures gameplay input only while ImGui actually
requests it. Width-only and height-only edits are included when the manager
exports its temporary current-scene changes to the clipboard. The manager does
not edit the separate BMS-IR Arena overlay or write skin source files.

Leaving Music Select now synchronously stops both its default BGM and any
explicit or generated song preview. A play transition can no longer interrupt
the preview fade and leave the selector BGM loop audible during gameplay; the
same shutdown path covers ordinary selection and BMS-IR Arena automatic starts.
OpenAL also uses the decoded duration of an existing explicit or discovered
one-shot preview, so its natural end restores the default selector BGM even
though LibGDX `Sound` does not expose a playback-completion query. Loop mode
continues to play the selected-song preview until the selection changes.

Version 0.4.14.61 also recovers BMS-IR body downloads across a
client restart by revalidating previously accepted archives before any network
request. New packages use portable readable names such as
`[Artist]Song-0123abcd.zip`, with the requested chart's eight-character MD5
prefix and the detected ZIP/RAR/7z extension. Existing `bmsir-<full-md5>`
packages remain compatible, and a retained multi-chart package can satisfy a
different chart request after restart. Targeted download-root scans wait behind
an active song update instead of being discarded, equivalent pending scans
coalesce, and body-only configurations keep their download-task progress
current. After the scan, the client confirms whether the requested chart
entered the song database. A ready notice tells the player to select the chart
again to start play; the download action itself does not automatically begin
gameplay.

Version 0.4.14.73 also loads referenced key sounds from a solid
7z archive in one bounded sequential archive pass before decoding them in
parallel. This avoids checksum failures and missing audio caused by reopening
one solid-compression block concurrently for hundreds of entries. The
temporary audio copies are removed after the chart finishes loading; the
original archive stays compressed and unchanged.

Independent readers of the same solid 7z are also safe when movie BGA
materialization overlaps that key-sound pass. Apache Commons Compress 1.26.2
removes the shared skip-buffer race present in 1.26.1, and a concurrent-reader
regression test covers later entries in one repository-owned solid archive.

The Resource built-in-table picker keeps configured built-in tables visible as
checked choices. Players can check new tables or uncheck configured built-in
tables, then apply both additions and removals together. The apply action is
available only when the selection changes; custom URL tables remain untouched.

The same picker now organizes 109 presets into `Beginner-friendly`, `BMS-IR
supported`, and `Other` sections with one shared search field. The beginner
section contains the BMS-IR cross-game master and 36 game-specific tables;
the game list is collapsed until opened or matched by a search. The BMS-IR
section contains all 33 supported table families, including the 13 presets
that were previously missing from the client list.

Play-skin previews now expose the synthetic session as ordinary play, include
a silent representative BGA, and drive gauge-increase and gauge-max timers.
Skins such as WMII can therefore construct their normal-play score graph and
BGA frame and animate gauge effects without leaving Skin Select.

The current development source separates Arena play cursor visibility from
pointer capture. When `Show cursor during play` is off, the cursor becomes
invisible after the short inactivity delay without being confined to the game
window. Mouse movement, the Mod Menu, and leaving play restore visibility;
ordinary non-Arena play keeps its existing inactivity-based cursor capture.

GitHub pushes do not publish official binaries automatically. Public packages
are built, signed where applicable, verified, and released through the BMS-IR
release procedure.

Every BMS-IR-built body or plugin made downloadable through the launcher is
covered by that procedure, including internal test and prerelease updates. A
distribution is not complete until both ordinary-score acceptance and the
Arena client-version/build gate are activated and verified where applicable.

## Arena oraja 0.4.14.81

Adds NANTOKA MANIA MODE to MANIAC OPTIONS. Supported solo charts use fixed
judgment windows, integer gauge arithmetic, and dedicated long-note handling.
Mode-on scores and replays use a separate local MANIAC identity and cannot be
submitted to online ranking or Arena. Launcher `0.2.29`, plugin `0.0.72`,
protocol v8, and all `0.4.14.80` behavior remain present.

## Arena oraja 0.4.14.80

Adds optional vertical color gradients to dynamic TrueType/OpenType skin text
in JSON and Lua skins. Both upper and lower `RRGGBBAA` colors are required;
the active destination tint and alpha multiply the gradient. Existing text
without both properties keeps the legacy single-color rendering path.
Launcher `0.2.29`, plugin `0.0.72`, protocol v8, and all `0.4.14.79` behavior
remain present.

## Arena oraja 0.4.14.79

Fixes Skin Scene projective shaders on macOS so the GLSL 150 core-profile
conversion rewrites every legacy `attribute` and `varying` qualifier,
including one at the start of the source. A regression test covers both
converted shader stages. Launcher `0.2.29`, plugin `0.0.72`, protocol v8,
Windows rendering, and all `0.4.14.78` behavior remain present.

## Arena oraja 0.4.14.78

Treats `timer: 0` like an omitted scene timer, retains batching until texture,
blend, camera, or the nested hard rectangle mask stack changes, reloads
textures after image-only edits, and keeps generated quads upright for both
canvas origins. The format exposes seven blend modes and nested hard rectangle
masks; it does not expose alpha/color masks. Launcher `0.2.29`, plugin
`0.0.72`, protocol v8, and all `0.4.14.77` behavior remain present.

## Arena oraja 0.4.14.77

Adds the optional neutral Skin Scene v1 resource for JSON and Lua skins. Scene
documents are compiled and validated before use, then render deterministic
timelines, nested 2D/3D transforms, perspective-correct textured meshes,
projective nested hard rectangle masks, color transforms, and seven named
blend modes. Invalid or non-finite input fails safely, retained resources are
bounded and disposed, and metrics plus a self-authored sample are included.
Skins without a scene keep the existing SpriteBatch path. Launcher `0.2.29`,
plugin `0.0.72`, protocol v8, and all `0.4.14.76` behavior remain present.

## Arena oraja 0.4.14.76

Adds direct 0.01--20.00 LR2 HI-SPEED editing and shared 1--1000 START+6/7
lane-cover step editing to both Arena overlay layouts. Japanese settings now
name the existing forced-LN conversion explicitly. At the Music Select root,
a concrete key-mode selection hides only tables whose resolved chart and
course modes are all incompatible; mixed and unresolved tables remain
reachable. Launcher `0.2.29`, plugin `0.0.72`, protocol v8, and all
`0.4.14.75` behavior remain present.

## Arena oraja 0.4.14.75

Adds a default-on per-player Skin Select live-preview switch. Turning it off
skips preview construction, reload, and rendering without disabling skin
selection or custom options. The full and compact Arena overlays can follow
the current chart mode or pin the LR2 HI-SPEED editor to any supported play
mode and edit its normal HI-SPEED change step through a validated 0.00--10.00
numeric field. Launcher `0.2.29`, plugin `0.0.72`, protocol v8, and all
`0.4.14.74` behavior remain present.

## Arena oraja 0.4.14.74

Updates Apache Commons Compress to 1.26.2, preventing false solid-7z checksum
failures when movie-BGA materialization overlaps the key-sound load. When
BMS-IR is Primary IR, the song context menu now shows one dedicated
`BMS-IR Leaderboard` instead of duplicating the same service through the
generic Primary IR entry. Other Primary IR services retain their separate
leaderboard. Launcher `0.2.29`, plugin `0.0.72`, protocol v8, and all
`0.4.14.73` behavior remain present.

## Arena oraja 0.4.14.73

Shows full-library chart totals and archive scan details in one song-update
result dialog. Resolves base-dependent BMS IDs using the effective `#BASE`
regardless of whether `#BASE 62` appears before or after the definitions.
Loads all referenced key sounds from a solid 7z archive through one bounded
sequential pass before parallel decoding, preventing checksum failures and
missing audio; temporary copies are removed and the source archive is
unchanged. Launcher `0.2.29`, plugin `0.0.72`, protocol v8, and the complete
`0.4.14.72` behavior remain present.

## Arena oraja 0.4.14.72

Restores the complete retained LR2oraja Endless Dream behavior found by the
exact 52-commit audit and locks the result in executable feature/history
gates. rian DX/X MODE and all rianIR submission/replay-send paths remain
excluded by product-owner decision. Launcher `0.2.29`, plugin `0.0.72`,
protocol v8, native audio, and every `.59` through `.71` release fix remain
present.

## Arena oraja 0.4.14.71

Hides the Arena play cursor without capturing or confining the OS pointer.
Mouse movement, the Mod Menu, and leaving play restore cursor visibility.
Ordinary non-Arena cursor capture is unchanged. Launcher `0.2.29`, plugin
`0.0.72`, protocol, score behavior, native audio, and bundled default skins are
unchanged.

## Arena oraja 0.4.14.70

Restores top-row `8` as the fixed Music Select compatibility shortcut for Show
All Charts while keeping physical NUMPAD 8 separately configurable. Launcher
`0.2.29`, plugin `0.0.72`, protocol, score behavior, native audio, and bundled
default skins are unchanged.

## Arena oraja 0.4.14.69

Keeps the transactional all-table refresh already present in the release line:
every configured table is downloaded and validated before live caches are
replaced, and any failure preserves the previous caches. Music Select now
stops its BGM and explicit or generated preview synchronously before ordinary
or Arena gameplay begins. OpenAL one-shot previews return to the default BGM
after their decoded duration. Default-OFF diagnostics add static-image BGA
cache-plan, collision, miss, and texture-upload measurements without changing
cache or rendering behavior. Bundled default skin assets and definitions are
unchanged. Launcher `0.2.29`, plugin `0.0.72`, protocol, score behavior, and
the Windows native-audio repair baseline remain unchanged.

## Arena oraja 0.4.14.68

Repairs the Skin Widget Manager so the child window remains open independently
of the F5 parent menu and no longer captures gameplay input merely because it
is visible. Clipboard export now includes width-only and height-only changes,
and the window explains its temporary current-scene scope. The separate BMS-IR
Arena overlay and skin source files are unchanged. Launcher `0.2.29`, plugin
`0.0.72`, protocol, score behavior, and the Windows native-audio repair
baseline are unchanged.

## Arena oraja 0.4.14.66

Republishes the Windows body as a complete native-audio repair baseline. The
body manifest includes the required PortAudio/JPortAudio DLLs together with
their pinned sources, licenses, build manifest, and SPDX SBOM, so Launcher
0.2.28 downloads any files missing from an existing installation. Client
behavior, Launcher `0.2.28`, and plugin `0.0.72` are otherwise unchanged from
0.4.14.65.

## Arena oraja 0.4.14.65

Removes the non-working pseudo FHS option and the BMS-IR-specific equivalent-
green editor/readout. The LR2-style fixed-HI-SPEED settings now have a real
mode selector plus independent 50–400 reference-BPM values, defaulting to 150.
Both full and compact Music Select Arena overlays can toggle the override and
adjust the current mode's base scroll and reference BPM. Ordinary skin green
number reporting remains based on actual BPM, cover, and LIFT; NO SPEED and
replay safeguards remain in force.

## Arena oraja 0.4.14.64

Adds a default-OFF LR2-style fixed-HI-SPEED override with independent base
scroll values per play mode and a fixed 150 BPM reference. Its equivalent
green number is SUD+ and LIFT aware in both Start Here and active play. An
optional pseudo FHS uses a clean short START+SELECT release while preserving
the normal exit hold at the larger of 500 ms and the configured delay. The
setting is replay-safe and ignored by NO SPEED constraints.

## Arena oraja 0.4.14.63

Moves the physical-folder visibility parent switch and BMS Path checklist from
BMS-IR Features to Resource directly below BMS Path. Classic and Sidebar use
the same Resource controller; saved player settings and filtering behavior are
unchanged.

## Arena oraja 0.4.14.62

Adds a default-OFF, per-player physical-folder filter to BMS-IR Features. When
enabled, one checkbox appears for each configured BMS Path and only checked
physical roots remain at the Music Select root. Zero checks creates a
difficulty-table-only root without filtering tables, courses, favorites,
commands, searches, Primary IR roots, or Arena candidates.

## Arena oraja 0.4.14.61

Restores ordinary LN/CN/HCN selection and mode-separated IR behavior. Arena
wire protocol v8 locks the selected mode for casual/private rooms and verifies
the decoded note count at the ready barrier; rated Arena remains LN-only.
All 0.4.14.60 recovery features and the safe all-table update remain present.

## Arena oraja 0.4.14.60

Restored the four feature groups that the 0.4.14.59 hotfix unintentionally
omitted: startup-settings search, gameplay-start render-stall mitigation,
BMS-IR body-download recovery, and difficulty-table comments exposed to Music
Select skins. The safe all-table update from 0.4.14.59 remains present, and the
Arena wire protocol remains v7.

## Arena oraja 0.4.14.59

Resource settings again provide one visible action to update every configured
difficulty table. The client fetches and validates the complete configured set
before replacing live caches. A successful update removes orphan `.bmt` files
left by URL edits or removals while preserving unrelated files; any fetch,
validation, or cache error keeps or restores the previous complete cache set.

## Arena oraja 0.4.14.58

The Windows x86-64 package replaces the repository's untraceable legacy
PortAudio/JPortAudio binaries with a clean, double-built native bundle. Fixed
source archive hashes, the MSVC/CMake toolchain, enabled host APIs, output
hashes, license selection, and full file inventory are recorded and verified
before packaging. ASIO and WASAPI remain available; failure to verify the ASIO
source or build fails the Windows package instead of silently distributing an
unknown binary.

The MIT-licensed JPortAudio Java sources are compiled directly with the body
instead of loading the old prebuilt JAR. JNA 5.13.0 uses its Apache-2.0 option.
The Windows launch scripts and portable launcher both pass the package's
`natives/` directory as the JVM native-library path. macOS does not receive the
Windows DLLs.

## Arena oraja 0.4.14.57

Within one running client, later requests from the same registered URL wait
for an active request and then reuse a retained package only after that package
is verified to contain the newly requested chart MD5. URL equality alone never
suppresses another package from a multi-download landing page.

Each accepted or verified reused package triggers a song-database scan of the
configured download root only. Parent-directory fallback is disabled for this
targeted update, so the downloaded compressed archive becomes visible without
rescanning an unrelated tree.

## Arena oraja 0.4.14.56

When the default-OFF BMS-IR body URL option encounters an HTML distribution
page, it can try up to 12 ZIP/RAR/7z links in document order. The same bounded
resolution applies to one archived Wayback page. Every candidate still has to
pass the existing archive checks and contain the requested chart MD5 before it
is retained compressed; failed tasks can be selected again safely.

Generated Music Select previews now pass their in-memory WAV to the WAV
decoder. Preview-only source decoding uses GC-managed buffers and explicit
limits of 256 sounds, 16 MiB per source, 64 MiB cumulative source data, 32 MiB
decode/PCM per sound, and 96 MiB retained PCM. A chart that exceeds a limit
keeps the normal Music Select BGM without caching a partial preview, while
ordinary gameplay key-sound allocation is unchanged.

## Arena oraja 0.4.14.55

The Other settings add `BMS-IR本体URLから取得` / `Download from BMS-IR body
URLs`. It is off by default. When enabled, a missing difficulty-table song can
try its BMS-IR-registered HTTP(S) archive URL, then one Wayback snapshot if the
live download cannot be installed. Existing IPFS, configured HTTP-provider,
and browser-page behavior remain unchanged.

Accepted ZIP, RAR4/RAR5, and 7z packages remain compressed in `http_download`.
The client enforces the 2 GiB download limit, archive structure/path/expanded-
size checks, and the requested chart MD5 before a no-overwrite install. These
checks are not antivirus scanning, so leave the setting off unless you accept
the risk of processing an untrusted archive and its media.

## Arena oraja 0.4.14.54

The default-ON difficulty-table level display now has a per-player switch.
Turning it off restores each chart's stored `#PLAYLEVEL` for display and LEVEL
sorting without changing ordinary folders or table assignments.

Music Select generates an 18-second in-memory preview when a chart has no
readable explicit or automatically discovered preview. One low-priority,
latest-request worker and an eight-entry LRU cache bound the work; explicit
preview priority and the existing `OFF`, one-shot, and loop modes are retained.

Default-OFF timing diagnostics write bounded JSONL summaries for render, input
dispatch, BGA decode/upload, audio calls/mixing, GC, and memory pressure. The
collector uses bounded atomic counters and a daemon writer; it does not change
input, judgement, keysound, BGA fallback, audio-buffer, or gameplay scheduling
policy. See [the timing diagnostics guide](docs/TIMING_DIAGNOSTICS.md) before
collecting a capture.

## Arena oraja 0.4.14.53

The native LibGDX game-window title remains exactly `Arena oraja` across body
updates so OBS capture rules do not need a version-specific title. Play skins
can read the resolved fixed lane placement during READY and PLAY through the
existing RANDOM refs.

On an LR2-style grouped song row, the configurable `Show All Charts` action
opens the retained variants as separate chart entries. It is available from a
physical NUMPAD shortcut and the song context menu without changing the global
difficulty-display setting or sending Arena selection traffic.

Skin Select play previews present their deterministic session as ordinary
play, declare a silent BGA, and drive gauge-increase and gauge-max timers. WMII
can therefore construct its score graph, BGA frame, and gauge effects in the
preview.

## Arena oraja 0.4.14.52

Startup configuration now uses one left sidebar across every settings tab,
with aligned labels and controls while preserving the existing settings and
controller behavior.

Inside difficulty-table folders, Music Select displays the first decimal
integer from each table entry as the chart level. This also applies to
aggregate folders such as `全曲`; ordinary folders and the chart's stored
`#PLAYLEVEL` remain unchanged.

The Resource built-in-table picker contains 109 presets grouped into
`Beginner-friendly`, `BMS-IR supported`, and `Other`. The beginner group is the
BMS-IR cross-game master plus 36 game-specific tables, and the BMS-IR group
includes 13 supported presets that were missing from the previous client list.
Configured built-in tables are checked and can be unchecked to remove them;
new checks add tables, all changes apply together, and custom URL tables are
left untouched.

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
cleanup. Song loading now finishes with one consolidated result: detected,
processed, and added/updated chart totals remain primary, while checked,
loaded, and unreadable archive totals appear as a secondary breakdown with the
causal rejection reason when applicable.

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

Missing difficulty-table songs can also opt in to direct archive downloads
from their BMS-IR-registered HTTP(S) body URL. A registered HTML distribution
page can resolve bounded ZIP/RAR/7z links, with a Wayback snapshot fallback
when the live route fails. The switch is off by default. Accepted ZIP/RAR/7z
packages remain compressed in `http_download`; the client applies bounded
page/network/archive checks and requires the requested chart MD5 before saving
without overwrite. These checks are not antivirus scanning. See
[`doc/BMSIR_ARENA.md`](doc/BMSIR_ARENA.md#bms-ir-body-url-downloads) for the
complete behavior and security boundary.

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
The navigation rail has a stable width and searches localized category names,
setting names, and their descriptions. A matching setting opens its category;
a true no-match query replaces stale page content with clear guidance.
Restrained single-color icons and grouped settings cards keep the content
boundary stable while changing pages. Every scalar row uses one stable
two-column layout: its label stays at the left and its editor or ON/OFF switch
uses the same trailing column. Standalone switches sit at the row's far-right
edge, with a persistent plain-language explanation below. Folder lists,
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
JDK 17 once. It also validates the complete ED 0.4.0 feature inventory and the
known distribution-to-`main` integration inventory before either lane starts;
each lane runs in its own worktree to avoid Gradle output races.

```sh
python3 tools/build_arena_release.py \
  --windows-worktree /release/oraja-windows \
  --macos-worktree /release/oraja-macos \
  --java-home /release/jdk-17 \
  --output-dir /release/build-0.4.14.81
```

`build-state.json` records both commands, durations, logs, source commit, and
artifact hashes. It also stages one `Arena-oraja.jar` under each OS-specific
`github-releases/` directory and records two prerelease targets in
`tenP0312-dev/oraja`: `test-<version>-windows-x86-64` and
`test-<version>-macos-aarch64`. Both tags must target the recorded reviewed
commit. Keeping the platforms on separate releases permits the same canonical
asset name on both, and GitHub's automatic source archives are then generated
from this body repository. The versioned filenames under `artifacts/` remain
internal build identities and are not public asset names. Do not recreate the
worktrees, download the JDK, or initialize submodules during every release;
refresh the prepared worktrees to the reviewed commit before invoking the
helper.

Run the same parity gate directly with
`python3 tools/check_feature_parity.py --root .`. The maintained inventory and
evidence rules are documented in
[`docs/ED_FEATURE_PARITY.md`](docs/ED_FEATURE_PARITY.md).

On Windows, use `gradlew.bat` instead of `./gradlew`. Generated jars are
written under `dist/`.

## Diagnostic logs

The client creates its Java/JUL log as `logs/beatoraja_log.xml` and its
bounded Arena diagnostic log as `logs/bmsir-arena.log`. Opt-in BGA, render,
input and audio timing summaries are written separately to the bounded
`logs/bmsir-timing.log`; see
[`docs/TIMING_DIAGNOSTICS.md`](docs/TIMING_DIAGNOSTICS.md) for activation and
metric limitations. The `logs/` directory is created automatically beside the
client data directories when either the configuration UI or gameplay starts.
Existing logs from older releases are left in place and are not migrated
automatically.

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
