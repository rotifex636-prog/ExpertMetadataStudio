import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sharing import create_share, lookup_share, revoke_share

class SharingTests(unittest.TestCase):
    def test_share_persists_and_can_be_revoked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = "processed/" + "a"*32 + "/cleaned.mp4"
            token = create_share(root, "a"*32, key)
            self.assertEqual(lookup_share(root, token)["key"], key)
            self.assertTrue(revoke_share(root, token))
            self.assertIsNone(lookup_share(root, token))

    def test_random_or_malformed_tokens_not_found(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertIsNone(lookup_share(Path(directory), "../private"))
            self.assertIsNone(lookup_share(Path(directory), "a"*43))

if __name__ == "__main__":
    unittest.main()
