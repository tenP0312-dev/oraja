# BMS-IR Arena client

Status: BMS-IR Arena v1 public beta. The unified `BMS-IR Arena oraja
0.3.2-dev` candidate replaces the separate Endless Dream and beatoraja Arena
bodies and lets one installation select LR2 or oraja judgement/gauge behavior.

This release adds named public/code-only rooms, optional room passwords,
explicit between-game READY, public-lobby and spectator chat, unanimous
in-play finish voting, match-relative BP graphs, and the restored combined
GENOCIDE normal ☆1--☆12 / official発狂 ★1--★25 rated ceiling.

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
play-time mouse cursor, and enables optional mutual unrestricted matching and
a mirrored synchronized-RANDOM layout. Ctrl+Shift+F5 toggles visibility. If
that shortcut opens the Endless Dream menu instead, use
`Show BMS-IR Arena Overlay` in the F5 menu to restore the overlay.
The graph uses the actual available plot height and keeps bars and the selected
outline inside the MAX guide even at the minimum window height. Each new
nomination round returns to the selector root before opening
`BMS-IR Arena 選曲候補`, so temporary folder labels do not accumulate across
auto-requeued matches.
The normal and compact overlays use one persistent phase banner. It emphasizes
the action required now, shows server-clock remaining seconds for fill,
nomination, option selection, chart loading, and synchronized start, and keeps
the selected chart's KEY count plus SINGLE/DOUBLE PLAY visible in the same
panel. The older separate four-second KEY popup is not used.
The Web Arena page remains available for the same queue controls, spectating,
and durable match history.

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
  `CPU`. The server fixes the CPU's final EX SCORE at the minimum AA boundary.
  A strict EX SCORE win over the CPU adds exactly one Arena rating point; a
  tie, loss, or forfeit adds none, and the CPU itself has no rating result.
  `BOT戦を許可` is on by default for compatibility. Turn it off before
  entering the rated queue to wait for human opponents only; ordinary play
  remains available while waiting.
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
  GENOCIDE-normal or official発狂 chart between ☆1 and the weakest
  participant's rating ceiling. Rating 1000 has a ☆10 ceiling and each 100
  rating advances one combined normal/発狂 band.
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
- After Arena play, the result screen remains until the ordinary IR submission
  finishes. Fixed Arena options are restored only after that submission has
  captured the Arena score.
- A normal result or hard fail automatically returns the account to the Arena
  queue. Client shutdown or an unexpected Arena play exit requests a
  zero-score forfeit and stops automatic entry. Entering the normal result
  screen is not an exit and leaves time for the final packet to be accepted.

## Build

Use a JDK 17 distribution that includes JavaFX:

```bash
./gradlew clean build --no-daemon
```

The server release gate checks both Arena protocol release and build identity.
That gate controls supported distribution; server-side score/state validation
remains necessary because an open-source client identity can be imitated.

Build the distributable fat jar with an explicit target platform and
architecture. For example, the macOS Apple Silicon canary is built with:

```bash
./gradlew clean shadowJar --no-daemon -Dplatform=macos -Darch=aarch64
```

The artifact name identifies the unified BMS-IR Arena oraja client:

```text
BMS-IR-Arena-oraja-0.3.2-dev-macos-aarch64.jar
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
