# BMS-IR Arena client

Status: controlled BMS-IR Arena v1 canary. The direct service and OS-specific
downloads are live, but global Web navigation and announcement are not.

## Enabling

1. Configure the normal BMS-IR IR entry with the BMS-IR player ID and game
   password.
2. In the IR configuration screen, enable `BMS-IR Arena`.
3. Keep the default WSS server unless a developer is running a controlled
   local service.
4. Start the client, then use the `BMS-IR Arena` overlay to enter matchmaking.

The startup switch controls the real-time connection. The authenticated
in-game overlay controls entry, waiting cancellation, and match withdrawal.
It also shows the current rating, an up-to-eight-player real-time vertical EX
graph with MAX/AAA/AA/A guides and per-player OP, live/final result details
with clear lamps, and the Arena rating leaders. During play, the graph opens
at the bottom center and can be moved and resized; ImGui stores the adjusted
position and size in `layout.ini`. The Web Arena page remains available for the
same queue controls, spectating, and durable match history.

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
- Courses, practice, autoplay, and replay are not eligible matching states.
- If a match is reserved during an ordinary song, finish the song and its IR
  result first.
- After the fill window closes, a 20-second nomination phase opens. The normal
  selector stays navigable, and selecting a song nominates that exact chart
  instead of starting ordinary play. The overlay also provides an explicit
  server-random choice.
- Each participant contributes one candidate slot. A missing nomination at
  the deadline becomes a server-random official-table chart. Candidate charts
  remain hidden until every slot is filled; the server then reveals all
  candidates, selects one slot uniformly, and highlights the selected chart
  in the overlay.
- Every nominated or random candidate must be a positive-note-count official
  発狂BMS table chart between ★1 and the weakest participant's rating ceiling.
  The client checks the selected MD5 before accepting it; the server validates
  the LN-scale processed-note count during play.
- Arena play keeps the selected NORMAL, MIRROR, RANDOM, R-RANDOM, S-RANDOM, or
  SPIRAL lane option and uses LN. H-RANDOM, ALL-SCR, RANDOM-EX, and
  S-RANDOM-EX are assist-class options for Arena and are clamped to NORMAL.
  Gauge and ordinary visual/timing preferences remain available.
- Assist chart modifiers, trainer features, BPM guide, custom widened judge,
  CONSTANT, battle, and mode conversion are disabled for that Arena play and
  restored afterward.
- When BMS-IR Arena is enabled, the game does not auto-catch the OS mouse
  cursor during play. The canary client must not prevent desktop mouse
  movement while waiting, playing, or returning from an Arena match.
- Start+Select and Escape cannot abort a server-selected Arena chart. This
  applies only while the Arena play is active; Arena OFF and ordinary play keep
  their normal input behavior.
- The client sends current EX and processed-note count at most once per second,
  together with the selected OP, followed by one immediate final packet with
  the result ClearType. The processed-note count uses the larger of the play
  counter and judged-note total so skins or rules that keep the in-play pass
  counter stale still update spectators. A normal completion uses the
  server-selected chart total; hard fail keeps the actual processed count.
- SP sends only the active 1P lane option even if stale 2P/FLIP settings exist.
  DP sends both sides and FLIP together with the chart play mode.
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

The artifact name identifies this as the dedicated BMS-IR Arena ED client:

```text
BMS-IR-Arena-ED-0.1.5-dev-macos-aarch64.jar
```

Do not publish an artifact from an uncommitted worktree. Build the reviewed
commit, record the artifact hashes privately for rollout, and configure the
server allowlists before distribution.
