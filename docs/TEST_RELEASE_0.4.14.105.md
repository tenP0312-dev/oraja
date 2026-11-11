# Arena oraja 0.4.14.105 optional internal test

This candidate preserves the 0.4.14.104 test lineage and adds only merged
#466 (#465): generated charts follow a drifting tempo. Each bar's beat/8th
grid is aligned to the onsets; when the alignment drifts 30-150 ms from the
fixed grid, the chart follows the music's bars with exact per-bar BPM changes
(#BPMxx, channel 08). Steady songs keep a constant BPM; beyond 150 ms the
tempo estimate itself is wrong and the fixed grid is kept.

Measured: 誘惑 (slows ~60 ms behind over its last 80 s) last 47 s 7.1 -> 12.9
notes per bar and notes within 20 ms of an onset 36% -> 89%; BPM 89.73-90.25.
26 BMS renders: no false switch with the true grid and F1 unchanged.

Charting guitar by separating harmonic from percussive sound was measured and
not added: it lowered the match on every test set.

Plugin 0.0.77, launcher 0.2.29 and both optional flags remain.

Operator checks: regenerate 誘惑; the ending keeps its density and the notes
stay on the music; the scroll speed change is not noticeable. A steady song
shows a single BPM. Repeat ordinary play/IR and the pending BGA/EQ/function-key
checks. Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
