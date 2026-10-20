"""Real WAV offline scoring regression fixtures are SYNTHETIC, NOT owner proof."""
import array
import hashlib
import json
import math
import struct
import tempfile
import unittest
import wave
from pathlib import Path
from tools import score_owner_real_wav as scoring

MODEL = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"


def wav(folder, name, *, duration=3.0, freq=440, silent=False):
    file = folder / name
    file.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(file), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        values = array.array("h", (0 if silent else int(2000 * math.sin(
            2 * math.pi * freq * k / 16000)) for k in range(int(duration * 16000))))
        if struct.pack("=H", 1) == b"\x00\x01":
            values.byteswap()
        w.writeframes(values.tobytes())
    return {"file": name, "sha256": hashlib.sha256(file.read_bytes()).hexdigest()}


def manifest(folder):
    enroll = [wav(folder, f"enroll/{i}.wav", freq=300 + i * 70) for i in range(3)]
    probe = wav(folder, "probe/owner.wav", freq=380)
    replay_audio = wav(folder, "probe/replay.wav", freq=510)
    source_audio = wav(folder, "replay/source.wav", freq=420)
    entries = [
        dict(probe, recording_sha256=probe["sha256"], trial_id="t001",
             split="calibration", kind="genuine", speaker_id="owner",
             session_id="genuine_session_1", consent=True),
        dict(replay_audio, recording_sha256=replay_audio["sha256"], trial_id="t002",
             split="holdout", kind="replay", speaker_id="owner",
             session_id="replay_session_1", consent=True,
             replay_source_file=source_audio["file"],
             replay_source_sha256=source_audio["sha256"]),
    ]
    for row in entries:
        row.pop("sha256", None)
    return {"schema": "jarvis.speaker-wav.v1", "enrollment": enroll, "trials": entries}


class RealOwnerWavScoringTests(unittest.TestCase):
    def test_unverified_model_and_empty_corpus_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "fake.onnx"
            file.write_bytes(b"not the pinned native model")
            with self.assertRaisesRegex(ValueError, "(?i)model|sha"):
                scoring.verify_model(file)
            with self.assertRaises(ValueError):
                scoring.build_scored_rows({"schema": "jarvis.speaker-wav.v1",
                                           "enrollment": [], "trials": []},
                                          root, lambda pcm: [1.] * 64)

    def test_real_wav_validation_rejects_corrupted_tampered_silent_and_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            f = wav(root, "probe.wav")
            audio = scoring.load_checked_pcm(root, f["file"], f["sha256"], 40000, 160000)
            self.assertEqual(48000, len(audio))
            self.assertTrue(max(abs(x) for x in audio) > .001)
            with self.assertRaisesRegex(ValueError, "(?i)hash|sha"):
                scoring.load_checked_pcm(root, f["file"], "0" * 64, 40000, 160000)
            wav(root, "silence.wav", silent=True)
            silence_hash = hashlib.sha256((root/"silence.wav").read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "(?i)silent|signal"):
                scoring.load_checked_pcm(root, "silence.wav", silence_hash, 40000, 160000)
            (root / "link.wav").symlink_to(root / "probe.wav")
            with self.assertRaisesRegex(ValueError, "(?i)symlink|link"):
                scoring.load_checked_pcm(root, "link.wav", f["sha256"], 40000, 160000)
            with self.assertRaises(ValueError):
                scoring.load_checked_pcm(root, "../probe.wav", f["sha256"], 40000, 160000)

    def test_template_matches_android_normalize_average_normalize_and_only_scores(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            m = manifest(root)
            calls = []
            def fake_embed(pcm):
                calls.append(len(pcm))
                vector = [0.0] * 64
                vector[0] = 0.8
                vector[1] = 0.6
                return vector
            rows = scoring.build_scored_rows(m, root, fake_embed)
            self.assertEqual(5, len(calls))
            self.assertEqual(2, len(rows))
            self.assertAlmostEqual(1.0, rows[0]["score"])
            self.assertEqual(MODEL, rows[0]["model_sha256"])
            self.assertEqual(rows[0]["enrollment_sha256"], rows[1]["enrollment_sha256"])
            self.assertEqual(m["trials"][1]["replay_source_sha256"],
                             rows[1]["replay_source_sha256"])
            for row in rows:
                self.assertNotIn("file", row)
                self.assertNotIn("raw_audio", row)
                self.assertNotIn("embedding", row)
                self.assertNotIn("authorization", row)

    def test_replay_source_must_be_proven_locally_and_enrollment_is_independent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            m = manifest(root)
            m["trials"][1]["replay_source_sha256"] = "0" * 64
            with self.assertRaisesRegex(ValueError, "(?i)hash|sha"):
                scoring.build_scored_rows(m, root, lambda pcm: [1.] * 64)
            m = manifest(root)
            m["trials"][0]["recording_sha256"] = m["enrollment"][0]["sha256"]
            with self.assertRaisesRegex(ValueError, "(?i)hash|sha"):
                scoring.build_scored_rows(m, root, lambda pcm: [1.] * 64)

    def test_enrollment_vectors_fail_closed_on_inconsistent_or_invalid_embeddings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            m = manifest(root)
            counter = 0
            def inconsistent(pcm):
                nonlocal counter
                counter += 1
                return [1.0 if k == counter else 0.0 for k in range(64)]
            with self.assertRaisesRegex(ValueError, "(?i)consistent|enroll"):
                scoring.build_scored_rows(m, root, inconsistent)
            with self.assertRaises(ValueError):
                scoring.build_scored_rows(m, root, lambda pcm: [math.nan] * 64)

    def test_wav_decoder_reads_only_the_verified_in_memory_bytes(self):
        # The bytes whose SHA was checked must also be the bytes decoded,
        # rather than re-opening a possibly replaced filesystem path.
        import io
        from unittest import mock
        original = wave.open
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            audio = wav(root, "stable.wav")
            kinds = []
            def guarded_open(src, mode="rb"):
                kinds.append(type(src))
                if not isinstance(src, io.BytesIO):
                    raise ValueError("Unverified second WAV filesystem read")
                return original(src, mode)
            with mock.patch.object(scoring.wave, "open", side_effect=guarded_open):
                pcm = scoring.load_checked_pcm(root, audio["file"], audio["sha256"],
                                               40000, 160000)
            self.assertEqual(48000, len(pcm))
            self.assertEqual([io.BytesIO], kinds)

    def test_private_jsonl_output_is_exclusive_and_private(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)/"scored.jsonl"
            scoring.write_private_jsonl(output, [{"trial_id": "x", "score": .5}])
            self.assertEqual(0o600, output.stat().st_mode & 0o777)
            self.assertEqual("x", json.loads(output.read_text())["trial_id"])
            with self.assertRaises(FileExistsError):
                scoring.write_private_jsonl(output, [])
            

if __name__ == "__main__":
    unittest.main()
