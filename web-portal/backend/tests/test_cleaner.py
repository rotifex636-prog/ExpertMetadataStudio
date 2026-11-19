"""Offline regression tests for the first web cleaning backend.
Run: python -m unittest discover -s tests -v
"""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app import clean_video, inspect, authorize
from fastapi import HTTPException


def probe(streams, duration="10.0"):
    return {"streams": streams, "format": {"duration": duration, "size": "100"}}


class CleanerTests(unittest.TestCase):
    def test_h264_aac_stream_copy_and_metadata_flags(self):
        before = probe([
            {"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080},
            {"codec_type": "audio", "codec_name": "aac"},
        ])
        after = probe([
            {"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080},
            {"codec_type": "audio", "codec_name": "aac"},
        ])
        with tempfile.TemporaryDirectory() as directory:
            source, dest = Path(directory) / "source.mp4", Path(directory) / "cleaned.mp4"
            with patch("app.inspect", side_effect=[before, after]), patch("app.run_command") as run:
                report = clean_video(source, dest)
            self.assertTrue(report["stream_copy"])
            args = run.call_args.args[0]
            self.assertIn("-map_metadata", args)
            self.assertIn("-1", args)
            self.assertIn("-c", args)
            self.assertIn("copy", args)

    def test_vp9_rejected_without_transcoding(self):
        streams = probe([{"codec_type": "video", "codec_name": "vp9"}])
        with patch("app.inspect", return_value=streams), patch("app.run_command") as run:
            with self.assertRaisesRegex(RuntimeError, "not supported"):
                clean_video(Path("vp9.mp4"), Path("out.mp4"))
            run.assert_not_called()

    def test_changed_streams_fail_verification(self):
        before = probe([{"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080}])
        after = probe([{"codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720}])
        with tempfile.TemporaryDirectory() as directory:
            dest = Path(directory) / "out.mp4"
            dest.write_bytes(b"mock")
            with patch("app.inspect", side_effect=[before, after]), patch("app.run_command"):
                with self.assertRaisesRegex(RuntimeError, "verification failed"):
                    clean_video(Path("in.mp4"), dest)
            self.assertFalse(dest.exists())

    def test_missing_admin_token_fails_closed(self):
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(HTTPException) as raised:
                authorize("Bearer fake")
        self.assertEqual(raised.exception.status_code, 503)

    def test_incorrect_token_rejected(self):
        with patch.dict("os.environ", {"PORTAL_ADMIN_TOKEN": "correct"}):
            with self.assertRaises(HTTPException) as raised:
                authorize("Bearer incorrect")
        self.assertEqual(raised.exception.status_code, 403)


if __name__ == "__main__":
    unittest.main()
