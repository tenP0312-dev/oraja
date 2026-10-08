# Arena oraja 0.4.14.106 optional internal test

This candidate preserves the 0.4.14.105 test lineage and adds only merged
#469 (#468), from operator feedback on generated charts:

- Repeats keep fills: positions always come from each bar's own audio; a
  detected repeat only reuses lanes (DOPAMINE chorus gaps 3 -> 0, CANDY POP
  drum roll bars 6/8 -> 10/11 notes).
- Chorus chords: chord sizes are ranked against each note's own bar instead
  of the whole song (per-4-bar mean chord 1.11-2.87 -> 1.43-1.92 on 4 songs).
- Dying tails: no note where the level is below -15 dB and falling (Uptown
  Funk's "Stop!" break).
- Non-music spans: notes stay within the span whose bars line up with the
  beat grid (Sugar's 38 s wedding scene and video outro get none).

26 BMS renders: F1 at amount 3 0.750 -> 0.758, amount 4 0.739 -> 0.748; 0.36%
of notes fall outside the detected span.

Plugin 0.0.77, launcher 0.2.29 and both optional flags remain.

Operator checks: regenerate DOPAMINE and CANDY POP with repeats on (no lost
beats, the drum roll is charted), compare chorus and A-melody chords, Uptown
Funk's second verse break, and Sugar (notes start with the music). Repeat
ordinary play/IR and the pending BGA/EQ/function-key checks. Codex does not
launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
