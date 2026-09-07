# 7K TO 9K trial conversion

This is a disposable prototype for player feedback, not a ranked chart family.
Tracking: tenP0312-dev/oraja#357; original requests: BMS-Mania/IR#478.

## Use and boundaries

Open MANIAC OPTIONS using the existing F2/control chord, select `7K TO 9K`,
and set `ON / NO SAVE`. Return to Music Select normally. Play a 7KEY chart
using the configured 9KEY skin and input bindings. Turning the option off
restores ordinary play. The choice is off by default and saved per player.

The converter applies to solo single-chart PLAY and AUTOPLAY. Courses,
practice, replay playback, borrowed ghost placements, G-BATTLE, BMS-IR Arena,
and the legacy Arena connection do not enable it. Unsupported chart modes
retain their existing behavior. Other MANIAC settings remain saved but are
suspended on converted plays. Normal lane shuffle, Random Trainer, One Bass,
the legacy 7-to-9 placement, and legacy LN/mine modifiers are also suspended
for those plays. Existing selected/authored LN interpretation remains intact.

The result screen still shows the current attempt, but no score, lamp, play
count, history, player aggregate, or replay is written. Direct score/replay
writer calls are guarded as well as result handling. Ordinary and MANIAC IR
submission are disabled, and helper result/play-end messages are suppressed
to avoid recording the trial as an ordinary play. No leaderboard is created.
Settings, operational logs, and ordinary scene notifications are unaffected.

## Placement, version 1 prototype

- Work on decoded in-memory objects; leave chart files and source hashes intact.
- Use nine physical pop'n lanes, with no scratch lane. Normal source keys keep
  their relative left-to-right order within each chord.
- Search placements deterministically, maximizing the number of retained
  starts first, then minimizing placement costs. No random generation seed.
- Reject chords containing any of `147 148 149 158 159 169 258 259 269 369`,
  and reject more than six simultaneously occupied buttons. Digits are
  one-based physical button numbers. The rule includes held LNs.
- Reserve each retained LN's destination through its release timestamp. Keep
  the original start/end pair, timing, keysound, type, slices, and layers.
- Prefer the original keyboard shape mapped to buttons 2--8. For isolated
  normal-key sequences, favor the source ascent/descent direction and recent
  lane continuity. Scratch phrases favor alternating 1 and 9, then nearby
  odd buttons when the complete chord requires a different placement.
- Use an 80 ms recent-note penalty for repeated destinations and combinations
  that resemble impossible chords. Use a 500 ms continuity/phrase window.
  These are initial tuning values, not documented universal human limits.
- When no safe placement retains every start, move excess notes to BGM at
  the original time. A reduced LN moves both endpoints together using the
  existing autoplay representation. Retained count is maximized for the
  current chord given existing holds, not globally across the entire song.
- Preserve hidden notes in the base 2--8/1 mapping. Omit mines in this trial;
  a mine is a hazard rather than a missing playable keysound.
- Report the number of reduced starts at play startup (one LN counts as one
  removed start, irrespective of the mode's judged-note count).

## Evidence and remaining acceptance

The community impossible-chord list is at
https://w.atwiki.jp/asagaolabo/pages/2567.html . It is a static baseline,
not a guarantee about rapid transitions, hand size, or controller geometry.
PMS construction guidance also recommends checking unintentional impossible
chords and audio differences:
https://pmsdifficulty.xxxxxxxx.jp/guide_chart.html .

Automated coverage includes all 255 nonempty source chords, six-button
capacity, deterministic stair/scratch placement, held HCN pairing, audio
object/slice/layer preservation, unsupported modes, setting round trips,
Arena blocking, MANIAC submission denial, result persistence policy, and
byte-identical record files after attempted score/count/replay writes with
Force LN both off and on.

Physical acceptance remains open: test a sparse melody, an ascending and
descending staircase, scratch-heavy passages, dense chords, long-note holds,
and quick retry with 9KEY controls. Ask whether the original phrase remains
recognizable, whether a rapid transition feels unreasonable, and whether
scratch alternation interrupts the melody. The heuristic does not guarantee
the absence of quasi-impossible transitions. Longer holds may cause excessive
later reduction; that is a useful prototype feedback case.

Source completion does not distribute a body build. Binary publication,
release versioning, channel/gate updates, and physical acceptance remain the
separate release stage. Do not reuse this prototype's storage identity as a
future ranked chart identity.
