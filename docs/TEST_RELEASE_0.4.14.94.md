# Arena oraja 0.4.14.94 optional internal test

This candidate preserves the 0.4.14.93 BGA test lineage and the merged release
wording cleanup. It adds only the merged #422 output EQ overlay and #423
function-key/numpad menu. Plugin 0.0.77, launcher 0.2.29, and both optional
update flags remain unchanged.

Source main is not used as a replacement for the distribution lineage. The
two feature commits are applied directly to retain every previously distributed
feature. BGA #413/#414/#415 remain pending physical and native-memory acceptance;
this candidate does not complete or merge those Drafts.

Operator checks: adjust EQ via F5 → その他設定 → 出力イコライザー and check
mode switching, gain, saving, and sound continuity on PortAudio/ASIO. OpenAL
should show its unsupported output notice. On Music Select, open the function
key menu from F5 and both Arena overlays; check F-key and numpad actions,
disabled gameplay timing actions, folder-only F10, and X-post confirmation.
Also repeat ordinary play/IR and the pending BGA #413 checks. Codex does not
launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
