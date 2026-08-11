# BMS-IR Arena client

Status: BMS-IR Arena v1 release branch. This source prepares the unified
`Arena oraja 0.4.14.34`. It replaces the separate Endless Dream and
beatoraja Arena bodies and lets one installation select LR2 or oraja
judgement/gauge behavior.

This release adds the default-OFF
`高レート基準の選曲を許可` setting. Rated selection keeps every level reached
by the player's active-season peak rating. Players who enable the setting no
longer lower the room ceiling; when at least one player leaves it disabled,
the lowest disabled player's peak remains the guard. It also includes named
public/code-only rooms, explicit between-game READY, custom-table rooms,
server-managed CPU play, and the combined GENOCIDE normal ☆1--☆13 /
official発狂 ★1--★25 rated selection.

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

## Enabling

1. Configure the normal BMS-IR IR entry with the BMS-IR player ID and game
   password.
2. In the IR configuration screen, enable `BMS-IR Arena`.
3. Keep the default WSS server unless a developer is running a controlled
   local service.
4. Start the client, then use the `BMS-IR Arena` overlay to enter matchmaking.

The launcher setting `判定・ゲージ` selects the rule profile used for ordinary
play and for creating managed rooms:

- `LR2`: LR2 judge windows, gauge behavior, and default TOTAL.
- `oraja`: the original beatoraja rule set for each key mode.

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
optional IIDX FHS and judge-rank sort cycles plus their two independent skin
notices upgrade it to schema 15.
Later saves by a non-BMS-IR body cannot erase them. The sidecar uses the same
backup-safe write mechanism as player config and never contains IR user IDs,
passwords, or unrelated player settings.

Holding F2 for about one second on Music Select opens the one-column MANIAC
OPTIONS screen; a short F2 press keeps the existing refresh command. The
screen is an opaque black full-window mode rather than a window over Music
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
is independent and OFF by default, so START+6/7 does not activate FHS-style
scratch recalculation unless explicitly enabled.

`選曲OPにIIDX FHSを追加する` is OFF by default. When enabled, Music Select
cycles `OFF / START / MAX / MAIN / MIN / IIDX FHS`; the ordinary pre-launch
HI-SPEED FIX combo still offers only the five legacy choices. In IIDX FHS,
START plus a HI-SPEED key changes the current multiplier by exactly 0.50.
Changing SUD+ reloads the saved green number at the current BPM and includes
LIFT in the cover calculation. Without LIFT, an SUD+ off/on cycle reloads when
SUD+ is enabled. With LIFT, later cycles reload when SUD+ is disabled; the
first in-play SUD+ activation also reloads and starts at white number 125. That
first-activation state is retained between charts in one course and reset on
returning to Music Select.

`選曲ソートに判定難易度を追加する` remains ON by default to preserve the
existing judge-rank sorter. Turning it OFF restores the original eight-value
sort cycle. IIDX FHS uses skin index 5 after the five legacy HS-FIX images;
judge-rank sort uses index 8 after the eight legacy sort images. A skin without
those images safely falls back to `OFF` or `TITLE`. Their compatibility notices
have separate switches, `IIDX FHS選択時に未対応スキン向け通知を表示する` and
`判定難易度ソート選択時に未対応スキン向け通知を表示する`, both ON by
default. Each notice appears when its value is selected and once when Music
Select restores that value. The global INFO-notification switch must also be
ON. Disabling an extension while it is active normalizes IIDX FHS to START BPM
or judge-rank sort to TITLE.

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

- Every long note is interpreted as legacy LN throughout this BMS-IR build,
  including ordinary play outside Arena.
- The decoder input, explicit chart `#LNMODE`, explicit CN/HCN note types,
  replay/pattern output, local catalog metadata, score storage keys, ranking
  requests, and IR score payloads are normalized to LN.
- The launcher LN-type control is fixed to `LONG NOTE`.
- Existing `songdata.db` files do not need to be rebuilt for Arena chart
  ownership checks. The exact source chart is identified by MD5 and the model
  is normalized when loaded for play.

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

The startup launcher has a `BMS-IR固有設定` tab. One-bass input and the
first-timing preview default to ON and may be changed there. `全ロングノートを
LONG NOTEとして扱う` is shown as an always-ON compatibility rule rather than
an editable switch: BMS-IR rejects CN/HCN results, so disabling the rule would
make chart note counts and submitted scores disagree.

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
BMS-IR-Arena-oraja-0.4.14.34-macos-aarch64.jar
```

The public page offers two forms for each supported OS:

- non-bundled: the platform JAR plus `bms_ir_arena_oraja_0.0.69.jar`;
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
  --body-jar dist/BMS-IR-Arena-oraja-0.4.14.34-macos-aarch64.jar \
  --plugin-jar /reviewed/bms_ir_arena_oraja_0.0.69.jar \
  --base-assets /reviewed/clean-beatoraja-assets \
  --java-home /reviewed/java-21-home \
  --launcher-app "/reviewed/BMS-IR Arena.app" \
  --output-dir dist \
  --confirm-base-assets-redistributable
```

Use `windows-x86-64` with a matching Windows x64 Java 21 runtime for the
Windows archive and pass the reviewed portable launcher. Add `--test-build`
only for an internal test package:

```bash
python tools/package_arena_release.py \
  --platform windows-x86-64 \
  --body-jar dist/BMS-IR-Arena-oraja-0.4.14.34-windows-x86-64.jar \
  --plugin-jar /reviewed/bms_ir_arena_oraja_0.0.69.jar \
  --base-assets /reviewed/clean-beatoraja-assets \
  --java-home /reviewed/windows-java-21-home \
  --launcher-exe /reviewed/BMS-IR-Arena-launcher.exe \
  --output-dir dist \
  --test-build \
  --confirm-base-assets-redistributable
```

Test packages name the launcher `BMS-IR Arena Test.exe` or
`BMS-IR Arena Test.app` and select the test update channel. The ZIP contains
`release-manifest.json` with body, plugin, and launcher SHA-256 values plus the
initial local version marker. Generated JARs and ZIPs remain release artifacts
and must not be committed to the source repository.

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
commit, record the artifact hashes privately for rollout, and configure the
server allowlists before distribution.
