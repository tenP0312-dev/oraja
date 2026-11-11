# Arena oraja 0.4.14.110 optional internal test

Preserves every feature in the 0.4.14.109 test lineage and adds only the
skin-controlled hand camera from oraja #480 / #482 (`e1c1793d`).

- Camera is OFF by default. Pre-launch Video settings select the camera,
  capture size/FPS and horizontal mirroring.
- JSON/Lua `camera` objects use ordinary destinations for position, size,
  timer/op/draw and aspect fitting. Normalized crop fields select the source
  area. Play-only placement is achieved by declaring the object only in the
  play skin. Existing skins and Skin Select previews do not open a camera.
- Shipped FFmpeg captures RGB video without audio in a separate process;
  one reader and three buffers retain only the latest frame. No demand,
  pause and exit stop capture. Driver failures disable only the camera.
- Launcher 0.2.30 supplies the macOS camera usage description and is included
  as an optional update. Plugin 0.0.79 is retained. Body/plugin mandatory
  flags and the existing minimum launcher policy remain unchanged.

See [settings and skin examples](HAND_CAMERA.md). Score/replay/IR/Arena
contracts are unchanged. Server gates must be additive for the exact reviewed
artifacts before signed test promotion. No stable/mandatory update or general
release notice belongs to this test.

Source preparation: #483. Physical camera, rendering, launcher permissions,
ordinary play/Arena/BGA, stop/reentry and disconnect acceptance remain
operator-run in #481. Codex does not launch a client or launcher for QA.
