# Gameplay Timing Diagnostics

Arena oraja `0.4.14.54` added an opt-in timing log that separates render,
input dispatch, BGA, audio output and runtime pressure. The current development
source extends that evidence for gameplay-start stalls while keeping the
collector disabled by default.

## Enable and collect

With the client stopped, set the following top-level value in
`config_sys.json`, then restart the client:

```json
"timingDiagnostics": true
```

For a one-off command-line launch, the equivalent JVM property is:

```text
-Dbmsir.timingDiagnostics=true
```

The client writes newline-delimited JSON to `logs/bmsir-timing.log`. A summary
is written every 10 seconds and once during normal shutdown. File output,
rotation, JSON formatting and GC inspection run on a dedicated daemon thread;
render, input, decoder and audio threads only update bounded atomic counters.
The event queue drops entries instead of blocking gameplay. Exceptional
render-stall and PortAudio-underflow events are formatted at their observation
site but still use that bounded non-blocking queue; the writer performs the
file I/O.

The log rotates at 2 MiB and retains five backups. It does not include chart
paths, account credentials, chat text or Arena wire payloads. Disable the
setting and restart after the capture is complete.

## Metric meaning

All timing distributions contain `count`, `average_us`, `p50_us`, `p95_us`,
`p99_us`, `max_us`, and `max_at`. `max_at` is the UTC instant at which the
period's maximum was observed. Percentiles use fixed upper-bound buckets, so
they are intentionally approximate.

| Metric | What it measures | Important limitation |
| --- | --- | --- |
| `render_interval_us` | Time between render-loop entries | Includes frame limiting, VSync and OS scheduling |
| `render_duration_us` | Time spent in one client render pass | Does not include time waiting for the next frame |
| `input_poll_interval_us` | Cadence of `BMSPlayerInputProcessor.poll()` calls | Does not prove that GLFW or controller backend state refreshed at that cadence |
| `input_poll_duration_us` | Duration of one client input poll | Does not include device firmware or USB latency |
| `input_to_judge_dispatch_us` | Game-clock delay from a live key-state change to judge processing in play/practice | Approximates judge/keysound dispatch; it is not physical device-to-speaker latency |
| `bga_decode_us` | One FFmpeg image decode call | Codec work outside `grabImage()` may appear elsewhere |
| `bga_pixmap_lock_us` | Decoder wait for the shared Pixmap | High values indicate decoder/render contention |
| `bga_pixmap_copy_us` | Pixmap allocation/copy time | Includes a resize allocation when dimensions change |
| `bga_render_queue_us` | Delay from decoded Pixmap completion to render runnable start | High values indicate a delayed render thread or queued uploads |
| `bga_texture_lock_us` | Render wait for the shared Pixmap | High values indicate decoder/render contention |
| `bga_texture_upload_us` | Pixmap preparation and texture update/create | Measures the render-thread upload call, not later GPU execution |
| `openal_play_call_us` | Duration of the libGDX/OpenAL `Sound.play`/`loop` call | OpenAL does not expose this client's device-buffer or hardware latency |
| `portaudio_enqueue_us` | Time to reserve a mixer input | Includes contention on the mixer-input lock |
| `portaudio_enqueue_to_mix_us` | Delay until an enqueued sound first enters a mixed buffer | Does not include subsequent backend/device buffering |
| `portaudio_mix_us` | CPU time to build one output buffer | Compare with the configured buffer duration |
| `portaudio_write_us` | Time blocked in the PortAudio output write | Long writes or errors can identify backend/device stalls |
| `loudness_analysis_wait_us` | Elapsed preparation time from the first non-blocking loudness poll until completion, cancellation, or timeout | The render thread continues drawing while this timer is active |
| `bga_prepare_step_us` | One bounded render-thread static-BGA disposal/upload step | At most four old textures are disposed and one static texture is uploaded per step |
| `bga_prepare_total_us` | Wall-clock time for all bounded static-BGA preparation steps | Includes time between render frames; it is not continuous CPU time |

Every event and summary carries a process-local `session_id`, monotonic
`transition_id`, anonymized 12-character `chart_id`, and current state. Play
preparation distinguishes `LOADING_AUDIO`, `LOADING_BGA`, `READY`, `COUNTDOWN`,
and `ACTIVE_PLAY`; a normally completed/failed play session ends as `RESULT`.
The ID is a truncated chart hash, not a chart path.

A render pass over 16.67 ms emits `render_stall`. If it remains active for at
least 50 ms, the watchdog also emits one `render_stall_sample` containing the
render thread name and up to 16 stack frames. The watchdog never suspends the
render thread. A PortAudio underflow emits `portaudio_underflow` with
`queue_depth`, `frames`, `write_us`, and `writer_delay_us`; the latter is the
time from the previous write completion to the next write start, including
mixer work and scheduling delay.

`audio_config` records the selected backend and, for PortAudio/ASIO, the actual
sample rate, frames per buffer and the theoretical duration of one buffer.
That duration is not an end-to-end latency claim. The default OpenAL backend
does not use the PortAudio `deviceBufferSize` setting.

The summary counters include PortAudio underflows, write errors, rejected
enqueues, BGA decoder/texture errors and skipped uploads. Runtime gauges include
used/committed/max heap, direct-buffer usage, GC deltas, active movie decoders,
retained in-memory movie bytes, and current/maximum pending BGA uploads.

## Gameplay-start behavior in the current development source

The PRELOAD render path no longer invokes `System.gc()` and never waits on an
unfinished loudness-analysis `Future`. It polls the existing worker result and
keeps the loading screen responsive, retaining the 15-second cancellation and
fallback behavior.

Static BGA texture disposal and upload remain owned by the render thread, but
are split into bounded steps. READY/Arena readiness is not announced until
media loading, loudness handling, and that static-texture queue are complete.
Movie decoding and Pixmap transfer keep their existing worker/render ownership;
OpenGL texture work is not moved to a worker thread.

## Reading a capture

- A high `render_interval_us` tail with a low `render_duration_us` tail points
  toward frame limiting, VSync, event polling or OS scheduling rather than
  heavy rendering.
- A high `input_to_judge_dispatch_us` tail that tracks render intervals is
  evidence of render-coupled judge/keysound dispatch.
- Rising `max_pending_bga_uploads` and `bga_render_queue_us` identify BGA work
  waiting behind the render loop. Stable `retained_movie_bytes` and decoder
  counts argue against a leak; values that keep rising after songs are released
  justify a lifecycle investigation.
- PortAudio `mix_us` or `write_us` approaching/exceeding the configured buffer
  duration, underflows, or write errors identify an audio-path problem. OpenAL
  captures can only show call time, not backend/device latency.

The diagnostic switch itself does not change BGA fallback, input polling,
judgement, keysound scheduling, audio buffering, or thread priorities. The
gameplay-start changes above apply with diagnostics both OFF and ON.

Windows ASIO acceptance still requires same-machine before/after captures.
Use the matrix from [the tracking Issue](https://github.com/tenP0312-dev/oraja/issues/208):
initial play, retry, consecutive songs/course transitions, BGA OFF/static/video,
diagnostics OFF/ON, and 48 kHz at 128 and 256 frames. Source-level tests cannot
establish zero hardware underflows or distinguish every OS/driver safepoint.
