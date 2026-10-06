# Arena oraja 0.4.14.99 optional internal test

This candidate preserves the 0.4.14.98 test lineage and adds only merged
#446 (#445): generated charts follow the music.

- Place where the music hits (default on): 16th positions ranked by local
  onset strength plus a metrical bonus; the count follows the song's clear
  onsets scaled by note amount (light / reduced / as the music / dense).
  Off = the previous fixed 4th / 8th / 16th grid.
- Repeat repeated phrases (default on): bars matching an earlier bar's
  low/mid/high pattern reuse its lanes and chord sizes where both hit.
- Scratch (toggle): on hi-hat dominated positions, never on adjacent 16ths.

On 26 real BMS renders, "as the music" matched charted note timings with
F1 0.75 (0.72 fully automatic) vs 0.69 (0.67) for the previous every-8th grid.
Plugin 0.0.77, launcher 0.2.29, and both optional update flags remain.

Operator checks: from CHARTS FROM AUDIO and the drop window, play with the
defaults (rests and fills follow the song, repeated phrases look the same),
each note amount, scratch on/off, repeat off, and "place where the music
hits" off with 4th/8th/16th (plain fixed grid). Settings persist across
restart. Repeat ordinary play/IR and the pending BGA, EQ and function-key
checks. Codex does not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
