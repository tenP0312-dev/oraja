# Arena oraja 0.4.14.109 optional internal test

Preserves every feature in the 0.4.14.108 test lineage and adds only three
changes that were merged to main but missing from the test lineage:

- oraja #452 (#451): on macOS the game window holds display and idle-system
  sleep assertions through `caffeinate -w`, so controller-only play no longer
  lets the display sleep; the assertions end with the process.
- oraja #344: NANTOKA MANIA HCN bodies switch to the released image after an
  early release while the end stays pending for reentry and end judgment.
- `eb5a78ad`: looping skin-select previews keep falling notes on the
  synthetic PLAY timer, so later loops restart from the chart start.

Plugin 0.0.79, launcher 0.2.29 and both optional flags remain unchanged.
Refs oraja #478.

Operator acceptance: play on macOS with only a controller for longer than the
display sleep timeout and confirm no sleep and no remaining `caffeinate`
assertion after exit; early-release a NANTOKA HCN; loop a play-skin preview.
Physical client checks are pending; Codex does not launch a client or launcher.
