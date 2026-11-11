# Arena oraja 0.4.14.101 optional internal test

This candidate preserves the 0.4.14.100 test lineage and adds only merged
#454 (#453), from operator feedback on 0.4.14.100:

- Rhythm while following the music: 16ths (16 per bar) or triplets (24 per
  bar, triplet 8ths and 16ths); notes still only where the music hits. The
  fixed grid adds 12th and 24th. Automatic triplet detection was measured
  and is unreliable, so the rhythm is the player's choice.
- Metrical bonus by real interval (>=250 ms beat-like, >=150 ms 8th-like):
  unchanged at 150 BPM, 16ths at 79 BPM no longer penalized.
- Ranking mixes the song-wide level (0.7): notes on positions without a
  clear onset -20%, so shaker/kick-only passages are not filled with 8ths.

26 real BMS through the Java pipeline: "as the music" F1 0.75 (0.73 fully
automatic). Plugin 0.0.77, launcher 0.2.29 and both optional flags remain.

Operator checks: a triplet/shuffle song with Rhythm = 三連系; a slow song's
16ths with the default amount; a shaker- or kick-only passage; the 12th/24th
fixed grids. Repeat ordinary play/IR and pending BGA/EQ/function-key checks.
Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
