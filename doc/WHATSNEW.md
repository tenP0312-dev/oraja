# Unreleased

- Fixed LR2-style START+6/7 SUD+ changes recalculating HI-SPEED even when the
  dedicated FHS recalculation switch was disabled. Ordinary scratch/mouse
  cover control and IIDX FHS SUD+ green-number reload behavior are unchanged.

# Arena oraja 0.4.14.37

- The My Difficulty Table overlay now stages existing-table metadata and up to
  64 selected-chart add/update/remove changes in memory. Pending changes can be
  reviewed, individually undone, discarded together, or saved atomically for
  one authoritative Music Select hot reload. Table switching/reload cannot
  silently discard a draft, and revision conflicts retain it until the user
  explicitly reviews and rebases it.
- Japanese-capable Arena fields now place an IME-aware OS text control directly
  over the clicked field, so conversion text remains visible in the normal
  input position. The underlying field remains editable if the OS control
  cannot take focus, and chat/room buffers retain their limits for UTF-8 text.
- ImGui-owned keyboard, NUMPAD, mouse-scratch, scroll, click, and drag input is
  discarded before the underlying game or skin processes the same frame.

# Arena oraja 0.4.14.18

- Restored the launcher self-update path for users who skipped the previous
  internal test release. The test patch now includes launcher 0.2.11 together
  with the Arena body update.

## MANIAC play, Double Battle, and Arena presentation

- Added an OpenLR2 Beta3 v100201-derived full-window black MANIAC OPTIONS
  screen on F2 hold or a one-second 2+4+6 key chord. The screen uses a simple
  one-column text list: 1KEY moves down, 2KEY moves up, 6KEY cycles the value,
  and 7KEY saves and returns to Music Select. Music Select input pauses while
  it is open and settings are committed once when returning. It includes EX modes,
  note transforms, mines, strict-judge options, and visual
  effects. Ranked generated charts use a fixed MT19937 generation seed while
  normal RANDOM and Random Trainer remain per-play options.
- Unified the existing Music Select `EXTRA NOTE` control and pre-launch setting
  with the LR2-compatible MANIAC EXTRA MODE. The old ASSIST-only Extra Note
  modifier is disabled, so the skin, F2 screen, dedicated score DB, and online
  ranking now use one OFF / LEVEL 1 / LEVEL 2 / LEVEL 3 setting.
- Restored OFF / FLIP / BATTLE / BATTLE AS to the normal DP option display.
  BATTLE and BATTLE AS now control MANIAC Double Battle and its AUTO SCRATCH
  setting instead of the legacy L-ASSIST implementation. F2 remains available
  for the same settings plus RANDOM LINK and the native-DP warning.
- Manual-scratch and auto-scratch Double Battle keep separate local and BMS-IR
  ranking identities. AUTO SCRATCH moves both generated scratch lanes to
  autoplay while preserving existing manual Double Battle records.
- MANIAC and Double Battle records use a separate `bmsir_maniac.db`. Ranked
  transforms use isolated BMS-IR leaderboards, ghosts, and score sync; a safe
  vanilla `score.db` export is available from pre-launch configuration. Music
  Select lamps now follow the exact active MANIAC/Double Battle settings and
  clear retained bar scores before reloading after a play, when F2 or skin
  settings change, or when synchronized records change.
- Enlarged the MANIAC OPTIONS text and added a brief description for the
  selected option on wide screens.
- Split the Arena graph into its own window, added score differences, match
  state, rating changes, persistent private-room records, notification-sound
  testing, and optional detailed protocol logging.
- Added Japanese and English text for Arena surfaces, F2 MANIAC OPTIONS, the
  built-in Ctrl+Shift+F5 windows, notifications, and BMS-IR pre-launch
  settings. The READY note preview now uses a one-second fade cycle.
- Added the portable launcher and signed patch format. Windows test packages
  launch from `BMS-IR Arena Test.exe`; the existing BAT remains available.
- Launcher 0.2.11 installs and prefers the dedicated `Arena-oraja.jar` while
  retaining `beatoraja.jar` and versioned clients only as compatibility
  fallbacks. Its Arena launch button enters Music Select directly instead of
  opening pre-launch configuration.
- Client windows and startup progress now display the full four-part internal
  release version instead of truncating it to `0.4.14`.
- EXTRA MODE, ADD NOTES, and LOUDNESS now collapse existing long notes before
  generation as LR2 does. Dedicated MANIAC IR targets cannot fall through to
  the ordinary leaderboard, and the legacy CONSTANT display reads the active
  PlayConfig.

# Arena oraja 0.4.13

## Leaderboard, overlay, and play-setting refinements

- Unified the two song leaderboard entries as `BMS-IR Leaderboard`. Rankings
  and selectable ghost data now come directly from the BMS-IR HTTPS endpoint,
  with gzip, bounded responses, longer reads, and a short ranking cache.
- Restored a persistent Arena overlay checkbox to the F5 mod menu. Turning it
  back on restores the last normal or compact display mode.
- Expanded the START+6/7 cover step range to 1--1000 and added an independent,
  default-OFF switch for recalculating HI-SPEED when a cover changes.
- Added a default-OFF option that restores the pre-play judge timing after a
  chart when automatic timing adjustment was enabled at play start. Lua skins
  can read/write it or trigger the registered toggle event.
- Added one switch for all transient INFO toasts. Warning and error notices
  remain visible.
- Removed duplicate startup login for the same IR/account and shortened the
  displayed client identity to `Arena oraja 0.4.13`.

# BMS-IR Arena oraja 0.4.12

## Startup progress and configurable play controls

- Replaced the post-FFmpeg black startup wait with an in-window progress log
  covering the song database, IR login, rivals, audio, input, difficulty
  tables, skins, and Arena connection.
- Added three START+6/7 modes: existing oraja high-speed controls, LR2-style
  SUD+ adjustment, and extended SUD+/HIDDEN/LIFT adjustment. Cover step size is
  configurable, and each press changes it once without hold-repeat.
- Added configurable actions for every physical NUMPAD 0--9 key. Music Select
  and global shortcuts can be assigned independently, and the judge-timing
  adjustment step is configurable.

# BMS-IR Arena oraja 0.4.11

## Arena target, graph, and four-player rated matches

- Arena can now write the selected opponent's live EX SCORE directly into the
  normal skin TARGET SCORE and target name. The setting is shared between the
  startup BMS-IR settings and the Arena overlay.
- Added fixed entry-order score graphs. In fixed mode, both graph columns and
  player colors stay tied to the player instead of moving with the live rank.
- Rated Arena now always resolves as a four-player BO2. Missing seats are
  filled with EASY, NORMAL, and HARD server CPUs; private-room limits are
  unchanged.
- CPU final EX SCORE is fixed before the chart from EASY A--AA, NORMAL
  A--AAA, and HARD A--the chart's BMS-IR all-time best, then only revealed
  progressively during play.
- Hidden seasonal CPU ratings now participate in the same four-player Elo
  settlement as humans. BO2 rank is decided only by the two-song point total.
- CPU rows are no longer shown as waiting users on the Arena status surfaces.

# BMS-IR Arena oraja 0.4.10

## End-of-chart live error handling

- Repeated copies of the same Arena error are shown only once per match while
  every received error remains in the diagnostic log.
- This complements the server's bounded live-only chart-total correction;
  final result validation remains strict.

# BMS-IR Arena oraja 0.4.9

## Live series reliability hotfix

- Rated BO2 keeps its original participants in both rounds. A later waiter can
  no longer enter round 2, and leaving after series creation is settled as a
  rated walkover/forfeit instead of invalidating the whole series.
- Live SP/DP score updates now run at 4 Hz and are capped to the server chart
  total. Clear and FAILED results stay on the result screen until Arena accepts
  the final packet.
- Fixed 14KEY-only private rooms, room/password/chat keyboard leakage, plain-F5
  recovery, the hidden 30-second fill countdown, persistent mouse cursor, and
  the immovable MATCH FOUND/status window. DP options use compact three-line
  labels.
- Java-bundled launchers now load the packaged `ir` plugin directory explicitly;
  the body also auto-discovers a local `ir` directory.

# BMS-IR Arena oraja 0.4.8

## Points-only BO2 and overlay hotkeys

- Rated BO2 placement and rating now use only the two-round point total.
  Equal points share placement and have no direct rating change; EX SCORE and
  EX rate remain visible only as reference values.
- Backspace and Delete can now be assigned as standalone Arena overlay
  shortcuts or used in exact multi-key chords. Use the explicit `解除` button
  to clear a shortcut.

# BMS-IR Arena oraja 0.4.7

## Continuous CPU waiting matches

- CPU BO2 repeats after a five-second interval while one CPU-enabled player
  remains in the rated queue. A waiting human opponent takes priority.
- CPU charts are selected from all owned charts between the player's rated
  ceiling and five bands below it, inclusive.
- CPU final EX SCORE now ranges from A through MAX.

# BMS-IR Arena oraja 0.4.6

## BMS-IR Dan local sync

- Added `BMS-IR段位をローカル同期する` to the startup `BMS-IR固有設定` tab.
- Successful BMS-IR Primary IR table reads save only class/Dan courses to a
  backup-safe per-player cache. Score Attack and ordinary course tables are not
  imported.
- The last good Dan cache remains available under the local `COURSE` root when
  offline or when a later table request fails. Personal files in `course/` are
  never replaced.

# BMS-IR Arena oraja 0.4.5-dev

## BMS-IR-specific startup settings

- Added a `BMS-IR固有設定` launcher tab.
- One-bass input and the first-timing note preview can be enabled or disabled
  independently and are preserved in the backup-safe BMS-IR sidecar.
- The first-timing preview now appears during chart loading as soon as the
  resolved chart and active skin notes can be rendered, then remains through
  READY and disappears when play starts.
- The mandatory BMS-IR LONG NOTE compatibility rule is shown read-only with an
  explanation that CN/HCN scores are not accepted.

# Endless Dream 0.3.1
## New features
- #### Context Menu
  - Keys 3 and 5 now activate the Context Menu
  - The Context Menu can be opened on a Song or top-level Table folder in Music Select
  - Autoplay and Practice Mode functionality has been placed inside the Context Menu
- #### LR2IR and LR2 G-BATTLE Support
  - Lunatic Rave 2 Internet Ranking song leaderboards are now accessible inside the Context Menu
  - Pressing play on a LR2IR leaderboard score will enter G-BATTLE
    - Your pacemaker will be set to challenge the leaderboard score you have selected
    - With the RANDOM option enabled your random will match the random that the leaderboard score was obtained with
    - Only 7K is currently supported
    - Currently only NONRAN, MIRROR, and RANDOM are supported.
- #### In-game skin configuration
  - Update your skin settings live inside the game
    - Accessible via the Skin Configuration window in the mod menu
    - The menu will let you configure the currently displayed scene's skin configuration
      - To edit the PLAY scene, play a chart with the menu open. The same goes for RESULT, etc.
      - You can freeze gameplay timers, very useful for editing scenes like DECIDE
  - Edit skin elements dimensions and properties live with the Skin Widget Manager
    - Make small edits to your skin on the fly, resize elements with the mouse
    - Comes with an undo button and full history for all alterations
- #### OBS Scene switcher and Automatic Recording
  - Automate scene switching from within the Endless Dream launcher
  - Configure replay recording and saving with per-scene settings
    - Recordings can be saved: always, whenever you take a screenshot, or whenever a replay is saved (Using the auto-save replay feature)
- #### Automatically send screenshots to Discord with Webhooks
  - Send a plain image or a rich embed to as many channels as you'd like with webhooks as soon as you take a screenshot
  - Add as many channels as you'd like, configurable from the Discord tab in the launcher
- #### Additional features
  - Automatic per-chart volume normalization has been added to the Audio tab in the launcher
  - An option to skip the DECIDE screen has been added to the Music Select tab in the launcher
  - You can force visual LN end caps to display in the Play Option tab in the launcher
  - A list of default table URLs is provided in a new table in the Resource tab in the launcher
    - You can quickly add popular tables into your active table list
    - You still need to reload your tables for this to take effect
  - The Misc Settings mod menu window has more play settings that previously required a restart to configure
  - Song Manager window in the mod menu now has an option to sort songs by least recently played

## Behavior changes
- Playing courses with CONSTANT is now ASSIST CLEAR (previously CONSTANT had no effect)
- Switching to CN/HCN LN modes has been disabled in game. This can now only be changed in the launcher
- Notifications will now be displayed for a variety of different events
  - E.g. when downloading songs, playing a song with options that will restrict score saving, etc.
- The analog scratch threshold default has been changed from 100 to 50
- The spinning turntable emblem in skins now spins smoother
- The sound of quickly scrolling through music select is less loud
- Improved audio when changing between scenes
- Music previews generated with the preview generator tool will have lower priority
- Skins with large bitmap fonts will load faster
- In-progress song downloads now display a progress bar
- Loading BMS and Table will now display a spinner instead of hanging the launcher
- Song Downloader menu has a retry failed downloads button
- Config saving and loading has gotten more robust
- Your settings will now save periodically when you have the game open

## Bug fixes
- Fixed the Linux Wayland crash with NVIDIA graphics cards (no more gamescope!)
- Fixed a crash when launching borderless without a set monitor
- Fixed issues with loading osu files
- On Linux, fixed opening chart folder with F3, the new version download link in the launcher, and fixed the launcher becoming non-functional after starting the game
- Fixed Discord rich presence on macOS
- Fixed bitmap font text display becoming transparent in certain contexts
- Fixed a crash caused by incorrect entries in some difficulty tables
- Fixed the Fullscreen toggle button (F4) causing skins to behave incorrectly and making the window bar inaccessible

## Known issues:
- [Linux] Certain skin fonts may only load partially due to incorrect letter case in their filenames. Can be manually resolved by renaming the offending files.
- [Linux] When loading configuration files created on Windows, skin settings will fail to transfer. Fix by replacing backslashes with forward slashes in the skin paths in saved skin settings in `config.json` and `config_play.json` in the player folder.
- [macOS] Videos in skins sometimes "flash"
- Skin Widget Manager works abnormally when editing sliders or scrollbars
