# Arena oraja 0.4.14.95 optional internal test

This candidate preserves the 0.4.14.94 test lineage (BGA asynchronous pipeline,
output EQ overlay, function-key/numpad menu). It adds only the merged #434
charts generated from dropped audio (with the #432 estimator tool it builds
on), #430 key/play-setting profiles, and #428 IGNORED folder. Plugin 0.0.77,
launcher 0.2.29, and both optional update flags remain unchanged.

Source main is not used as a replacement for the distribution lineage. The
merged feature commits are applied directly to retain every previously
distributed feature. BGA #413/#414/#415 remain pending physical and
native-memory acceptance; this candidate does not complete or merge those
Drafts.

Operator checks:

- Generated charts: in Music Select drop one MP3/OGG/WAV onto the window; the
  analysis window shows BPM/first beat. Check x2/x1/2, half-beat shift,
  division, min/max chord and scratch, then Play. Notes should match the
  music; the work-folder notice appears and no score, replay, or IR
  submission is recorded. Return to Music Select after the result.
- Key profiles: in the Arena overlay Profiles tab, save, overwrite, duplicate,
  rename, delete, and apply (all modes / current mode / keys only), including
  controller and MIDI assignments.
- IGNORED folder: ignored songs and charts appear under the IGNORED root
  folder and can be restored; turning off "Keep ignored songs visible" in the
  launcher BMS-IR tab restores the previous hiding.
- Repeat ordinary play/IR and the pending BGA, EQ, and function-key checks.

Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
