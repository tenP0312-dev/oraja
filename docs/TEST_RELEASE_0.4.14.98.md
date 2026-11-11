# Arena oraja 0.4.14.98 optional internal test

This candidate preserves the 0.4.14.97 test lineage and adds only merged
#443 (#442): 0.4.14.97 crashed in Music Select (`ClassCastException` in
`BarRenderer.prepare`) as soon as the CHARTS FROM AUDIO row was drawn,
because the text-status step cast song/folder-style bars to `SongBar` /
`FolderBar`. The step now checks the bar class. Plugin 0.0.77, launcher
0.2.29, and both optional update flags remain unchanged.

Operator checks: with "Audio for charts" set, Music Select opens without a
crash and shows CHARTS FROM AUDIO and its folder/file rows with names; the
controller-only flow plays and returns. Repeat ordinary play/IR and the
pending BGA, EQ, and function-key checks. Codex does not launch the client or
launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
