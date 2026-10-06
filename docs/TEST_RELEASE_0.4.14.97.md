# Arena oraja 0.4.14.97 optional internal test

This candidate preserves the 0.4.14.96 test lineage and adds only merged
#440 (#439): the CHARTS FROM AUDIO root row and the folder/audio-file rows
inside it are drawn (folder bar / song bar) instead of blank, the top
settings row reads "Play: <song name>", and labels avoid glyphs the skin
font lacks. Plugin 0.0.77, launcher 0.2.29, and both optional update flags
remain unchanged.

Operator checks: the CHARTS FROM AUDIO row and every folder/file row show
their names; entering a file shows "Play: <song name>" as the large title on
the top row; repeat the 0.4.14.96 controller-only checks, ordinary play/IR,
and the pending BGA, EQ, and function-key checks. Codex does not launch the
client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
