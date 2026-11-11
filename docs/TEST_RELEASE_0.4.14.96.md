# Arena oraja 0.4.14.96 optional internal test

This candidate preserves the 0.4.14.95 test lineage (generated charts, key
profiles, IGNORED folder, BGA asynchronous pipeline, output EQ overlay,
function-key/numpad menu). It adds only merged #437 (#436): controller-only
generated charts. Plugin 0.0.77, launcher 0.2.29, and both optional update
flags remain unchanged. BGA #413/#414/#415 remain pending physical and
native-memory acceptance and are not merged by this candidate.

Operator checks:

- Pre-launch config, Resource tab: choose and clear "Audio for charts"
  (譜面生成用の音源); the setting persists after restart.
- Music Select root shows CHARTS FROM AUDIO only while a folder is set; it
  lists sub-folders, then audio files. An empty folder shows one notice row.
- Entering a file shows "Analyzing..." and refreshes by itself. With the
  controller only: change note division, min/max chord (max follows min),
  scratch, reshuffle, BPM x2/x1/2/candidates/±0.01, first beat ±10 ms,
  half-beat shift and reset; the cursor stays on the changed row. Play from
  the top row; the result returns to Music Select with no score, replay, or
  IR submission. Settings are remembered and shared with the drop window.
- A drop still opens the drop window; folder sessions do not.
- Repeat ordinary play/IR and the pending 0.4.14.95, BGA, EQ, and
  function-key checks.

Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
