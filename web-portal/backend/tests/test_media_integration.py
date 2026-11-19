"""Real FFmpeg smoke test using locally generated synthetic media.

No copyrighted content or network access required.
"""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app import clean_video, inspect


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg tools required")
class RealMediaIntegrationTests(unittest.TestCase):
    def test_cleaning_preserves_h264_aac_streams(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "original.mp4"
            destination = Path(temp) / "cleaned.mp4"
            result = subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=blue:s=320x240:r=24:d=1",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=1",
                "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-metadata", "title=private-test-title",
                str(source),
            ], capture_output=True, text=True, timeout=60)
            if result.returncode:
                self.fail("Synthetic test video generation failed: " + result.stderr[-500:])
            report = clean_video(source, destination)
            self.assertTrue(destination.is_file())
            self.assertTrue(report["stream_copy"])
            streams = inspect(destination)["streams"]
            self.assertEqual([s["codec_name"] for s in streams], ["h264", "aac"])
            # Container metadata is stripped, but codec-level metadata is not
            # claimed to be exhaustively removed.
            metadata = subprocess.run([
                "ffprobe", "-v", "error", "-show_entries", "format_tags=title",
                "-of", "default=noprint_wrappers=1", str(destination),
            ], capture_output=True, text=True, timeout=20)
            self.assertNotIn("private-test-title", metadata.stdout)


if __name__ == "__main__":
    unittest.main()
