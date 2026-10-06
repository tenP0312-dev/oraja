#!/usr/bin/env python3
"""Estimate a constant BPM and the first beat position of an audio file.

Usage:
    python estimate_audio_grid.py song.mp3 [--min-bpm 70] [--max-bpm 200] [--json]

Decoding is delegated to ffmpeg (which also removes MP3 encoder delay when the
file carries a LAME/Xing header). Only numpy is required.

The result is meant to seed a fixed-grid chart: ``first_beat_sec`` is the audio
time of a beat, ``bpm`` is a constant tempo. ``stability`` reports how far the
fitted grid drifts from the music, so callers can reject songs with tempo
changes or free tempo.
"""

import argparse
import json
import subprocess
import sys

import numpy as np

SAMPLE_RATE = 22050
HOP = 128
WINDOW = 1024
FRAME_RATE = SAMPLE_RATE / HOP
# Measured with synthetic kick/snare/hat/click/pluck trains: an onset peaks in
# the envelope ~35 ms *after* the start of the frame that first sees it.
ONSET_LAG_SEC = 0.035
COARSE_SPAN_SEC = 40.0
DOUBLE_TIME_RATIO = 0.8
# Log-spectral flux inflates room noise, so the first-sound threshold has to
# sit well above it.
FIRST_SOUND_RATIO = 0.3
SUSTAIN_SEC = 2.0
STABLE_DRIFT_MS = 30.0
# (subdivision, weight): beats, plus 8th notes at half weight. Chosen on 26
# constant-BPM BMS renders: exact BPM 19 -> 22 of 26. Adding 16ths, or
# weighting the low band, did not help there.
GRID_LEVELS = ((1, 1.0), (2, 0.5))


def decode_mono(path, sample_rate=SAMPLE_RATE):
    cmd = [
        "ffmpeg", "-v", "error", "-i", path,
        "-ac", "1", "-ar", str(sample_rate), "-f", "f32le", "-",
    ]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0:
        raise RuntimeError("ffmpeg failed: " + proc.stderr.decode("utf-8", "replace").strip())
    samples = np.frombuffer(proc.stdout, dtype="<f4")
    if samples.size < sample_rate:
        raise RuntimeError("audio is shorter than one second")
    if np.abs(samples).max() < 1e-4:
        raise RuntimeError("audio is silent")
    return samples


def onset_envelope(samples):
    """Half-wave rectified log-spectral flux, one value per HOP samples."""
    window = np.hanning(WINDOW).astype(np.float32)
    n_frames = 1 + (samples.size - WINDOW) // HOP
    idx = np.arange(WINDOW)[None, :] + HOP * np.arange(n_frames)[:, None]
    mag = np.empty((n_frames, WINDOW // 2 + 1), dtype=np.float32)
    # chunked to bound memory on long songs
    for start in range(0, n_frames, 4096):
        frames = samples[idx[start:start + 4096]] * window
        mag[start:start + 4096] = np.abs(np.fft.rfft(frames, axis=1))
    freqs = np.fft.rfftfreq(WINDOW, 1.0 / SAMPLE_RATE)
    mag = mag[:, (freqs >= 30) & (freqs <= 8000)]
    logmag = np.log1p(100.0 * mag)
    flux = np.maximum(np.diff(logmag, axis=0, prepend=logmag[:1]), 0).sum(axis=1)
    # remove slow trends so quiet and loud passages weigh comparably
    kernel = np.ones(int(FRAME_RATE * 0.5)) / int(FRAME_RATE * 0.5)
    local = np.convolve(flux, kernel, mode="same")
    env = np.maximum(flux - local, 0)
    # a frame-wide smoothing keeps sub-frame phase information usable
    env = np.convolve(env, np.array([0.25, 0.5, 0.25]), mode="same")
    return env


def interp(env, positions):
    positions = np.clip(positions, 0, env.size - 1.001)
    base = positions.astype(np.int64)
    frac = positions - base
    return env[base] * (1 - frac) + env[base + 1] * frac


def grid_score(env, bpm, phases, span=None):
    """Mean onset strength on the beat grid for each phase offset (frames).

    The 8th-note grid is scored too, at half weight: a 2:3 or 3:4 wrong tempo
    can land every beat on a real hit, but its subdivisions miss the music.
    """
    period = FRAME_RATE * 60.0 / bpm
    n_beats = int((env.size - 2 - period) // period)
    if span is not None:
        n_beats = min(n_beats, span)
    scores = np.zeros(phases.size)
    for level, weight in GRID_LEVELS:
        points = np.arange(n_beats * level) * (period / level)
        for i, phase in enumerate(phases):
            scores[i] += weight * interp(env, points + phase).mean()
    return scores, period


def best_phase(env, bpm, n_phase=48):
    period = FRAME_RATE * 60.0 / bpm
    phases = np.linspace(0, period, n_phase, endpoint=False)
    scores, _ = grid_score(env, bpm, phases)
    top = int(np.argmax(scores))
    return phases[top], scores[top]


def refine_phase(env, bpm, coarse_phase):
    period = FRAME_RATE * 60.0 / bpm
    phases = coarse_phase + np.linspace(-0.6, 0.6, 25) * (period / 48 * 2)
    phases = np.maximum(phases, 0)
    scores, _ = grid_score(env, bpm, phases)
    top = int(np.argmax(scores))
    if 0 < top < scores.size - 1:  # parabolic peak
        a, b, c = scores[top - 1], scores[top], scores[top + 1]
        denom = a - 2 * b + c
        shift = 0.5 * (a - c) / denom if denom != 0 else 0.0
        step = phases[1] - phases[0]
        return phases[top] + shift * step, b
    return phases[top], scores[top]


def loudest_segment(env, span_frames):
    if env.size <= span_frames:
        return env
    csum = np.concatenate([[0.0], np.cumsum(env)])
    sums = csum[span_frames:] - csum[:-span_frames]
    start = int(np.argmax(sums))
    return env[start:start + span_frames]


def estimate(env, min_bpm, max_bpm):
    baseline = env.mean() + 1e-9
    # Coarse pass on the busiest stretch only: over a whole song a 0.1 BPM step
    # would already smear the comb peak, so the step has to match the span.
    segment = loudest_segment(env, int(COARSE_SPAN_SEC * FRAME_RATE))
    bpms = np.arange(min_bpm, max_bpm + 0.05, 0.1)
    coarse = np.empty(bpms.size)
    for i, bpm in enumerate(bpms):
        coarse[i] = best_phase(segment, bpm, n_phase=32)[1]
    # weak log-tempo prior: nudge toward the usual 100-190 range, which also
    # breaks half/double ties; it is not strong enough to override clear evidence
    prior = np.exp(-0.5 * (np.log2(bpms / 140.0) / 0.9) ** 2)
    ranked = coarse * (0.85 + 0.15 * prior)
    peaks = [i for i in range(1, bpms.size - 1)
             if ranked[i] >= ranked[i - 1] and ranked[i] >= ranked[i + 1]]
    peaks.sort(key=lambda i: -ranked[i])
    candidates = []
    for i in peaks[:6]:
        lo, hi = bpms[i] - 0.3, bpms[i] + 0.3
        fine_bpms = np.arange(lo, hi, 0.01)
        fine = np.array([best_phase(env, b, n_phase=64)[1] for b in fine_bpms])
        j = int(np.argmax(fine))
        bpm = float(fine_bpms[j])
        phase, score = refine_phase(env, bpm, best_phase(env, bpm, 96)[0])
        candidates.append({
            "bpm": bpm,
            "phase_frames": float(phase),
            "score": float(score / baseline),
            "ranked": float(ranked[i]),
        })
    candidates.sort(key=lambda c: -c["ranked"])
    return prefer_double_time(candidates, max_bpm)


def prefer_double_time(candidates, max_bpm):
    """Half-time is a standing ambiguity: a comb on every other beat often
    scores a little higher because it lands only on the strongest hits. If the
    doubled tempo is also a strong candidate, take it."""
    best = candidates[0]
    for other in candidates[1:]:
        if (abs(other["bpm"] / best["bpm"] - 2.0) < 0.005
                and other["bpm"] <= max_bpm
                and other["score"] >= DOUBLE_TIME_RATIO * best["score"]):
            candidates.remove(other)
            candidates.insert(0, other)
            break
    unique = []
    for c in candidates:
        if all(abs(c["bpm"] - u["bpm"]) > 0.05 for u in unique):
            unique.append(c)
    return unique


def stability(env, bpm, phase, window_sec=15.0):
    """Largest deviation (ms) of local beat phase from the global grid."""
    period = FRAME_RATE * 60.0 / bpm
    win = int(window_sec * FRAME_RATE)
    devs = []
    for start in range(0, env.size - win, win // 2):
        seg = env[start:start + win]
        if seg.mean() < env.mean() * 0.3:
            continue
        # global grid expressed in this segment
        local_phase = (phase - start) % period
        offsets = np.linspace(-0.5, 0.5, 41) * period * 0.5
        beats = np.arange(int(win // period) - 1)[None, :] * period
        scores = np.array([interp(seg, beats + local_phase + o).mean() for o in offsets])
        devs.append(offsets[int(np.argmax(scores))] / FRAME_RATE * 1000.0)
    if not devs:
        return 0.0
    return float(max(abs(d) for d in devs))


def first_sustained_sound(env, threshold):
    """Start of the music: the first onset burst followed by another within
    SUSTAIN_SEC. A lone burst is ignored; decoders that keep the encoder delay
    produce one where digital silence steps into the track's noise floor."""
    loud = env > threshold
    bursts = np.nonzero(loud & ~np.concatenate([[False], loud[:-1]]))[0]
    window = int(SUSTAIN_SEC * FRAME_RATE)
    for current, following in zip(bursts, bursts[1:]):
        if following - current <= window:
            return float(current / FRAME_RATE)
    return float(bursts[0] / FRAME_RATE) if bursts.size else 0.0


def analyze(path, min_bpm=70.0, max_bpm=200.0):
    samples = decode_mono(path)
    env = onset_envelope(samples)
    candidates = estimate(env, min_bpm, max_bpm)
    best = candidates[0]
    bpm = best["bpm"]
    period_sec = 60.0 / bpm
    phase_sec = best["phase_frames"] / FRAME_RATE + ONSET_LAG_SEC
    first_sound = first_sustained_sound(env, np.percentile(env, 99) * FIRST_SOUND_RATIO)
    # earliest grid beat that is not before the music starts (with one beat of slack)
    k = int(np.ceil((first_sound - period_sec * 0.5 - phase_sec) / period_sec))
    first_beat = phase_sec + max(k, int(np.ceil(-phase_sec / period_sec))) * period_sec
    drift = stability(env, bpm, best["phase_frames"])
    return {
        "bpm": round(bpm, 3),
        "first_beat_sec": round(first_beat, 4),
        "phase_sec": round(phase_sec % period_sec, 4),
        "duration_sec": round(samples.size / SAMPLE_RATE, 2),
        "confidence": round(best["score"], 2),
        "max_drift_ms": round(drift, 1),
        "stable_tempo": drift <= STABLE_DRIFT_MS,
        "alternatives": [
            {"bpm": round(c["bpm"], 3), "confidence": round(c["score"], 2)}
            for c in candidates[1:4]
        ],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("audio")
    parser.add_argument("--min-bpm", type=float, default=70.0)
    parser.add_argument("--max-bpm", type=float, default=200.0)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    result = analyze(args.audio, args.min_bpm, args.max_bpm)
    if args.json:
        print(json.dumps(result, ensure_ascii=False))
    else:
        for key, value in result.items():
            print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
