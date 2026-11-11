# Arena oraja 0.4.14.100 optional internal test

This candidate preserves the 0.4.14.99 test lineage and adds only merged
#449 (#448), from operator feedback on 0.4.14.99:

- Scratch only on clear hi-hats (high band >= 2x low and mid, stronger half,
  at least an 8th apart): 9.5% of positions on 13 operator songs (was 38-56%).
- When the half/double tempo scores >= 75% of the chosen one, a one-press row
  under Play (and a drop-window button) switches to it (アオとキラメキ:
  173 -> 86.5, which the operator confirmed plays right).
- The player's BPM / first-beat correction is remembered per audio file and
  reused on the next analysis with the same result.

Plugin 0.0.77, launcher 0.2.29, and both optional update flags remain.
The version is the first with a three-digit last component; patch-server and
launcher compare components numerically, and the Arena gate matches exactly.

Operator checks: scratch amount with scratch on; the half/double row on
アオとキラメキ and fast songs; correct a song's BPM, play, re-enter it (the
correction is reused, "保存した補正" shown) and reset it. Repeat ordinary
play/IR and pending BGA/EQ/function-key checks. Codex does not launch the
client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
