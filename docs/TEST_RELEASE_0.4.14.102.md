# Arena oraja 0.4.14.102 optional internal test

This candidate preserves the 0.4.14.101 test lineage and adds only merged
#457 (#456). Rhythm changes inside songs, so the song-wide 16ths/triplets
setting of 0.4.14.101 is replaced by a union grid: each beat offers 0, 1/4,
1/3, 1/2, 2/3 and 3/4, with 1/4 vs 1/3 and 2/3 vs 3/4 competing (stronger
onset wins). Straight and triplet passages are charted per position.

26 real BMS: Java pipeline "as the music" F1 0.75 (0.74 fully automatic, was
0.73); triplets captured where present (one song 44 of 48 in the prototype),
1.9% false triplet notes. Plugin 0.0.77, launcher 0.2.29 and both optional
flags remain.

Operator checks: a song that switches to triplets partway (only that part
should get triplets); straight songs without stray triplets; Shaker at 145 and
at its 96.67 candidate. Repeat ordinary play/IR and pending BGA/EQ/function-key
checks. Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
