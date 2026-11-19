import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from downloader import classify_url, add_links, jobs, pending, queue_lock


class URLValidationTests(unittest.TestCase):
    def test_supported_platforms(self):
        self.assertEqual(classify_url("https://www.facebook.com/watch/?v=123"), "facebook")
        self.assertEqual(classify_url("https://youtu.be/abcd"), "youtube")
        self.assertEqual(classify_url("https://www.tiktok.com/@user/video/123"), "tiktok")

    def test_rejects_unsafe_hosts(self):
        for url in ["http://youtube.com/watch?v=x", "https://youtube.com.evil.test/x",
                    "https://127.0.0.1/a", "https://user:pass@youtube.com/x",
                    "https://youtube.com:8443/x", "file:///etc/passwd"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                classify_url(url)

    def test_all_or_nothing_validation(self):
        with self.assertRaises(ValueError):
            add_links(["https://youtu.be/abcd", "https://evil.example/video"], Path("/tmp"))
        self.assertFalse(pending)


if __name__ == "__main__":
    unittest.main()
