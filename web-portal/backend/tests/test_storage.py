import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from storage import upload_cleaned, presigned_download, r2_client


class R2Tests(unittest.TestCase):
    def test_missing_credentials_fail_closed(self):
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "not configured"):
                r2_client()

    def test_upload_uses_private_object_and_mp4_content_type(self):
        client = MagicMock()
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "cleaned.mp4"
            file.write_bytes(b"sample")
            with patch("storage.r2_client", return_value=(client, "my-private-bucket")):
                result = upload_cleaned(file, "a" * 32)
        self.assertEqual(result["key"], "processed/" + "a" * 32 + "/cleaned.mp4")
        self.assertEqual(result["bytes"], 6)
        client.upload_file.assert_called_once()
        self.assertEqual(client.upload_file.call_args.kwargs["ExtraArgs"], {"ContentType": "video/mp4"})

    def test_presigned_url_expiry_is_bounded(self):
        client = MagicMock()
        client.generate_presigned_url.return_value = "https://example.invalid/signed"
        with patch("storage.r2_client", return_value=(client, "bucket")):
            result = presigned_download("processed/" + "b" * 32 + "/cleaned.mp4", 9000)
        self.assertEqual(result, "https://example.invalid/signed")
        self.assertEqual(client.generate_presigned_url.call_args.kwargs["ExpiresIn"], 3600)

    def test_rejects_untrusted_key(self):
        with self.assertRaises(ValueError):
            presigned_download("../secret.mp4")


if __name__ == "__main__":
    unittest.main()
