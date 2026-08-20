# BMS-IR Arena client

Status: BMS-IR Arena v1 release branch. This source prepares the unified
`Arena oraja 0.4.14.58`. It replaces the separate Endless Dream and
beatoraja Arena bodies and lets one installation select LR2 or oraja
judgement/gauge behavior.

The current development source also advances Arena negotiation to protocol v8
for LN/CN/HCN. Casual/private rooms use the selected mode; rated Arena remains
LN-only. No release channel or existing v7 artifact changes until a separate
reviewed build and rollout.

Version `0.4.14.58` replaces the untraceable legacy Windows PortAudio and
JPortAudio binaries with a clean x86-64 build from pinned PortAudio 19.7.0 and
the official Steinberg ASIO SDK 2.3.4. The ASIO SDK's GPL-3.0-only route is
selected. CI verifies both source archives, builds the DLLs twice, rejects
different outputs, and records the exact toolchain, features, file hashes,
licenses, corresponding source, and SPDX SBOM. The JPortAudio Java source is
compiled with the body; JNA 5.13.0 uses its Apache-2.0 option.

The current development source expands the bundled default skins without
copying third-party skin assets. Music Select uses a 1920×1080 `POP//BELT`
layout with a right-side song list and stacked song, score, radar, comment, and
IR/rival information on the left. Its principal runtime PNGs are rendered from
committed SVG masters, and a square neutral placeholder remains visible when a
selected song has no banner. Music Select exposes the four MANIAC EXTRA MODE
states, the persistent judge-timing restore switch, and all nine sort states
including judge rank. Folder rows show lamp counts, while the `Song Detail`
skin option selects the existing table comment or a compact IR/rival summary.
The table-comment view replaces the notes-distribution/BPM graph under
CLEAR/PLAY with a high-contrast wrapped panel. The 7-key Lua
play skin adds three lane widths, a toggleable song/play-information overlay,
EARLY/LATE totals, and the resolved fixed
RANDOM lane values from references `450`--`456` and `459`. The Select controls use one
replaceable square-cell placeholder sprite; changing the artwork does not
change any action or property reference.

Version `0.4.14.57` serializes body-download requests from the same registered
URL, reuses a retained package only after confirming the requested chart MD5,
and performs the post-install song update against only the configured download
root with parent fallback disabled. Multi-package landing pages remain able to
continue to the correct later archive candidate.

The current development source saves new packages as portable readable
`[artist]title-<8-character-md5>.zip/.rar/.7z` files. It rediscovers those
packages and legacy `bmsir-<full-md5>` packages after a client restart, then
revalidates the requested chart before any network request. A retained
multi-chart package can therefore satisfy another chart request even when its
filename was derived from the first requested chart. A targeted download-root
scan is queued when another song update is active; equivalent pending scans
coalesce without losing their completion checks. Download-task progress is
reconciled when the BMS-IR body option is the only HTTP download path enabled.
Once the scan finishes, the client checks the requested MD5 in the song
database and either tells the player to select the chart again to play or shows
a clear registration warning.

Version `0.4.14.56` resolves bounded ZIP/RAR/7z candidates from registered HTML
distribution pages, including through one Wayback snapshot, while retaining
the requested-chart-MD5 and archive checks. It also loads generated in-memory
WAV previews correctly and applies explicit source, decode, and retained-memory
limits without changing ordinary gameplay key-sound allocation.

Version `0.4.14.55` adds the default-OFF direct HTTP(S) archive path for missing
difficulty-table songs. Accepted ZIP, RAR4/RAR5, and 7z packages remain
compressed and must pass bounded archive checks plus the requested chart MD5.
These checks are not antivirus scanning.

The current development source persists the last non-fullscreen WINDOW or
BORDERLESS mode independently from fullscreen. A client that enters
fullscreen, saves or exits there, and later starts in fullscreen therefore
returns to the original window style when F4 is pressed. Existing configs
without this value infer BORDERLESS from a saved BORDERLESS mode and otherwise
keep the legacy WINDOW fallback.

The current development source removes the PRELOAD render thread's forced GC
and blocking loudness-result wait. Loudness completion is polled while the
loading screen continues to render, with the existing timeout/fallback kept.
Static BGA textures retain render-thread/OpenGL ownership but their disposal
and upload are spread across bounded preparation steps; the client reaches
READY or sends Arena readiness only after the queue completes. The default-OFF
timing log adds per-period maximum timestamps, play-session/transition/chart
context, 16.67 ms render-stall events, one safe render-stack sample after 50
ms, DirectBuffer usage, and individual PortAudio underflow context. Windows
ASIO before/after acceptance remains a separate physical-client test.

Version `0.4.14.54` adds a per-player switch for difficulty-table display
levels, bounded in-memory preview generation for charts without a readable
preview, and default-OFF gameplay timing diagnostics. The diagnostic collector
observes render, input dispatch, BGA, audio, GC, and memory pressure without
changing scheduling or fallback policy.

The current development source provides a default-OFF physical-root filter in
Resource settings beside the configured BMS Paths. Enabling it reveals those
paths as checkboxes and keeps only checked physical root folders at the Music
Select root. An empty selection intentionally hides every physical root for
difficulty-table-only use. Descendants of a visible root remain unchanged, and
difficulty tables, courses, favorites, commands, searches, Primary IR roots,
and Arena candidates are never filtered by this setting.

Version `0.4.14.53` keeps the native game-window title stable for OBS, exposes
the resolved RANDOM lane placement to play skins during READY/PLAY, opens the
retained variants of an LR2-style grouped song row as separate entries, and
restores the ordinary-play/BGA/gauge state required by WMII and similar Skin
Select previews.

Version `0.4.14.52` reorganizes startup configuration into one left sidebar,
uses difficulty-table entry levels in Music Select, and expands the Resource
built-in-table picker to 109 presets grouped into Beginner-friendly, BMS-IR
supported, and Other. Configured built-ins can be unchecked and removed in the
same transactional apply operation that adds new tables; custom URL tables are
not changed.

The current development source assigns `選択曲の全譜面表示` / `Show all charts
for selected song` to physical NUMPAD 8 by default. Like the other physical
NUMPAD shortcuts, it can be reassigned or cleared in the startup configuration.
An unchanged legacy default shortcut map gains this NUMPAD 8 assignment on
load, while customized maps retain their stored assignments.
On an LR2-style grouped song row it opens a local virtual folder containing
only that row's retained difficulty variants as separate charts; it does not
query every chart in the physical folder, regroup the result, change the global
difficulty-display setting, or send Arena selection traffic. The song context
menu exposes the same operation as `全譜面を表示` / `Show All Charts`. Closing
the virtual folder returns to the original grouped song. The existing
`Related` physical-folder action and course component expansion remain intact.

Version `0.4.14.51` adds Windows WASAPI Shared/Exclusive selection and a
separate ASIO audio-driver choice. Existing configurations remain on WASAPI
Shared or their former non-WASAPI path. ASIO lists only Host API type 3 output
devices and does not silently rewrite an unavailable ASIO selection.

Version `0.4.14.50` makes the selected BMS Path itself the persisted work
folder. Its charts and descendants remain no-save/no-submit content without
creating a reserved child directory. The selected root is labeled and
protected from BMS Path removal; the 0.4.14.49 compatibility marker remains.

Version `0.4.14.51` adds a `WASAPI Mode` / `WASAPI モード`
selector to the Audio tab. It is enabled only when PortAudio and a Windows
WASAPI output device are selected. Shared mode remains the default and keeps
the existing JPortAudio path. Exclusive mode asks the bundled PortAudio 19.6
WASAPI backend for exclusive access, which prevents other applications from
using that output while Arena oraja is running. The device must accept the
selected 44.1 kHz or 48 kHz sample rate; when no sample rate is selected, the
device default is used. An unsupported device or format follows the existing
PortAudio startup-failure path and falls back to OpenAL. OpenAL, other
PortAudio host APIs, macOS, Linux, and existing configurations are unchanged.

The same Audio tab exposes `ASIO` as an independent Windows output driver.
Selecting it filters the device list to PortAudio Host API type 3 (ASIO) and
to devices with at least one output channel. The exact device name and Host
API are stored, the WASAPI mode control is disabled, and startup rejects a
non-ASIO or unavailable selection instead of silently rewriting it to OpenAL.
The selected mode, device, Host API, sample rate, and buffer size are logged.
The original 0.4.14.51 build used the previously bundled PortAudio DLL and its
blocking-stream path. Version 0.4.14.58 replaces that binary through the pinned
GPLv3 source/build process above without changing the saved audio behavior.
Driver-specific buffer-size lists and the ASIO control-panel button remain
optional follow-up work.

Version `0.4.14.49` adds the Resource-tab `Work folder` / `作業フォルダ`
button and the reserved `_BMSIR_TESTPLAY` authoring directory. Charts below
that directory do not save local records and are never submitted to IR or
Arena.

Version `0.4.14.48` adds a default-OFF Input-tab setting that keeps
dedicated game-controller/HID buttons, hats, and axes active while the game
window is unfocused. Keyboard, mouse, and mouse-scratch input remain
focus-bound, so a player can work in another application between songs without
letting ordinary typing operate Arena oraja.

The same version keeps HI-SPEED, green number, SUD+, LIFT, and HIDDEN live
through PRELOAD and READY. The final pre-start lane-setting state becomes the
next chart's initial state.

This release adds the default-OFF
`高レート基準の選曲を許可` setting. Rated selection keeps every level reached
by the player's active-season peak rating. Players who enable the setting no
longer lower the room ceiling; when at least one player leaves it disabled,
the lowest disabled player's peak remains the guard. It also includes named
public/code-only rooms, explicit between-game READY, custom-table rooms,
server-managed CPU play, and the combined GENOCIDE normal ☆1--☆13 /
official発狂 ★1--★25 rated selection.

Version `0.4.14.36` adds a native `難易度表編集` overlay tab. It loads only
tables owned by the signed-in BMS-IR player, can create or rename a table, and
can add/update/remove the chart currently selected in Music Select with a level
and comment. Ordinary players keep one active table. The BMS-IR system owner
(player 190000) can explicitly select one of multiple owned tables or create
another one; no table is chosen implicitly when several exist. Each mutation
includes the selected table ID and its last server revision, so another
player's table cannot be targeted and a concurrent Web/client edit reloads the
authoritative selected table instead of being overwritten. Child-table levels
managed by a cross-game master are shown read-only and continue to come from
the master.
Version `0.4.14.37` lets an existing table hold metadata and up to 64
selected-chart add/update/remove changes as an in-memory draft. The overlay
shows the pending count and
list, supports per-change undo and discard-all, then saves the complete draft
atomically. A revision conflict keeps the draft, loads the latest server state,
and requires an explicit review/rebase before retry. Reload and table switching
cannot silently discard pending work; drafts are lost when the client exits.
New-table creation remains immediate so the server can assign its ID.
Successful responses replace one stable in-memory table bar from the selector
root on the render thread, so one batch causes one hot reload without a game
restart or stale folder objects. Empty tables appear after their first saved
chart. Pasted bulk import, ordering, and My Dan/course editing remain on the
Web editor.

The selected-table snapshot may include an optional `aggregate_folder` label.
When present, the client keeps every authoritative level folder and appends one
display-only folder containing the same complete chart set. It does not add
duplicate snapshot entries or draft/edit identities. The cross-game all-song
master uses this field for `全曲`; ordinary personal and system tables omit it.

Difficulty-table entry comments are retained for ordinary bmstable imports and
the in-memory My Difficulty Table. `[[BR]]`, CRLF, and CR normalize to LF.
Music Select skins read the active entry through string property
`tablecomment` / `1004`; an entry from another table or an ordinary local
folder cannot leak into it. JSON-skin text objects can set `wrapping: true` and
choose destination width and font height, so the skin determines the effective
characters per line and visible line layout. The bundled default select skin
provides one wrapped presentation, but no engine-owned comment overlay is
forced over custom skins. The My Difficulty Table editor accepts up to 4,096
Unicode characters and shows LF as `[[BR]]` in its single-line IME editor.

In the same version, clicking an Arena room-name, chat, or My Difficulty Table
text field places an
IME-capable OS text control directly over the same field. Japanese conversion
text is therefore visible in its normal input position before it is committed,
without a separate dialog. These fields reserve UTF-8 capacity by the
documented Unicode-character limit. On macOS the Swing control runs in a
short-lived helper JVM, keeping its AppKit event loop separate from the
JavaFX/LWJGL body. If that helper does not become ready within five seconds,
exits, or returns invalid protocol data, the underlying field and body input
are unlocked automatically. Other platforms retain the in-process control
with the same startup fail-safe. While an ImGui item or the inline editor
owns input, keyboard, NUMPAD shortcuts, mouse scratch, scroll, clicks, and
drags are discarded before the underlying game or skin can consume them.

Version `0.4.14.35` added a standalone optional IIDX FHS after the five legacy
Music Select HS-FIX values. The current development source removes that FHS
pending a new specification and safely migrates its saved value to START BPM.
The independently configurable judge-rank sorter and its unsupported-skin
notice remain available.
Version `0.4.14` adds LR2-compatible MANIAC OPTIONS, MANIAC-owned Double
Battle and AUTO SCRATCH, isolated MANIAC local and online records, a
mode-following leaderboard and ghost,
vanilla DB export, split Arena graph/status presentation, private-room records,
Japanese/English built-in UI, and the portable signed-update launcher.
Version `0.4.14.34` recognizes monotonic adjacent-key SP-to-DP stair phrases
of at least three notes and alternates their DP sides. The inclusive per-step
limits are 333,334 microseconds for LEVEL 1, 111,112 for LEVEL 2, and 83,334
for LEVEL 3. Simultaneous adjacent normal keys are evaluated as one chord and
split across opposite sides whenever the existing scratch and key-LN safety
reservations allow it. Stair and chord structure now takes priority over
same-keysound and measure-balance preferences; scratch phrase gaps and hard
reservations are unchanged. The revised placement uses a dedicated SP-to-DP
placement identity so older internal-test scores and ghosts remain isolated.
Version `0.4.14.33` removes the 350 ms wait when a Music Select START or
SELECT short press is configured as none. The corresponding option panel opens
on the first pressed frame because there is no short action to distinguish;
the same-frame START+SELECT detail chord and START plus a playable key remain
higher priority. Difficulty and key-mode short actions keep the existing
350 ms release/hold split.
Version `0.4.14.32` makes the configured key-mode checks global Music Select
visibility filters as well as cycle choices. START and SELECT each have an
independent short-press choice (none, difficulty, or key mode); holding START
opens play options, holding SELECT opens assist options, START plus a playable
key remains immediate, and START+SELECT still opens detailed options. In LR2
grouped display, a difficulty change now applies one shared difficulty stage
to every grouped song in the current list. Separate-row display still moves
the cursor only within the selected song.
Version `0.4.14.31` fixes configured key-mode cycling at the root and on
folder-only lists. Difficulty SELECT now offers separate rows with cursor
movement or an LR2-style grouped row; existing players default to separate
rows. Both difficulty displays retain the current folder/table/search scope.
Version `0.4.14.30` makes Music Select's SELECT-only action configurable. The
legacy option panel remains the default; the alternatives use a release before
350 ms to cycle either LR2-style grouped difficulties or an allow-listed key
mode, while a 350 ms hold opens the existing option panel. START+SELECT still
opens detailed options. Difficulty groups are limited to charts visible in the
current folder/table/search result and require the same song folder and key
mode. Key-mode cycling skips unchecked modes and modes absent from that list.
Version `0.4.14.29` stops repeated scratch keysounds from pinning separated
SP-to-DP scratch phrases to one side. Same-keysound stability remains for
normal keys; connected scratch phrases, long-scratch reservations, and key-LN
safety are unchanged.
Version `0.4.14.28` narrows the adjacent-scratch merge thresholds for SP-to-DP
LEVEL 1--3 to 320, 240, and 160 ms respectively. Transitive chaining remains
unbounded: a connected phrase stays on one side regardless of its total
duration or scratch count.
Version `0.4.14.27` makes SP-to-DP scratch phrases reserve one DP side for
their complete duration, including long-scratch bodies and guard time. Normal
keys and key-long-note holds that overlap the reservation are forced to the
other side.
Version `0.4.14.25` adds MANIAC SP-to-DP levels 1--3 with isolated local and
online records, deterministic ranking identities, ghosts, and owner-score
sync. It also shows each waiting player's name, integer rating, grade, and
waiting state in the existing Arena participant columns.
Version `0.4.14.24` collects the completed client refinement batch: it keeps
the built-in F5 and Ctrl+Shift+F5 Arena menus consistently localized, suppresses
gameplay INFO popups for NUMPAD judge-auto and timing actions, preserves
BORDERLESS through fullscreen changes, fixes Start Here green-number previews,
and adds judge-rank plus title-stable level sorting. It also contains the
HTTPS/WSS migration and bundled select-skin fixes prepared in `0.4.14.23`.
Version `0.4.14.23` corrects legacy saved `http://` Arena server values to
`wss://`, so the value remains valid for the Arena WebSocket client, and fixes
the bundled default song-select skin's JSON comma and radar draw order.
Version `0.4.14.22` adds the underlying data model and skin-object
rendering support for an IIDX-style chart-tendency radar graph
(NOTES/PEAK/SCRATCH/SOFLAN/CHARGE/CHORD) at song select
(`SongData.getNotesRadar()`, `SkinRadarGraph`, the JSON skin
`radargraph` element, and six new `NUMBER_RADAR_*` skin properties for
numeric display). It ships engine-only; no bundled skin references it
yet, so nothing changes visually until a skin's JSON opts in.
Version `0.4.14.21` fixes MANIAC Double Battle plays showing the ordinary
(non-MANIAC) lamp immediately on returning to select instead of the correct
MANIAC lamp, caused by the play session's mode doubling (BEAT_7K ->
BEAT_14K) leaking into the select-screen SongData.
Version `0.4.14.20` removes the on-screen "Next play will not be submitted
to IR" popup for MANIAC/freq-trainer plays; the underlying no-submit
behavior is unchanged, only the notice is gone since the player already
knows they enabled the option.
Version `0.4.14.19` forces the MANIAC API to HTTPS and auto-upgrades legacy
`ws://` Arena server settings to `wss://`, keeps BORDERLESS mode across a
fullscreen round-trip, fixes the Start Here green-number calculation to
include SUD+ lane cover and LIFT together, and localizes the remaining
hardcoded English strings in the Arena overlay.
Version `0.4.14.18` collapses existing long notes before LR2 EXTRA MODE,
ADD NOTES, and LOUDNESS generation, keeps MANIAC IR targets on the active
isolated leaderboard during immediate chart starts, and makes the legacy
CONSTANT skin property read the same selected PlayConfig as its toggle.
Version `0.4.13` unifies the in-game song ranking as `BMS-IR Leaderboard`,
restores a persistent F5 overlay switch, adds judge-timing restoration with a
Lua API, makes INFO toasts optional, removes duplicate startup IR logins, and
recovers the existing player selection when a fresh system config has not yet
stored its player ID. DP Arena options use one compact `RAN / -` line and append
`/ FLIP` on the same line when enabled.
Version `0.4.12` adds an in-window startup progress log, three configurable
START+6/7 modes, and individually configurable physical NUMPAD 0--9 shortcuts.
Version `0.4.11` adds main-skin Arena target injection, fixed entry-order score
graphs, fixed four-player rated BO2, EASY/NORMAL/HARD hidden-rating server
CPUs, CPU-inclusive four-player Elo settlement, and CPU-free public waiting
lists. Version `0.4.10` shows the same Arena error only once per match and
message, while retaining every occurrence in the diagnostic log. Version
`0.4.9` fixes series participant intrusion and settles post-start
withdrawal as a rated walkover/forfeit, keeps clear/FAILED results until Arena
accepts them, synchronizes SP/DP live scores at 4 Hz, fixes 14KEY-only rooms and
text-input leakage, restores F5 recovery and the fill countdown, and loads the
packaged IR plugin in Java-bundled releases. Version `0.4.8` makes rated BO2 placement and rating depend only on the two-round
point total, labels EX rate as reference-only, and allows Backspace or Delete
as Arena overlay shortcuts. Version `0.4.7` keeps CPU BO2 running at five-second intervals while one
player waits, chooses the CPU chart from every owned chart in the inclusive
six-band range from the player's rated ceiling down through ceiling minus
five, and chooses each CPU final EX SCORE from A through MAX. Version `0.4.6`
adds backup-safe, per-player local synchronization of the
class/Dan courses received from BMS-IR Primary IR. Version `0.4.5-dev` adds a
startup `BMS-IR固有設定` tab for one-bass and the
first-timing preview, persists both switches in the backup-safe BMS-IR
sidecar, and displays the preview as soon as the loading play screen can draw
the resolved chart and skin notes. Version `0.4.4-dev` permits one-bass during ordinary play while the Arena WSS
is merely connected or queued, while continuing to block it after reservation
and during Arena play. Version `0.4.3-dev` reduces the READY start-chart preview
to the first playable timing and makes a new one-bass placement replayable from
its ordinary RANDOM seed alone. Version `0.4.0-dev` added the original
ordinary-play LR2 one-bass input and READY preview, Lua play-skin accessors for the live
HI-SPEED margin and recent key/scratch FAST/SLOW direction, a bundled
SP/DP random-placement browser view for OBS, progressive CPU score graphs,
disconnect/reconnect labels, and bounded Arena chart-start diagnostics.

## Startup configuration layout

The startup configuration `Other` tab stores one system-wide layout choice:
`Classic` or `Sidebar`. A missing value, including every configuration written
before this option existed, resolves to Classic. Both layouts reuse the same
JavaFX setting controls and the same `config_sys.json` / player configuration
write paths; changing layout does not duplicate or migrate gameplay settings.

Sidebar places the existing Video, Audio, Input, Resource, Music Select, Play
Options, Skin, Other, BMS-IR Features, IR, Table, Stream, Discord, and OBS
destinations in a fixed-width left navigation list. Search covers the localized
category title and description plus every setting title and description. When
the selected category does not match, the first matching category opens; when
nothing matches, the right pane shows a no-result explanation instead of stale
settings. Each row uses
the same restrained single-color line style and the selected row retains a
text-and-background active state. Its player summary shows the current player
ID plus display name and LR2/oraja rule profile, and expands the existing player
controls into the right pane on demand. The selected page starts directly with
its setting cards because each setting carries its own persistent explanation.
Every Sidebar destination uses grouped row cards. Scalar settings use one
stable two-column layout: the visible name remains left-aligned and one wide
editor or ON/OFF switch occupies the shared trailing column, with a persistent
plain-language explanation below. A standalone switch and its ON/OFF label sit
at the far-right edge of that column. Complex editors keep their useful shape
instead of being compressed into scalar rows. Controller tables, BMS roots,
difficulty tables,
skin options/previews, local-table editors, Discord Webhook destinations, and
OBS scene mappings appear as full-width workspace cards with an explanation
above them. Scalar Sidebar controls stay bound to the original controls.
Complex editor nodes move into their Sidebar card only while Sidebar is active
and return to their exact original parent and position when Classic is
selected. The original controllers and controls therefore remain the single
configuration state used by Classic and by the existing save path.

The layout choice applies immediately so it can be previewed before leaving
the `Other` page, then persists with the normal configuration save. Classic
retains the prior top-tab layout.

## Resource settings and song-library updates

Version `0.4.14.39` keeps the song-library controls inside the Resource tab and
places them beside short descriptions of their scope. `Load songs` checks
added or changed songs immediately. `Full song update` first warns that it
will reread every registered song. The controls do not appear while another
configuration tab is open.

Right-clicking a configured BMS root can update only that root, open or copy
the path, or remove the root from configuration. Removing a root never deletes
its files, and the configured download root cannot be removed this way. A
selected-root update passes that root to the existing database scanner, so
unrelated roots are neither rescanned nor deleted.

Difficulty tables are shown in one active list. A row context menu can update
one table, edit a custom URL, change its order, or remove it. `Choose built-in`
opens a compact checkbox list that manages the built-in selection in both
directions. Checking a new table adds it, while unchecking a configured
built-in table removes it from the active Resource list. The apply action is
enabled only while that selection differs; custom URL tables remain untouched.
The picker groups its 109 presets into beginner-friendly, BMS-IR-supported,
and other sections. Beginner-friendly contains the cross-game all-song master
and 36 collapsible game-specific tables from BMS-IR. The BMS-IR section holds
the 33 supported table families. One search field filters names,
descriptions, and URLs across every section and opens matching groups.
`Load from URL` handles custom tables separately. Existing table URLs, active
ordering, BMS roots, and archive-scanning settings remain compatible with
saved profiles.

### Work folder

Select a configured BMS root and click `Set Work Directory` /
`作業フォルダに設定` below `Set DL Directory`. The selected BMS Path itself and
all descendants become disposable authoring content. The action persists only
the selected path; it does not create, move, delete, or open a directory. Use
the existing BMS Path context-menu action to open it.

Such a play keeps its on-screen result but does not write the local
score, lamp, play/clear count, score history, or replay. It also cannot submit
to ordinary IR or MANIAC IR and is excluded from Arena possession and
nomination paths. The same rule applies to archive virtual paths below the
selected work root.

設定済みBMSルートを選択し、`Set DL Directory` の下にある
`作業フォルダに設定` を押すと、選んだBMS Pathそのものと下位フォルダが作業対象に
なります。この操作は選択したパスだけを保存し、フォルダの作成・移動・削除・表示は
行いません。開く場合はBMS Pathの右クリックメニューを使います。

リザルト自体は表示しますが、スコア、
ランプ、プレイ／クリア回数、スコア履歴、リプレイは保存せず、通常IR、MANIAC IR、
Arenaにも送信・候補登録しません。

Version 0.4.14.49 compatibility is retained: `_BMSIR_TESTPLAY` as an exact,
case-insensitive directory component remains a no-save/no-submit safety marker
for every descendant and archive virtual path. A filename or longer directory
name that merely contains the text does not match. 0.4.14.49との互換性のため、
完全なフォルダ名 `_BMSIR_TESTPLAY` も引き続き大文字・小文字を区別しない安全用
マーカーとして扱います。

If any chart in a course is below the configured work folder or matches the
legacy marker, the aggregate course score, course replay, and course IR
submission are disabled. Ordinary charts elsewhere in that course retain
their existing per-chart save behavior. The same rule applies in Japanese:
コース内に作業フォルダ（互換マーカーを含む）の譜面が1つでもある場合は、コース
全体のスコア・リプレイ・IR送信を無効にし、それ以外の通常譜面の単曲保存は従来
どおりです。

## Difference-chart drag and drop

While Music Select is showing an ordinary physical song, loose `.bms`, `.bme`,
`.bml`, `.pms`, and `.bmson` difference-chart files can be dropped onto the
game window. The client copies every accepted file into the selected chart's
physical folder, rescans only that folder into the song database, and refreshes
the current song list after the update completes. Source files are never moved.

The whole batch is checked before copying. Existing or duplicate destination
names are not overwritten, and a failed copy removes files created earlier by
that batch. Drops are rejected while another song update is running or Arena is
preparing a match. Directories, full song packages, ZIP/RAR/7z files, arbitrary
assets, and archive-backed selected songs are outside this feature and remain
unchanged.

## ZIP/RAR/7z song archives

Version `0.4.14.38` added the opt-in archive scanner and version `0.4.14.41`
completed RAR5/7z, replacement, lookup, refresh, preview, and diagnostic
coverage. Version `0.4.14.42` exposed child folder bars when a parent also had
direct songs, but that also exposed ordinary per-song containers that are not
selection levels. Version `0.4.14.43` instead merges charts from an immediate
ZIP/RAR/7z virtual container into the physical parent's song list, keeps
ordinary song containers hidden, and works with an already-scanned database.
The archive setting is labeled
`Scan ZIP/RAR/7z song archives` (`ZIP/RAR/7z内の曲を走査`). It is off by
default so existing song-database update
behavior and startup cost do not change until the player enables it. After the
setting is enabled, run a normal song-database update for the configured song
roots.

The scanner recognizes BMS, BME, BML, PMS, and BMSON charts inside `.zip` and
`.rar` files (RAR4 and RAR5) and `.7z` files. The filename must use one of
those three supported suffixes, but the reader is selected from the content
signature; for example, a ZIP file accidentally named `.rar` is still read as
ZIP. Chart references for key sounds, preview music, stage, banner, back
images, image BGA, and movie BGA are resolved inside the same archive. Entries
use stable virtual paths such as `songs/pack.zip!/folder/chart.bms`; the
original archive is not moved, renamed, rewritten, or expanded into the song
library. A single entry may be copied to a bounded operating-system temporary
cache only when an existing decoder accepts file paths instead of streams.
The cache holds at most 128 entries and 2 GiB, removes its live entries on
normal shutdown, and removes older orphaned files on a later startup without
touching temporary files owned by another running client.

ZIP entry names use UTF-8 and fall back to Windows-31J for legacy Japanese
archives. Archive lookup names use Unicode NFC normalization and
locale-independent case folding, while the original entry spelling is kept
for archive I/O. Canonically colliding names, path escapes, duplicate names,
7z anti-items, excessive entry counts, and excessive expanded sizes are
rejected before charts are indexed. Encrypted, split/multi-volume, and nested
archives are unsupported and fail locally without changing the source file.
OSU charts inside archives are not indexed.

All chart directories inside an archive are presented from one archive folder
so deeply nested charts remain reachable. Same-folder grouping still uses each
chart's real directory, and automatic preview discovery is performed per chart
directory instead of selecting one preview for the whole archive. Refreshing
an archive folder re-enumerates its physical archive. If that read is rejected
or the containing directory cannot be read, the update fails closed and keeps
the last indexed folder and songs. The client logs the causal rejection reason
and shows loaded/rejected archive totals when the update finishes.

### BMS-IR body URL downloads

Version `0.4.14.55` adds an independent, default-off
`Download from BMS-IR body URLs` option under Other settings. When a
missing difficulty-table chart carries an HTTP(S) body URL, selecting or batch
filling that chart uses the registered URL. A direct ZIP/RAR/7z response keeps
the original path. Version `0.4.14.56` additionally recognizes bounded HTML
distribution pages. A response recognized as HTML is limited to 2 MiB and may
contribute at most 12 HTTP(S) ZIP/RAR/7z anchor links in document order. Each
candidate is downloaded and independently validated; scripts, forms, browser
automation, nested landing pages, and non-archive links are ignored. If the
live route cannot be installed, the client asks the Wayback Availability API
for one archived snapshot of the registered URL. An archived landing page may
resolve its rewritten archive link through the same bounded path. With the
option disabled, or when an entry has no eligible body URL, the existing IPFS
and configured HTTP provider routes keep their previous behavior. BMS-IR's own
`/new/song` page is treated as a browser page rather than an archive URL.

Body downloads are limited to 2 GiB and ignore the remote filename and
`Content-Disposition`. A response is first written to a generated staging
file, recognized only by a ZIP, RAR4/RAR5, or 7z content signature, and passed
through the existing archive entry/path/count/expanded-size/encryption checks.
Every request and redirect rejects resolved loopback, link-local, private,
multicast, and other non-public network destinations. Direct local/private
targets from registered pages and their archive links are therefore rejected.
At least one BMS, BME, BML, PMS, or BMSON entry must have the exact MD5 requested
by the table. Only then is the file moved without replacement to a portable
`http_download/[artist]title-<first-8-md5>.<format>` name and the song database
updated. The label comes from the difficulty-table artist and full title;
unsupported filesystem characters and controls become underscores, an absent
artist omits the bracketed prefix, and an overlong label is truncated without
removing the MD5 suffix or detected format. Rejected, oversized, ambiguous,
mismatched, and duplicate downloads leave no staging file and never overwrite
an installed archive.

Starting with version `0.4.14.57`, body downloads from one registered URL are
serialized. Before another network request, each package already retained for
that URL is checked with the same bounded archive reader and requested-chart
MD5 rule. A package is reused only when it contains that exact chart; matching
the registered URL by itself is not sufficient, so a landing page with several
different song packages can still resolve and retain the correct later link.
After either a new install or a verified reuse, the in-process song update
scans the configured download root directly and never falls back to scanning
its parent directory.

After restart, the client checks readable managed archives and legacy exact
`bmsir-<full-md5>.zip/.rar/.7z` archives in that root with the same bounded
archive and requested-chart-MD5 rules before reuse. This also recovers a
multi-chart package named for another chart in that package. An invalid archive
at the requested destination is never overwritten. If another song update is
active, this targeted update waits in a queue instead of being discarded;
equivalent pending requests share one scan while retaining each chart's
completion check.

Selecting an unavailable chart starts the download and keeps Music Select
active. It does not automatically start gameplay. After the queued or immediate
scan completes, a successful MD5 lookup displays a ready notice; select the
chart again to play it. If the archive was saved but the chart still is not in
the song database, the client shows a warning and asks for another update of
the configured download root.

Download-task identity uses both the registered URL and requested chart MD5,
so multiple charts carried by one package are not mistaken for the same task.
Selecting the same failed chart again or pressing Retry resets and reruns its
existing task. Active and completed identical tasks remain duplicate-protected,
and a retried task moves back from the expired list to the running list.

The accepted archive stays compressed; this path never extracts its contents
and never executes a file stored inside it. Enabling the body-download option
also enables archive indexing for that client session so the retained package
can be played. These format, structure, size, and chart-identity checks are not
antivirus scanning. Archive parsers and media decoders still process untrusted
data when the song is indexed or played, so players who do not accept that risk
should leave the option off and may scan the retained archive with their OS
security software before playing it.

When the selected chart has neither a readable explicit preview nor a
per-directory automatically discovered preview, version `0.4.14.54` builds an
18-second preview in memory. It chooses an eight-second dense region
between 25% and 80% of the playable-note distribution, favors the middle of the
song, and begins 500 ms before the chosen region. The bounded renderer retains
background audio that began before the window, ignores invisible notes and
mines, and supports layered or sliced BMSON audio through the same
`SongResource` path used by ordinary files and ZIP/RAR/7z entries.

Generated previews use a fixed 44.1 kHz stereo output, a 500 ms fade-in, a
one-second fade-out, and peak limiting. One daemon worker runs at minimum
priority, only its newest selection may publish a result, the pending queue is
limited to one request, and the in-memory WAV cache is an eight-entry LRU.
These previews do not create song-library files and continue to follow the
existing `OFF`, one-shot, and loop setting.

The current development source sends that in-memory resource through the WAV
decoder with a `.wav` display suffix and keeps preview-only PCM in GC-managed
buffers. A generated preview is abandoned before a partial result can be
cached when its plan needs more than 256 sounds, one source exceeds 16 MiB,
cumulative source data exceeds 64 MiB, one source exceeds its 32 MiB decode/PCM
budget, or retained decoded samples exceed 96 MiB. The selector then keeps its
ordinary default BGM. These limits do not change gameplay key-sound loading or
the priority of explicit and automatically discovered previews.

Archive cache revisions combine filesystem identity/change metadata with
sampled content so ordinary replacements are noticed even when file size and
modification time were preserved. This revision also invalidates any
materialized single-entry resource left in the in-process cache.

The decoded chart data keeps the same MD5/SHA-256 identity as an unpacked
copy. IR records, replays, tables, courses, and Arena chart possession
therefore continue to match by chart hash rather than by the virtual path.

Music Select keeps child folders visible when their parent also contains
directly indexed charts. This includes ZIP/RAR/7z archive folders, so an
archive remains reachable from a mixed ordinary-song folder before the
selected key-mode filter is applied to its charts.

## Enabling

1. Configure the normal BMS-IR IR entry with the BMS-IR player ID and game
   password.
2. In the IR configuration screen, enable `BMS-IR Arena`.
3. Keep the default WSS server unless a developer is running a controlled
   local service.
4. Start the client, then use the `BMS-IR Arena` overlay to enter matchmaking.

The launcher setting `判定・ゲージ` selects the rule profile used for ordinary
play and for creating managed rooms:

- `LR2`: LR2 judge windows, gauge behavior, default TOTAL, multi-BAD note
  selection, and LR2oraja long-note late-BAD handling.
- `oraja`: the original beatoraja rule set for each key mode, including
  single-target BAD selection and the original long-note late-BAD handling.

Rated Arena is always LR2. Managed rooms use the host's selected profile.
Every participant must use
a client compatible with that room profile; a match never mixes the two rule
sets. Arena temporarily applies the server-selected profile and restores the
launcher setting after the chart. The normal IR plugin uses the rule saved in
the completed score, so changing the launcher setting afterward does not move
an LR2 result into the oraja ranking or vice versa.

Rated Arena uses a two-chart points-only series. Managed rooms can select one chart,
play one shuffled nomination from every participant, or run a shuffled
first-to-2..5 series. Multi-chart formats show round wins and nomination
progress. Single-chart rooms can use all-player, host-only, or
rotating selectors; the host can assign the selector, transfer HOST, or kick
a participant. Participant roles, READY state, and chat are shown together in
the room lobby.

The startup switch controls the real-time connection. The authenticated
in-game overlay controls entry, waiting cancellation, and match withdrawal.
It also shows the current rating, an up-to-eight-player real-time vertical EX
graph with MAX/AAA/AA/A guides and per-player OP, live/final result details
with clear lamps, and the Arena rating leaders. During play, the graph opens
at the bottom center and can be moved and resized; ImGui stores the adjusted
position and size in `layout.ini`. SP and DP use separate saved layouts.
The settings tab selects normal, compact, or hidden display, controls the
play-time mouse cursor, and enables optional mutual unrestricted matching,
higher-basis chart selection, Arena target injection, score-graph order, and a
mirrored synchronized-RANDOM layout.
The overlay shortcut accepts any keyboard key, either alone or as an exact
multi-key chord, and defaults to Ctrl+Shift+F5. Hold the desired keys and
release all of them to register the chord. Escape cancels capture, while
the explicit `解除` button clears the shortcut. Backspace and Delete can be
assigned alone or inside an exact chord. Left/right Ctrl, Shift, and Alt are
treated as the same logical modifier. The unmodified F5 menu always includes
the persistent `Show BMS-IR Arena Overlay` checkbox. Re-enabling it restores
the last normal or compact display mode.
BMS-IR-specific settings are stored per player in the allow-listed
`bmsir_arena.json` sidecar. The first 0.4.1-dev or later start migrates existing
Arena values from `config_player.json`; 0.4.5-dev adds the one-bass and
first-timing preview switches, 0.4.6 adds the Dan local-sync switch, 0.4.11
adds Arena target and graph-order switches, and 0.4.13 upgrades the sidecar to
schema 7 for cover HI-SPEED recalculation, judge restoration, INFO toasts, and
the last visible overlay mode. Version 0.4.14 upgrades it to schema 10 for
MANIAC, Double Battle AUTO SCRATCH, Arena language, graph presentation, and
detailed logs. SP TO DP upgrades the sidecar to schema 11, SELECT actions to
schema 12, the difficulty display choice to schema 13, and independent
START/SELECT actions plus the shared difficulty stage to schema 14. The
judge-rank sort cycle and its skin notice upgrade it to schema 15. Historic
schema-15 IIDX FHS keys are ignored and removed on the next sidecar save. The
missing-table-song filter upgrades the sidecar to schema 16, the
difficulty-table LEVEL display switch upgrades it to schema 17, and the
physical-root visibility filter upgrades it to schema 18.
Later saves by a non-BMS-IR body cannot erase them. The sidecar uses the same
backup-safe write mechanism as player config and never contains IR user IDs,
passwords, or unrelated player settings.

Holding F2 for about one second on Music Select opens the one-column MANIAC
OPTIONS screen; a short F2 press keeps the existing refresh command. The
short refresh command recognizes root tables supplied by an exactly named
`BMS-IR` Primary IR. It fetches the complete Primary IR table response in the
background, then replaces only those BMS-IR-derived root tables together on
the render thread. Local tables, My Difficulty Table, favorites, and command
folders remain unchanged. A failed fetch or failed conversion keeps the
current tables, and a second request is rejected while one fetch or pending
application is active. The start, success, failure, and already-running states
use the built-in Japanese/English notifications. If the service is still
building a registered-rival snapshot, the plugin can return its last completed
snapshot; press F2 again after that build completes to load the newer snapshot.
Version `0.4.14.40` makes the normal startup path use the same replacement
mechanism without waiting for the network: it first loads a backup-safe
per-player last-good cache, labels the
phase `Primary IR選曲テーブル`, makes Music Select usable, and then starts one
silent background refresh. A valid non-empty result replaces the table cache;
an empty, invalid, or failed result leaves the previous cache intact. Managed
Dan courses are refreshed with the completed response and remain available
from their separate last-good cache while offline. If the player has already
opened another folder when the automatic refresh completes, applying the new
root bars waits until Music Select returns to the root instead of interrupting
navigation; the saved cache is already ready for the next startup.
The MANIAC OPTIONS screen is an opaque black full-window mode rather than a window over Music
Select. Its enlarged list uses the available width and shows a brief
description of the selected option on wide screens. Song selection input is
suspended while it is open. Changes remain in
a draft until `Apply and return`, Escape, or a new short F2 press commits them,
saves once, reloads the effective score set, and returns to Music Select. EXTRA
MODE, ADD NOTES, ADD LONGNOTES, ADD MINES, LOUDNESS, GAMBOL, and the visual
effects follow the algorithms and inclusive-random boundaries recovered from
OpenLR2 Beta3 v100201. Ranked chart generation uses a BMS-IR-fixed MT19937
seed so the generated base chart is identical for every player. Normal
RANDOM, MIRROR, S-RANDOM, Random Trainer, and borrowed leaderboard placement
are applied afterward in the same way as an ordinary chart and do not split
the ranking. Replays retain the actual option seed and placement hash.
Background folder refreshes keep the last committed `songdata.db` snapshot
available to Music Select, so a refresh cannot temporarily replace the current
folder with an empty list.

The existing Music Select EXTRA NOTE skin property/button (`350`) and the
pre-launch EXTRA setting now read and write this same MANIAC EXTRA MODE at
levels 0--3. The former beatoraja Extra Note modifier is not applied, so these
controls no longer create an unrelated ASSIST-only chart. A skin-side change
is saved immediately and reloads the matching MANIAC score and lamp.
As in LR2, EXTRA MODE and ADD NOTES first collapse an existing long note to its
start note and remove the end marker before generating notes. Generated notes
therefore cannot overlap and render inside an old long-note body.

Any MANIAC option or Double Battle play is written only to
`bmsir_maniac.db`; ordinary plays remain in `score.db`. Arena and courses
temporarily disable these modes, except that SP TO DP by itself remains
available in Arena on supported SP charts. Combining it with any other MANIAC
effect restores the ordinary Arena block. Ranked EXTRA MODE, ADD NOTES, ADD LONGNOTES,
SP TO DP, and Double Battle use isolated BMS-IR leaderboards. Unsupported combinations,
ADD MINES, LOUDNESS, or a custom generation seed remain local-only instead of
falling back to the normal leaderboard.

SP TO DP LEVEL 1--3 independently converts only SP 5KEY and 7KEY charts to
DP 10KEY and 14KEY. It moves the existing note objects without changing their
timing, keysound, long-note pairing, or playable-note count. A monotonic
adjacent-key stair of at least three visible notes alternates DP sides when
every step is at most 333,334 / 111,112 / 83,334 microseconds for LEVEL 1 / 2 /
3. Simultaneous visible normal keys are assigned as one chord; every feasible
adjacent-key pair is split across opposite sides. Both structures choose the
lower-load parity and use odd source keys on 1P as the deterministic tie-break.
Outside those structures, normal-key assignment keeps the same keysound on one
side within a measure where possible and uses the immediately preceding
measure as a stability hint. Scratch-phrase
assignment ignores keysound identity so repeated scratch samples cannot pin
separated phrases to one side. The converter groups overlapping
normal-scratch guards plus connected long-scratch intervals into one scratch
phrase. Every scratch in a phrase stays on one side; separated phrases prefer
1P, 2P, 1P alternation. A connected scratch-only roll therefore stays on one
side instead of changing hands inside the roll. LEVEL 1--3 merge adjacent
scratches at inclusive gaps of 320, 240, and 160 ms respectively, implemented
as symmetric 160, 120, and 80 ms before/after guards. A phrase has no maximum
duration or scratch count while each adjacent gap remains connected.

A normal key is forbidden on the reserved side while its timing overlaps the
scratch phrase. A key long note uses its complete start-to-end interval, so an
LN that starts before the scratch also moves to the other side when its held
body overlaps the reservation. When one key LN spans multiple separated
scratch phrases, those phrases share one scratch side so the LN always retains
one unreserved side. These are hard placement constraints rather than costs;
the remaining keysound, measure-balance, and short side-movement costs apply
only after a safe side remains available.
The conversion is deterministic, rebuilds the final DP mode and scratch lanes,
and uses its own canonical identity and local score/lamp key. Its LEVEL 1--3
records use the same dedicated MANIAC submit, ranking, ghost, and owner-sync
paths as other ranked MANIAC transforms, and never submit to or fall back to
the ordinary chart ranking. Owner sync accepts a record only when its ranking
class, canonical options, algorithm version, virtual chart ID, deterministic
generation seed, SP-to-DP placement version, and placement hash are internally
consistent. Placement version 2 keeps the 0.4.14.34 stair/chord layout separate
from the earlier internal-test layout without changing unrelated MANIAC
identities. SP TO DP and
Double Battle are mutually exclusive.

Double Battle, its two-scratch AUTO SCRATCH setting, RANDOM LINK, and the
native-DP warning can all be configured in MANIAC OPTIONS. Existing Music
Select skins may also use the ordinary DP option's OFF, FLIP, BATTLE, and
BATTLE AS display: BATTLE and BATTLE AS now select the same MANIAC Double
Battle settings instead of the legacy L-ASSIST implementation. The skin and
F2 controls save the same sidecar values and reload the exact Double Battle
lamp immediately. Manual-scratch and auto-scratch Double Battle use different
local and online identities; existing manual Double Battle records keep their
original identity.

Music Select reads its score and lamp from the exact effective MANIAC settings.
Changing EXTRA MODE, an ADD option, a visual option, or Double Battle therefore
switches the whole visible list and folder summaries to that setting's isolated
record; it never falls back to the ordinary SP/DP lamp. Returning from the F2
screen or from a completed play clears both the score cache and the score
objects retained by visible bars before bulk-reading the selected mode, and a
completed BMS-IR MANIAC sync uses the same reload path on the render thread.
When Double Battle is enabled
on a native DP chart, only Double Battle is suspended: the normal DP lamp is shown if no other
MANIAC option remains, otherwise the remaining exact MANIAC settings select the
lamp.

`本体UI言語` selects Japanese or English for Arena windows and phase messages,
F2 MANIAC OPTIONS, built-in Ctrl+Shift+F5 windows, and their notifications.
The BMS-IR-specific pre-launch controls are translated by the launcher's normal
resource-bundle locale and save the same language setting used after startup.

`判定自動調整値を曲終了後に戻す` is OFF by default. When enabled, a play
that starts while automatic judge-timing adjustment is ON snapshots the
current timing and restores it on result, failure, abort, state exit, or game
shutdown. Changing automatic adjustment during the chart does not alter the
snapshot decision. Lua skins can use
`bmsir_judge_timing_restore_enabled()` and
`set_bmsir_judge_timing_restore_enabled(boolean)`. Legacy skin properties can
use option `2900` and button/event `390` (`bmsir_judge_timing_restore`). The
setter and event are effective only on Music Select and persist immediately.

`INFO通知を表示する` controls all transient ImGui INFO toasts as one group.
It does not hide warnings, errors, dialogs, or Arena phase warnings. Cover
controls accept a step from 1 through 1000. `カバー変更時にHI-SPEEDを再計算`
is independent and OFF by default, so START+6/7 changes the selected cover
without changing HI-SPEED unless recalculation is explicitly enabled. Music
Select cycles the five legacy HI-SPEED FIX values: `OFF / START / MAX / MAIN /
MIN`. A value `5` saved by the removed standalone IIDX FHS migrates to START
BPM during configuration validation.

`選曲ソートに判定難易度を追加する` remains ON by default to preserve the
existing judge-rank sorter. Turning it OFF restores the original eight-value
sort cycle. Judge-rank sort uses index 8 after the eight legacy sort images. A
skin without that image may not display the value correctly. Its compatibility
notice switch, `判定難易度ソート選択時に未対応スキン向け通知を表示する`, is ON
by default. The notice appears when the value is selected and once when Music
Select restores it. The global INFO-notification switch must also be ON.
Disabling the extension while it is active normalizes judge-rank sort to TITLE.

The song context menu contains one `BMS-IR Leaderboard` entry. It reads the
LR2-compatible ranking and selectable ghost directly from BMS-IR over HTTPS;
the Arena client no longer depends on the old `dream-pro.info` redirect for
this feature. Ranking reads request gzip, accept the BMS-IR Shift-JIS payload,
cap decompressed data, and keep a short per-chart cache.

When `BMS-IR段位をローカル同期する` is enabled (the default), a successful
table fetch from an exactly named `BMS-IR` Primary IR extracts only courses
with a grade/class constraint. They are written to
`player/<player-id>/bmsir_dan_courses.json` and added to the local `COURSE`
root for that player. Score Attack and other non-class courses are excluded.
An empty response, a communication failure, or invalid course data never
replaces the last good cache. The managed file and its backup are separate
from user-authored files under `course/`; turning the setting off hides the
managed courses without deleting either cache or personal courses. A cached
course can be browsed offline, but it still requires every chart to exist
locally before play can start. Its chart hashes and constraints remain the
same, so normal BMS-IR course-score submission uses the existing identity.
The graph uses the actual available plot height and keeps bars and the selected
outline inside the MAX guide even at the minimum window height. Each new
nomination round returns to the selector root before opening
`BMS-IR Arena 選曲候補`, so temporary folder labels do not accumulate across
auto-requeued matches. If the nomination status arrives before the game has
returned to music select, the client keeps the same match-scoped request
pending and opens the folder as soon as music select is ready.
The normal and compact overlays use one persistent phase banner. It emphasizes
the action required now, shows server-clock remaining seconds for fill,
nomination, option selection, chart loading, and synchronized start, and keeps
the selected chart's KEY count plus SINGLE/DOUBLE PLAY visible in the same
panel. A separate prominent banner can show MATCHING, MATCH FOUND, song and
option selection, loading/READY progress, and the authoritative synchronized
3/2/1/START transition. Its sounds are de-duplicated per match and countdown
second, do not replay elapsed seconds after reconnect or clock correction, and
fall back to existing sound-set files when Arena-specific files are absent.
The settings tab independently controls that banner, countdown/start sounds,
10/5-second warnings, and notification volume. These presentation states only
read the existing server deadlines and start release; they never release play
or alter options, input, score, or replay state. The older separate four-second
KEY popup is not used.
All phase countdowns use the normal color above ten seconds, yellow from ten
through six, and red from five through zero. The Manual tab renders only
bounded structured text received from the Arena service and caches the latest
valid version locally.
The Web Arena page remains available for the same queue controls, spectating,
and durable match history.

## Arena notification sound files

Arena notification sounds belong to the ordinary sound set, not to a PLAY
skin, the `ir/` plugin directory, or the directory containing the body JAR.
Place a supported audio file under the sound-set directory selected by the
body:

```text
sound/<sound-set-name>/arena-match-found.wav
sound/<sound-set-name>/arena-phase-warning.wav
sound/<sound-set-name>/arena-ready.wav
sound/<sound-set-name>/arena-countdown.wav
sound/<sound-set-name>/arena-start.wav
sound/<sound-set-name>/arena-cancelled.wav
```

The filename stem must match exactly. `.wav`, `.flac`, `.ogg`, and `.mp3` are
accepted, so a normal bundled layout may use, for example,
`sound/ModernChic/arena-phase-warning.ogg`. Restart the body after adding or
replacing files so the sound-set map is rebuilt. If more than one sound set is
installed, put the Arena files in every set that may be selected.

The queue-entry button itself has no notification sound. A newly reserved
match uses `arena-match-found`; song-selection and option-selection warnings
use `arena-phase-warning` once at 10 seconds and once at 5 seconds; every
accepted 3/2/1 step uses `arena-countdown`; all-player load completion uses
`arena-ready`; start release uses `arena-start`; and match cancellation uses
`arena-cancelled`. Reconnect snapshots and clock corrections do not replay
elapsed sounds.

When an Arena-specific file is absent, the sound set falls back as follows:

| Arena file | Existing sound-set fallback |
| --- | --- |
| `arena-match-found.*` | `decide.*` |
| `arena-phase-warning.*` | `o-change.*` |
| `arena-ready.*` | `playready.*` |
| `arena-countdown.*` | `o-change.*` |
| `arena-start.*` | `playready.*` |
| `arena-cancelled.*` | `playstop.*` |

If neither the Arena-specific file nor its fallback exists, that event is
silent. The overlay Settings tab controls the 10/5-second warning, 3/2/1
countdown, start sound, and Arena notification volume. Effective volume is the
ordinary system-sound volume multiplied by the Arena notification volume.

## Dedicated-client long-note policy

- The ordinary launcher selects `LONG NOTE`, `CHARGE NOTE`, or `HELL CHARGE
  NOTE` (`lntype=0/1/2`). Decoder input, explicit chart long-note types, local
  catalog metadata, score storage keys, ranking requests, and IR score payloads
  preserve that selection.
- A casual/private Arena entry snapshots the selected mode as a room rule and
  every participant loads that mode. Rated queue entry explicitly uses LN, so
  an ordinary CN/HCN setting cannot change rated behavior.
- Protocol v8 sends the locked mode and actual decoded total-note count at the
  ready barrier. The server starts only after all humans agree. Older protocol
  clients keep implied LN behavior and cannot join a CN/HCN room.
- The exact source chart remains identified by MD5. CN/HCN possession is not
  rejected merely because an LN catalog count differs; the agreed decoded
  count becomes the live/final validation scale before start.

## Match behavior

- Ordinary single-song play remains available while queued.
- The normal `対戦` tab enters the existing rated queue. The
  `公開ロビー／ルーム` tab creates or joins an unrated six-character-code
  room. A room may be public or code-only, named, unlocked, or
  password-protected. Room rules choose EX SCORE, BP, or MAX COMBO;
  free/NORMAL/HARD/EXHARD/HAZARD gauge; and official-table or free selection.
- Every overlay layout names the current mode, active LR2/oraja rule profile,
  and whether rating can change. Rated and room play remain textually distinct
  even when colors are hard to distinguish.
- Rated Arena always reserves four participants. One, two, three, or four
  humans are filled with three, two, one, or zero server CPUs; a fifth human
  waits for the next BO2, and a started BO2 never accepts a general waiter.
  The saved `1人待機中のCPU戦を許可` switch only controls whether one human may
  start with CPU3. Once at least two humans are reserved, the server fills to
  four with CPUs regardless of that switch.
- Server CPUs are displayed as `CPU`, are excluded from public waiting lists,
  and never count as waiting users. Their chart is selected randomly from all
  owned official normal/発狂 charts between the human selection ceiling and
  five bands below it, inclusive. Each CPU final EX SCORE is fixed once before
  play from EASY A--AA, NORMAL A--AAA, or HARD A--the chart's BMS-IR all-time
  best, but only bounded progressive current EX is shown during the chart.
  Rated BO2 settlement includes humans and CPUs in one four-player Elo result
  with K=32. CPU ratings are hidden, CPU rows are omitted from public result
  deltas/rankings/match counts, and a one-human+CPU-only BO2 does not increment
  the human match count.
- During Arena play, the optional main-skin target setting writes the selected
  opponent's latest received live EX SCORE directly into the normal target
  score and target name. The modes are OFF, first opponent, directly above, and
  match-local specified player; if the specified player leaves, it falls back
  to the first opponent. Score graphs can stay in live rank order or use fixed
  entry order, which pins both bar position and color by player ID.
- `対戦後もこの部屋に残る` returns a non-forfeiting player to the same room
  code and rules. Turning it off leaves after the current result.
- Every participant returns unready between room games. The host may update
  room/rule settings during this pause; an accepted update clears all READY
  state. The next nomination begins only after all participating members press
  `準備OK`.
- During active play, the remaining participants may unanimously end the
  chart. Connected voters are finalized from their last validated live values
  as FAILED. A player whose disconnect grace expired or who left is already
  DNF and no longer blocks the vote.
- Courses, practice, autoplay, and replay are not eligible matching states.
- If a match is reserved during an ordinary song, finish the song and its IR
  result first.
- Two READY rated players open the 30-second human-priority fill window. Four
  READY rated players start immediately; when the fill window expires, the
  service fills missing rated seats with CPUs. Private-room limits are
  unchanged.
- The normal rating windows remain ±100, then ±300, then unrestricted. The
  unrestricted-match option bypasses the wait early only when every
  out-of-range pair enabled it.
- After the fill window closes, a 60-second nomination phase opens. The normal
  selector stays navigable, and selecting a song nominates that exact chart
  instead of starting ordinary play. The overlay also provides an explicit
  server-random choice.
- Each participant contributes one candidate slot. A missing nomination at
  the deadline becomes a server-random official-table chart. Candidate charts
  remain hidden until every slot is filled; the server then reveals all
  candidates, selects one slot uniformly, and highlights the selected chart
  in the overlay.
- Every nominated or random candidate must be a positive-note-count
  GENOCIDE-normal or official発狂 chart between ☆1 and the effective
  participant ceiling. Rating 1000 has a ☆10 ceiling, 1050 reaches ★2,
  and each later complete 50 points adds four insane levels through 1300=★22;
  1350 and above are unrestricted through ☆13 / ★25. A player's active-season
  peak preserves unlocked levels after rating loss. The default-OFF
  `高レート基準の選曲を許可` setting keeps that player in the lowest-ceiling
  guard; enabling it removes only that player's lower ceiling from the guard.
  The client checks the selected MD5 before accepting it; the server validates
  the LN-scale processed-note count during play.
- In a free-selection managed room, the selector returns to its normal
  root and accepts any positive-note server-catalog chart. At least one player
  must nominate explicitly; timeout and missing-chart rerolls stay within the
  submitted room candidates instead of choosing an unrelated random chart.
- Arena play keeps the selected NORMAL, MIRROR, RANDOM, R-RANDOM, S-RANDOM,
  or SPIRAL lane option and uses LN. H-RANDOM, ALL-SCR, RANDOM-EX, and
  S-RANDOM-EX remain unavailable for Arena and are clamped to NORMAL.
  Normal RANDOM receives one shared match seed. The mirror checkbox locally
  reverses that seven-key order; R-RANDOM and SPIRAL are not synchronized.
  Gauge and ordinary visual/timing preferences remain available in ranked
  play. A managed-room forced gauge temporarily replaces the gauge and
  disables Gauge Auto Shift for that Arena chart; both values are restored
  afterward.
- Assist chart modifiers, trainer features, BPM guide, custom widened judge,
  CONSTANT, battle, and mode conversion are disabled for that Arena play and
  restored afterward.
- The cursor checkbox chooses whether Arena play keeps the OS pointer available
  or uses the ordinary inactivity-based catch behavior.
- Start+Select and Escape cannot abort a server-selected Arena chart. This
  applies only while the Arena play is active; Arena OFF and ordinary play keep
  their normal input behavior.
- The client sends current EX, BP, MAX COMBO, and processed-note count at most
  once per second,
  together with the selected OP, followed by one immediate final packet with
  the result ClearType. The processed-note count uses the larger of the play
  counter and judged-note total so skins or rules that keep the in-play pass
  counter stale still update spectators. A normal completion uses the
  server-selected chart total; hard fail keeps the actual processed count.
- SP sends only the active 1P lane option even if stale 2P/FLIP settings exist.
  DP sends both sides and FLIP together with the chart play mode and displays
  them on one line, such as `RAN / -` or `RAN / MIR / FLIP`.
- The chat tab talks only to participants in the current match. Messages are
  limited to 200 normalized characters and one accepted message per second;
  the latest 50 return after reconnect and disappear when the match ends.
  Gameplay displays only recent chat read-only so input cannot capture keys.
- After Arena play, the latest Arena result remains visible during the
  between-match wait unless its explicit close button is pressed. Each new
  result opens normally. A rated result shows the player's previous rating,
  new rating, and delta prominently. Fixed Arena options are restored only
  after the ordinary IR submission has captured the Arena score.
- An incomplete BO2 round result automatically enters the ordinary result
  fade at the server-provided 15-second deadline. This inter-round guard also
  releases a stalled ordinary-IR wait so round 2 can begin after music select
  is reported. The final BO2 result and ordinary non-Arena results remain
  user-controlled.
- A normal result or hard fail automatically returns the account to the Arena
  queue. Client shutdown or an unexpected Arena play exit requests a
  zero-score forfeit and stops automatic entry. Entering the normal result
  screen is not an exit and leaves time for the final packet to be accepted.

## Ordinary-play and skin additions

- The native game window title stays exactly `Arena oraja` across client
  versions so OBS window-capture rules remain stable. Configuration, What's
  New, startup, and other version-identification surfaces keep the versioned
  `Arena oraja <version>` display name.
- Legacy skin string property `1010` retains the upstream-compatible
  `LR2oraja Endless Dream` version identity. This prevents LITONE and similar
  skins from mistaking the product name for beatoraja's built-in Arena skin API;
  the BMS-IR Arena graph remains in its external overlay.
- During READY and PLAY, play skins can read the resolved fixed lane placement
  through numeric references `450`--`466` and `469`, using the same one-based
  values as the result skin. The values come directly from the already-applied
  replay placement for RANDOM, ROTATE, CROSS, and RANDOM_EX; reading them
  never regenerates a seed or reapplies a modifier. NORMAL, MIRROR, S-RANDOM,
  H-RANDOM, unavailable sides, and old replays without a fixed placement
  return zero.
- Skin Select can show a live, scaled preview of the selected skin. The bundled
  Lua Skin Select declares `skin.skinpreview = { id = "skin-preview" }` and a
  destination with the same ID. JSON and Lua skins use the same explicit
  `skinpreview` declaration; LR2 Skin Select skins can use reference image 105.
  For existing JSON/Lua Skin Select skins without that declaration, the client
  finds the large change-skin click target (event 190, at least 160 x 90) and
  prefers a later, similarly shaped visual contained inside it. That visual is
  replaced at its original draw position, preventing a legacy thumbnail or
  gray placeholder from covering the live preview. If there is no safe
  contained visual, the click target remains the compatibility placement;
  small arrow/button-only layouts still need an explicit destination. The
  off-screen surface clears to the same opaque black as the real game screen,
  so an underlying placeholder cannot show through before READY.
  Changing a custom option, file, or offset reloads the preview. Skin Select
  itself is excluded to avoid recursive previews. Music Select skins render
  against a deterministic in-memory catalog containing two virtual folders and
  enough scored songs to fill a normal bar list without reading the user's song
  DB. DECIDE skins receive the same virtual selected chart through a
  `MusicDecide`-compatible state. Play skins render against a mode-matched,
  silent synthetic session presented to the skin as ordinary play. Its normal
  notes, chords, and charge notes advance
  through PRELOAD, READY, PLAY, music-end, and fadeout before the preview loops;
  score, combo, gauge, judge, and end timers advance with it. A deterministic
  silent BGA keeps BGA-gated frames and song-BGA declarations available when
  BGA is enabled, and gauge-increase/gauge-max timers follow the sample notes
  for both play sides. This allows skins such as WMII to build the same score
  graph and surrounding play parts they use in normal play. The chart's time
  and measure positions stay aligned so its first notes enter from above the
  visible lane. Double-play previews place sample notes on both sides and
  advance the independent 1P/2P judgement, combo, key-beam, end-of-note, and
  full-combo timers used by 14-key skins. Each loop resets the lane scan and
  input state, tap key beams release after a bounded hold, and charge-note
  pairs drive the held-LN body and HOLD timers. All data-backed previews also
  rewind untimed one-shot destinations, cached custom timers/events, and movie
  sources at the loop boundary, so DECIDE, PLAY, RESULT, and COURSE RESULT
  intro/fade animations replay after the first iteration. RESULT and COURSE
  RESULT skins receive result-compatible
  states with a representative
  current/best/rival score, gauge history, timing distribution, replay status,
  and a four-chart virtual course. Every data-backed preview has its own timer
  and player resource, so it does not replace the active selector resource,
  play audio, access a song database, save a score, or contact IR. A failed
  preview object is isolated from the configuration screen, play-skin offset
  mutations are restored after each frame, and the off-screen buffer is limited
  to the displayed destination size and a 2048-pixel maximum dimension.

The startup launcher has a `BMS-IR固有設定` tab. One-bass input and the
first-timing preview default to ON and may be changed there. Long-note behavior
uses the ordinary enabled LN-type selector; BMS-IR accepts new CN/HCN results
in mode-separated rankings, while rated Arena continues to lock LN.

- The Input tab option `非アクティブ時も専用コントローラー入力を受け付ける` is OFF by
  default. When enabled, GLFW game controllers continue to drive Music Select,
  READY, PLAY, and RESULT while another application has focus. PC keyboard,
  mouse, and mouse-scratch input remain tied to the Arena oraja window. A
  controller that emulates keyboard keys is therefore outside this feature;
  use its HID/joystick mode instead.

- Music Select START and SELECT independently assign their short press to
  `なし` / `難易度変更` / `鍵盤数変更`. With `なし`, the corresponding option
  panel opens on the first pressed frame. Difficulty and key-mode actions use a
  release before 350 ms for the short action; holding START opens play options
  and holding SELECT opens assist options. START plus a playable key remains
  immediate, and same-frame START+SELECT keeps the detailed-option panel.
  Difficulty mode
  can keep every chart as a separate row and move the cursor only within the
  selected same-song set, or combine currently visible charts with the same
  folder identity and key mode into one LR2-style row. The grouped display
  applies one shared BEGINNER--INSANE stage across all grouped songs in the
  current table/search scope, using the nearest lower available difficulty or
  the lowest chart when the exact stage is missing. Unchecked concrete key
  modes are hidden throughout Music Select and are omitted from every mode
  cycle. ALL adds the combined view to the cycle; a legacy ALL-only setting
  retains all concrete modes. An empty allow-list is normalized to 7K.
- `難易度表の難易度をLEVEL表示に使う` is a per-player, default-ON switch.
  When enabled, each song bar and the selected-song LEVEL display inside a
  difficulty table prefer the first contiguous decimal integer in that table
  entry's level.
  `発狂6`, `★06`, and `01.1` therefore display as `6`, `6`, and `1`.
  The same per-entry value follows a chart into a server-declared aggregate
  folder such as `全曲`, drives LEVEL sorting, survives LR2-style grouped
  difficulty cycling, and remains visible for unavailable table charts. A
  label without a representable integer falls back to the chart's local
  `#PLAYLEVEL`. Disabling the switch restores local `#PLAYLEVEL` display and
  LEVEL sorting. Ordinary folders, searches, favorites, Primary IR selection
  tables, chart identity, score storage, and IR payloads remain unchanged.
- `全難易度表で未所持曲を隠す` is a single per-player, default-OFF switch for
  every difficulty table. When enabled it hides unavailable song bars inside
  table folders even while HTTP downloads are enabled. Ordinary folders,
  searches, and Arena candidate lists keep their existing visibility behavior.
- `選曲ルートに表示する物理フォルダを絞り込む` is a per-player,
  default-OFF parent switch in Resource settings beside BMS Path. OFF preserves
  every physical root. ON reveals one checkbox for each configured BMS Path and
  shows only checked roots; zero checks hides all physical roots. It filters
  only the Music Select root, so descendants of an allowed root and all tables,
  courses, favorites, commands, searches, Primary IR roots, and Arena candidate
  folders keep their existing behavior.
- During ordinary PLAY with standard RANDOM, hold START and exactly one
  playable key through the DECIDE-to-READY transition to place the first source
  key on that destination. Once READY is visible the input has been captured
  and may be released. With the DECIDE screen disabled, the keys must already
  be held when the song is confirmed. DP reads each side independently. It is
  disabled for replay, FLIP, nonstandard randoms, a reserved match, and Arena
  play. Merely connecting to or queuing for Arena does not disable it during an
  ordinary play. New plays choose an ordinary 24-bit RANDOM seed that already places
  the first source key at the requested destination, so replay-chart and IR
  rival-chart borrowing reproduce the final placement from the seed alone.
  When one-bass is applied while borrowing a standard RANDOM, the borrowed
  placement is used as the base: only the first source key and requested
  destination are swapped. The complete result is then re-encoded as another
  ordinary 24-bit RANDOM seed, so replay and later IR borrowing need no extra
  one-bass metadata. A borrowed seed that already satisfies the request is
  retained unchanged. Seedless RIVALOPTION/REPLAYOPTION borrowing chooses a
  new replayable seed instead of treating the missing seed as an invalid
  borrowed placement. LR2IR G-BATTLE resolves its borrowed lane order to a
  standard seed before the same two-lane swap. DP encodes each side
  independently and keeps a missing packed seed missing on both sides.
  Replay files retain the destination to reproduce the pre-0.4.3 swap-based
  format as well.
- `bmsir-helper/random_pattern_dp.html` is extracted next to the atomic
  `current.json` snapshot after a chart placement is resolved. Add that local
  HTML file as an OBS browser source. The last SP/DP placement remains visible
  through select, play, and result scenes.
- The loading play screen and READY show only the notes at the chart's first
  playable timing, beginning with the first drawable frame after the resolved
  model and play-skin note images exist. Audio and BGA preload completion is
  not required. Simultaneous
  notes are shown together, and an LN start uses the normal-note image as the
  marker. The resolved post-modifier lanes and the active play skin's normal
  note image, lane width, thickness, animation frame, and note offsets are
  used. The unmodified note top is anchored to the top of each lane, or
  immediately below the lane cover when SUD+ is enabled. The chart is scanned
  once when loaded, so a silent opening longer than two measures is supported.
  Ending the loading preview rewinds only that scan position before READY; it
  does not restore saved HI-SPEED, green number, lane cover, LIFT, or HIDDEN
  values over adjustments made on the loading screen. PRELOAD and READY share
  one live lane-setting state until the chart actually starts. HI-SPEED, green
  number, SUD+, LIFT, and HIDDEN changes are applied immediately, and the final
  values at PLAY entry become the next chart's initial per-mode settings.
  With HI-SPEED FIX enabled, a manual HI-SPEED change updates its persistent
  fixed duration. Ordinary scratch, mouse-wheel, and cursor cover movement
  preserves that manual HI-SPEED while ordinary auto recalculation is OFF;
  when it is ON, the cover change recalculates at the current BPM. START+6/7
  keeps its stricter existing rule and recalculates only when both the ordinary
  and BMS-IR-specific cover-recalculation switches are ON.
  PlayConfig keeps `startHerePreviewEnabled`. The former measure-count and
  per-side note-cap fields remain readable for 0.4.0-dev configuration
  compatibility but no longer alter this first-timing marker.
- Lua play skins can call `main_state.play_hispeed_margin()` and
  `main_state.set_play_hispeed_margin(value)`;
  `main_state.start_here_preview_enabled()` /
  `set_start_here_preview_enabled(boolean)`. The legacy
  `main_state.start_here_preview_measures()` /
  `set_start_here_preview_measures(value)` pair remains callable for existing
  skins but no longer changes the marker; and
  `main_state.play_key_fast(side)`, `play_key_slow(side)`,
  `play_scratch_fast(side)`, or `play_scratch_slow(side)`. Side is `1` or
  `2`; direction flags use the most recent 500 ms. An optional second
  duration argument is clamped to 50--2000 ms.

## Build

Use a JDK 17 distribution that includes JavaFX:

```bash
./gradlew clean build --no-daemon
```

The server release gate checks both Arena protocol release and build identity.
That gate controls supported distribution; server-side score/state validation
remains necessary because an open-source client identity can be imitated.

The desktop launcher lives in its own repository,
[`tenP0312-dev/oraja-Rancher`](https://github.com/tenP0312-dev/oraja-Rancher),
a Tauri 2 launcher for Windows x64 and macOS arm64. It preserves unknown INI
fields and layout, accepts Java
21 or newer, detects the bundled Windows `runtime/bin/java.exe`, blocks
ambiguous duplicate BMS-IR plugin jars, and transactionally replaces one older
versioned plugin with its verified successor. It verifies canonical Ed25519
release manifests and every artifact hash before replacement, and restores the
prior plugin and files after a failed transaction. Its signed Markdown release
notes and announcement list support Japanese and English and are rendered
without executing release HTML. The upper-right `🌐 日本語` / `🌐 English`
control switches the launcher language, and the announcement list remains
visible when the installed body is current. The header shows the installed
body and launcher versions separately. Installation and update operations show
overall transferred bytes, percent, verified file count, and the following
verification, application, and launcher-restart phases. Existing installations
hash the signed delta paths and download only changed or missing files. A
verified staged launcher can restart as its own short-lived update helper and
relaunch after replacement. The verified helper is copied outside staging, and
successful updates remove both staging and rollback data before launch. An EXE placed in an empty portable directory
immediately checks its selected channel and downloads one signed compressed
bootstrap ZIP, verifies its full file inventory, and then applies the current
sparse delta. Missing files do not become `current` merely because
the launcher's compiled body version matches the channel version. The UI keeps
launch actions disabled until its initial update check completes. A signed
mandatory update, revoked body, or minimum-launcher requirement is also
enforced in Rust and cached locally, so a later network failure cannot
re-enable an old version. The Arena service compatibility gate remains the
final enforcement point for clients that never received that signed policy.
Pull-request CI produces explicitly unsigned validation artifacts; manually
dispatched CI builds only configured internal-test launchers and downloads the
SHA-256-pinned official Tauri CLI binary instead of compiling it or rebuilding
both configured and unconfigured launchers. Official launcher publication remains blocked
until Authenticode and Developer ID/notarization credentials plus the reviewed
manifest public key are available.

Build the distributable fat jar with an explicit target platform and
architecture. For example, the macOS Apple Silicon canary is built with:

```bash
./gradlew clean shadowJar --no-daemon -Dplatform=macos -Darch=aarch64
```

The artifact name identifies the unified BMS-IR Arena oraja client:

```text
BMS-IR-Arena-oraja-0.4.14.58-macos-aarch64.jar
```

The public page offers two forms for each supported OS:

- non-bundled: the platform JAR plus `bms_ir_arena_oraja_0.0.72.jar`;
- Java-bundled: a ready-to-extract ZIP containing the same two reviewed JARs,
  a Java 21 runtime, distribution-cleared base assets, and launch scripts. The
  Windows package contains the portable launcher EXE and the macOS package
  contains the portable app bundle; BAT and command startup remain available.

Build the Java-bundled ZIP only from a clean asset source whose redistribution
terms have been checked. The packager copies only the fixed visual/audio asset
directories and deliberately excludes player profiles, credentials, score and
song databases, tables, courses, logs, layouts, downloads, and backups. It
also verifies Java 21+, the target OS/architecture, the Java legal directory,
and the exact release filenames:

```bash
python tools/package_arena_release.py \
  --platform macos-aarch64 \
  --body-jar dist/BMS-IR-Arena-oraja-0.4.14.58-macos-aarch64.jar \
  --plugin-jar /reviewed/bms_ir_arena_oraja_0.0.72.jar \
  --base-assets /reviewed/clean-beatoraja-assets \
  --java-home /reviewed/java-21-home \
  --launcher-app "/reviewed/BMS-IR Arena.app" \
  --output-dir dist \
  --confirm-base-assets-redistributable
```

Use `windows-x86-64` with a matching Windows x64 Java 21 runtime for the
Windows archive and pass the reviewed portable launcher plus the bundle created
by `native-audio/build-native-audio.ps1 -VerifyReproducible`. The packager
rechecks the source identities, GPLv3 license route, file inventory, PE x64
identity, required ASIO/WASAPI/JNI exports, and SPDX declarations. Add
`--test-build` only for an internal test package:

```bash
python tools/package_arena_release.py \
  --platform windows-x86-64 \
  --body-jar dist/BMS-IR-Arena-oraja-0.4.14.58-windows-x86-64.jar \
  --plugin-jar /reviewed/bms_ir_arena_oraja_0.0.72.jar \
  --base-assets /reviewed/clean-beatoraja-assets \
  --java-home /reviewed/windows-java-21-home \
  --launcher-exe /reviewed/BMS-IR-Arena-launcher.exe \
  --native-audio-bundle /reviewed/native-audio-bundle \
  --output-dir dist \
  --test-build \
  --confirm-base-assets-redistributable
```

Test packages name the launcher `BMS-IR Arena Test.exe` or
`BMS-IR Arena Test.app` and select the test update channel. The ZIP contains
`release-manifest.json` with body, plugin, launcher, native-audio manifest, and
complete package-file SHA-256 values plus the initial local version marker.
The Windows ZIP also carries the original pinned PortAudio and ASIO SDK source
archives, build scripts, component license files, SOURCE_INFO, and SPDX SBOM.
Generated JARs, DLLs, and ZIPs remain release artifacts and must not be
committed to the source repository.

The release build uses JavaCPP and JavaCV 1.5.11 with the matching FFmpeg
7.1-1.5.11 preset. `shadowJar` fails when the target JavaCPP runtime, the
complete FFmpeg decoder runtime, or the expected dependency versions are
missing, or when native libraries for another OS are mixed into the artifact.
The Windows x86-64 artifact must therefore contain `jnijavacpp.dll`, its
bundled Visual C++ runtime DLLs, and the FFmpeg JNI/runtime DLLs before it can
be distributed.

Movie decoding copies JavaCV frames into an independent RGB888 Pixmap with
stride/channel handling and decoder/render locking. It recreates resources on
size changes, reopens the grabber after a failed loop seek, and logs open,
metadata, first-frame, first-Texture, and failure details through the normal
application logger. FFmpeg native loading completes synchronously before the
first decoder thread starts. Playback commands wake an unbounded blocking
queue without interrupting JavaCPP file extraction, including commands queued
during decoder initialization. Release acceptance must test both skin movies
and in-play BGA on the target Windows/Java 21 bundle.

Do not publish an artifact from an uncommitted worktree. Build the reviewed
commit and record artifact identities privately. For every BMS-IR-built body
or plugin that will become downloadable through the launcher, including the
internal test channel, follow the server repository's
`docs/PRODUCTION_VPS_OPERATIONS.md`: activate the exact ordinary-score
body/plugin allowlists and Arena client-version/build gates, perform required
guarded reloads, and verify both paths before promoting the signed channel.
