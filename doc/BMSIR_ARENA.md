# BMS-IR Arena client

Status: BMS-IR Arena v1 public beta. The unified `BMS-IR Arena oraja
0.4.2-dev` client replaces the separate Endless Dream and beatoraja Arena
bodies and lets one installation select LR2 or oraja judgement/gauge behavior.

This release adds the default-OFF
`高レート基準の選曲を許可` setting. Rated selection keeps every level reached
by the player's active-season peak rating. Players who enable the setting no
longer lower the room ceiling; when at least one player leaves it disabled,
the lowest disabled player's peak remains the guard. It also includes named
public/code-only rooms, explicit between-game READY, custom-table rooms,
server-managed CPU play, and the combined GENOCIDE normal ☆1--☆13 /
official発狂 ★1--★25 rated selection.

Version `0.4.1-dev` also adds ordinary-play LR2 one-bass RANDOM input,
READY-time start-chart previews, Lua play-skin accessors for the live
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
higher-basis chart selection, and a mirrored synchronized-RANDOM layout.
The overlay shortcut accepts any keyboard key, either alone or as an exact
multi-key chord, and defaults to Ctrl+Shift+F5. Hold the desired keys and
release all of them to register the chord. Escape cancels capture, while
Backspace or Delete alone clears it. Left/right Ctrl, Shift, and Alt are
treated as the same logical modifier. The unmodified F5 menu and its
`Show BMS-IR Arena Overlay` action remain a fixed recovery path.
Arena settings are stored per player in the allow-listed
`bmsir_arena.json` sidecar. The first 0.4.1-dev or later start migrates existing Arena
values from `config_player.json`; later saves by a non-Arena body cannot erase
them. The sidecar uses the same backup-safe write mechanism as player config
and never contains IR user IDs, passwords, or unrelated player settings.
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
- When only one real player is waiting, the fallback opponent is displayed as
  `CPU` if that unified client supports the server-managed flow and enabled
  `BOT戦を許可`. The match is created immediately only while that player is
  the sole active real Arena client. The CPU selects from the highest official
  normal/発狂 band the player owns at or below the player's current rated
  ceiling. Its final EX SCORE is selected once from inclusive AA through MAX
  before play, but only a deterministic monotonic current score is shown as
  the human progresses; the selected final value is revealed at completion. A human
  win/loss/tie changes only that human by `+1`/`-1`/`0`; the CPU has no rating
  or match count, and the CPU series does not increment the human's match
  count. A current CPU match finishes normally if another human
  appears, but no new CPU match starts while both humans remain active.
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
- Two READY players open a 30-second fill window. Four READY players reduce
  the remaining wait to at most ten seconds, and eight READY players start
  immediately.
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

- During ordinary PLAY with standard RANDOM, hold START and exactly one
  playable key while the chart is decided to place the first source key on
  that destination. DP reads each side independently. It is disabled for
  replay, FLIP, nonstandard randoms, and both legacy/new Arena states; replay
  files store the resolved destination so playback is stable.
- `bmsir-helper/random_pattern_dp.html` is extracted next to the atomic
  `current.json` snapshot after a chart placement is resolved. Add that local
  HTML file as an OBS browser source. The last SP/DP placement remains visible
  through select, play, and result scenes.
- READY shows a cached static preview of the first two measures by default.
  PlayConfig keeps `startHerePreviewEnabled`,
  `startHerePreviewMeasures` (1--8), and the bounded per-side note cap.
- Lua play skins can call `main_state.play_hispeed_margin()` and
  `main_state.set_play_hispeed_margin(value)`;
  `main_state.start_here_preview_enabled()` /
  `set_start_here_preview_enabled(boolean)` and
  `main_state.start_here_preview_measures()` /
  `set_start_here_preview_measures(value)`; and
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
BMS-IR-Arena-oraja-0.4.2-dev-macos-aarch64.jar
```

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
