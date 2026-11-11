# Arena oraja 0.4.14.107 optional internal test

Preserves every feature in the 0.4.14.106 test lineage and adds only merged
oraja #472 (#471): the dedicated BMS-IR Leaderboard sends the configured
BMS-IR account ID, so the server applies that account's receive preferences.
The account-scoped 30-second cache, anonymous fallback, other Primary IRs,
ghost downloads and full rival synchronization retain their existing paths.

Paired plugin 0.0.78 adds ordered course stage evidence v2 on capable hosts;
older hosts retain v1. Launcher 0.2.29 and both optional flags remain.
Refs oraja #473, BMS-Mania/IR #1403 and bms-ir-plugin #59.

Operator acceptance: switch Web receive preferences through full, capped,
until-self and score-only, wait for the short cache to expire, then check the
dedicated song list with BMS-IR and another Primary IR, anonymous access,
ghosts, ordinary play and full rival sync. Physical client checks are pending;
Codex does not launch a client or launcher.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed publication
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata and a deployment marker.
