# Arena oraja 0.4.14.104 optional internal test

This candidate preserves the 0.4.14.103 test lineage and adds only merged
#463 (#462), from operator feedback on generated charts:

- No notes in silent breaks: positions below 5% of the song's loud level are
  never chosen, so the metrical bonus cannot fill a break (CANDY POP had five
  notes in a 1.3-second A-melody break). Higher amounts are capped by audible
  positions.
- No slightly-off notes in 8th streams: a triplet wins over its 16th only when
  the onset peaks at least 75% of the way to the triplet, and a 1/3 also needs
  a 2/3 triplet within four beats; late sung 16ths stay on the 16th. A peak at
  the far edge of the window (at half tempo, the real 16th at 3/8 of the beat,
  燦々デイズ at 90 BPM) drops the triplet.

Measured: 26 BMS F1 at amount 3 0.746 -> 0.750 and amount 4 0.728 -> 0.737,
34 of 40 BMS triplets kept; lone 1/3 notes in straight pop cut 65-90%
(CANDY POP 67 -> 24, 超 Super Star 107 -> 15), 燦々デイズ triplet notes
90 -> 36.

Plugin 0.0.77, launcher 0.2.29 and both optional flags remain.

Operator checks: regenerate CANDY POP (no notes in the A-melody break) and
燦々デイズ / other pop songs (8th streams without slightly-off notes; real
triplet passages such as Shaker's still charted). Repeat ordinary play/IR and
the pending BGA/EQ/function-key checks. Codex does not launch the client or
launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
