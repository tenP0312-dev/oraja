import shutil
import tempfile
import unittest
import wave
from pathlib import Path

try:
    import numpy as np
    import estimate_audio_grid
except ImportError:  # numpy is an optional dependency of the tool
    np = None
    estimate_audio_grid = None

SAMPLE_RATE = 44100


def synth_track(bpm, offset, duration, seed, bpm_after_half=None, amplitude=1.0):
    """Kick/snare/hat pattern at a known tempo, with room noise.

    Synthetic on purpose: the expected BPM and first beat are exact, and no
    third-party audio is committed.
    """
    rng = np.random.default_rng(seed)
    count = int(duration * SAMPLE_RATE)
    out = np.zeros(count, dtype=np.float64)
    t_kick = np.arange(int(0.25 * SAMPLE_RATE)) / SAMPLE_RATE
    kick = np.sin(2 * np.pi * np.cumsum(120 * np.exp(-t_kick * 25) + 45) / SAMPLE_RATE)
    kick *= np.exp(-t_kick * 14) * 0.9
    t_snare = np.arange(int(0.15 * SAMPLE_RATE)) / SAMPLE_RATE
    snare = rng.standard_normal(t_snare.size) * np.exp(-t_snare * 30) * 0.5
    hat = np.diff(rng.standard_normal(int(0.05 * SAMPLE_RATE) + 1))
    hat = hat * np.exp(-np.arange(hat.size) / SAMPLE_RATE * 90) * 0.4

    def add(time, sound):
        start = int(round(time * SAMPLE_RATE))
        if 0 <= start < count:
            end = min(count, start + sound.size)
            out[start:end] += sound[:end - start]

    beat_index = 0
    now = offset
    while now < duration:
        beat = 60.0 / (bpm_after_half if bpm_after_half and now > duration / 2 else bpm)
        if beat_index % 4 in (0, 2):
            add(now, kick)
        else:
            add(now, snare)
        add(now, hat)
        add(now + beat / 2, hat * 0.8)
        beat_index += 1
        now += beat
    out += rng.standard_normal(count) * 0.02
    out *= amplitude / (np.abs(out).max() * 1.1)
    return out


def write_wav(path, samples):
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes((samples * 32767).astype("<i2").tobytes())


def phase_error_ms(result, offset, bpm):
    """Distance from the true beat grid, ignoring whole-beat differences."""
    beat = 60.0 / bpm
    return abs(((result["first_beat_sec"] - offset + beat / 2) % beat) - beat / 2) * 1000


@unittest.skipIf(np is None or shutil.which("ffmpeg") is None, "numpy and ffmpeg are required")
class EstimateAudioGridTest(unittest.TestCase):
    def analyze(self, **kwargs):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "track.wav"
            write_wav(path, synth_track(**kwargs))
            return estimate_audio_grid.analyze(str(path))

    def test_constant_tempo_recovers_bpm_and_offset(self):
        for bpm, offset, seed in ((147.37, 0.337, 1), (128.0, 1.2, 2), (160.12, 0.8, 3)):
            with self.subTest(bpm=bpm):
                result = self.analyze(bpm=bpm, offset=offset, duration=45, seed=seed)
                self.assertAlmostEqual(result["bpm"], bpm, delta=0.05)
                self.assertLess(phase_error_ms(result, offset, bpm), 10.0)
                self.assertTrue(result["stable_tempo"])

    def test_octave_ambiguous_tempo_lists_true_bpm(self):
        # 8th-note hats make 93.5 and 187 equally plausible; the tool must pick
        # one but never lose the other, so a caller can offer both.
        result = self.analyze(bpm=93.5, offset=0.05, duration=45, seed=3)
        found = [result["bpm"]] + [a["bpm"] for a in result["alternatives"]]
        self.assertTrue(any(abs(b - 93.5) < 0.05 for b in found), found)

    def test_quiet_track(self):
        result = self.analyze(bpm=140.0, offset=0.4, duration=45, seed=4, amplitude=0.03)
        self.assertAlmostEqual(result["bpm"], 140.0, delta=0.05)
        self.assertLess(phase_error_ms(result, 0.4, 140.0), 10.0)

    def test_first_beat_skips_silent_intro(self):
        result = self.analyze(bpm=150.0, offset=5.0, duration=45, seed=5)
        self.assertAlmostEqual(result["bpm"], 150.0, delta=0.05)
        self.assertAlmostEqual(result["first_beat_sec"], 5.0, delta=0.02)

    def test_tempo_change_is_reported_unstable(self):
        result = self.analyze(bpm=120.0, offset=0.3, duration=90, seed=6, bpm_after_half=126.0)
        self.assertFalse(result["stable_tempo"])

    def test_rejects_silent_audio(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "silent.wav"
            write_wav(path, np.zeros(SAMPLE_RATE * 5))
            with self.assertRaisesRegex(RuntimeError, "silent"):
                estimate_audio_grid.analyze(str(path))

    def test_rejects_too_short_audio(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "short.wav"
            write_wav(path, synth_track(bpm=120.0, offset=0.0, duration=0.5, seed=7))
            with self.assertRaises(RuntimeError):
                estimate_audio_grid.analyze(str(path))


if __name__ == "__main__":
    unittest.main()
