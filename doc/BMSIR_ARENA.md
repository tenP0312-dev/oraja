# BMS-IR Arena client

Status: BMS-IR Arena v1 release branch. This source prepares the unified
`BMS-IR Arena oraja 0.4.12`. It replaces the separate Endless Dream and
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

Rated Arena remains one chart. Managed rooms can select one chart,
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
treated as the same logical modifier. The unmodified F5 menu and its
`Show BMS-IR Arena Overlay` action remain a fixed recovery path.
BMS-IR-specific settings are stored per player in the allow-listed
`bmsir_arena.json` sidecar. The first 0.4.1-dev or later start migrates existing
Arena values from `config_player.json`; 0.4.5-dev adds the one-bass and
first-timing preview switches, 0.4.6 adds the Dan local-sync switch, and 0.4.11
adds Arena target and graph-order switches.
Later saves by a non-BMS-IR body cannot erase them. The sidecar uses the same
backup-safe write mechanism as player config and never contains IR user IDs,
passwords, or unrelated player settings.

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
  DP sends both sides and FLIP together with the chart play mode.
- The chat tab talks only to participants in the current match. Messages are
  limited to 200 normalized characters and one accepted message per second;
  the latest 50 return after reconnect and disappear when the match ends.
  Gameplay displays only recent chat read-only so input cannot capture keys.
- After Arena play, the latest Arena result remains visible during the
  between-match wait unless its explicit close button is pressed. Each new
  result opens normally. A rated result shows the player's previous rating,
  new rating, and delta prominently. Fixed Arena options are restored only
  after the ordinary IR submission has captured the Arena score.
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

The source tree also contains `arena-launcher/`, a Tauri 2 launcher for Windows
x64 and macOS arm64. It preserves unknown INI fields and layout, accepts only
Java 17, blocks duplicate BMS-IR plugin jars, verifies canonical Ed25519 release
manifests and every artifact hash before replacement, and rolls back a failed
transaction. Its signed Markdown release notes are rendered without executing
release HTML. A verified staged launcher can restart as its own short-lived
update helper and relaunch after replacement. CI outputs are explicitly
unsigned validation artifacts; official launcher publication remains blocked
until Authenticode and Developer ID/notarization credentials plus the reviewed
manifest public key are available.

Build the distributable fat jar with an explicit target platform and
architecture. For example, the macOS Apple Silicon canary is built with:

```bash
./gradlew clean shadowJar --no-daemon -Dplatform=macos -Darch=aarch64
```

The artifact name identifies the unified BMS-IR Arena oraja client:

```text
BMS-IR-Arena-oraja-0.4.12-macos-aarch64.jar
```

The public page offers two forms for each supported OS:

- non-bundled: the platform JAR plus `bms_ir_arena_oraja_0.0.68.jar`;
- Java-bundled: a ready-to-extract ZIP containing the same two reviewed JARs,
  a Java 21 runtime, distribution-cleared base assets, and launch scripts.

Build the Java-bundled ZIP only from a clean asset source whose redistribution
terms have been checked. The packager copies only the fixed visual/audio asset
directories and deliberately excludes player profiles, credentials, score and
song databases, tables, courses, logs, layouts, downloads, and backups. It
also verifies Java 21+, the target OS/architecture, the Java legal directory,
and the exact release filenames:

```bash
python tools/package_arena_release.py \
  --platform macos-aarch64 \
  --body-jar dist/BMS-IR-Arena-oraja-0.4.12-macos-aarch64.jar \
  --plugin-jar /reviewed/bms_ir_arena_oraja_0.0.68.jar \
  --base-assets /reviewed/clean-beatoraja-assets \
  --java-home /reviewed/java-21-home \
  --output-dir dist \
  --confirm-base-assets-redistributable
```

Use `windows-x86-64` with a matching Windows x64 Java 21 runtime for the
Windows archive. The ZIP contains `release-manifest.json` with the body and
plugin SHA-256 values. Generated JARs and ZIPs remain release artifacts and
must not be committed to the source repository.

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
and in-play BGA on the target Windows/Java 17 body.

Do not publish an artifact from an uncommitted worktree. Build the reviewed
commit, record the artifact hashes privately for rollout, and configure the
server allowlists before distribution.
