# Arena oraja 0.4.14.103 optional internal test

This candidate preserves the 0.4.14.102 test lineage and adds only merged
#460 (#459): generated charts from video files (mp4, m4v, webm, mpg, mpeg,
m1v, m2v, avi, wmv). The audio track is extracted with the bundled FFmpeg into
a WAV aligned so its time 0 is the first video frame (the BGA decoders' video
time 0); the chart starts that WAV and the movie (`#BMP01`, hard link or copy)
together. Synthetic checks decoded as BGA decodes: H.264+AAC 0 ms, a file with
audio 0.3 s late keeps its +299 ms, WebM/Opus -7 ms.

Plugin 0.0.77, launcher 0.2.29 and both optional flags remain.

Operator checks: put an MP4 (and a WebM) in the audio folder or drop it; the
chart generates, the video plays as BGA in sync with the music, and the result
returns without a score. Repeat with BGA display off (no video, music only).
Repeat ordinary play/IR and the pending BGA/EQ/function-key checks. Codex does
not launch the client or launcher for these checks.

Distribution requires exact-source Windows/macOS bodies and matching source
ZIPs, core/release/parity and plugin checks, final CI, signed history/artifact
audit, additive private gates, zero Arena use, rollback, guarded reloads,
health/WSS, exact live metadata, and a deployment marker before completion.
