# Gameplay Timing Diagnostics

Arena oraja `0.4.14.54` adds an opt-in timing log that separates render, input
dispatch, BGA, audio output and runtime pressure without changing their
scheduling or fallback behavior. It is disabled by default.

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
The event queue drops entries instead of blocking gameplay.

The log rotates at 2 MiB and retains five backups. It does not include chart
paths, account credentials, chat text or Arena wire payloads. Disable the
setting and restart after the capture is complete.

## Metric meaning

All timing distributions contain `count`, `average_us`, `p50_us`, `p95_us`,
`p99_us` and `max_us`. Percentiles use fixed upper-bound buckets, so they are
intentionally approximate.

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

`audio_config` records the selected backend and, for PortAudio/ASIO, the actual
sample rate, frames per buffer and the theoretical duration of one buffer.
That duration is not an end-to-end latency claim. The default OpenAL backend
does not use the PortAudio `deviceBufferSize` setting.

The summary counters include PortAudio underflows, write errors, rejected
enqueues, BGA decoder/texture errors and skipped uploads. Runtime gauges include
heap usage, GC deltas, active movie decoders, retained in-memory movie bytes,
and current/maximum pending BGA uploads.

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

These diagnostics collect evidence only. They do not change BGA fallback,
input polling, judgement, keysound scheduling, audio buffering or thread
priorities.
