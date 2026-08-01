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
