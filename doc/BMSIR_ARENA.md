# BMS-IR Arena client

Status: development implementation for BMS-IR Arena v1. The public Arena
service, download, and Web navigation are not live yet.

## Enabling

1. Configure the normal BMS-IR IR entry with the BMS-IR player ID and game
   password.
2. In the IR configuration screen, enable `BMS-IR Arena`.
3. Keep the default WSS server unless a developer is running a controlled
   local service.
4. Start the client, then enter matchmaking from the authenticated BMS-IR Web
   Arena page.

The startup switch controls the real-time connection. Web entry controls
whether the player is actually queued.

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
- After reservation, the next manual selection is blocked.
- The server selects an official 発狂BMS table chart. The client checks the
  exact MD5 before accepting it; the server validates the LN-scale processed
  note count during play.
- Arena play uses NORMAL and LN. Gauge and ordinary visual/timing preferences
  remain available.
- Assist chart modifiers, trainer features, BPM guide, custom widened judge,
  CONSTANT, battle, and mode conversion are disabled for that Arena play and
  restored afterward.
- When BMS-IR Arena is enabled, the game does not auto-catch the OS mouse
  cursor during play. The canary client must not prevent desktop mouse
  movement while waiting, playing, or returning from an Arena match.
- The client sends current EX and processed-note count at most once per second,
  followed by one immediate final packet.
- After Arena play, the result screen remains until the ordinary IR submission
  finishes. Fixed Arena options are restored only after that submission has
  captured the Arena score.
- A completed match stops queueing. Re-enter from the Web page for another
  match.

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
BMS-IR-Arena-ED-0.1.0-dev-macos-aarch64.jar
```

Do not publish an artifact from an uncommitted worktree. Build the reviewed
commit, record the artifact hashes privately for rollout, and configure the
server allowlists before distribution.
