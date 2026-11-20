"""Offline API security checks: public links and admin-only endpoints."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fastapi.testclient import TestClient
import app as portal
from sharing import create_share


class PublicApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.client = TestClient(portal.app)

    def tearDown(self):
        self.temp.cleanup()

    def test_unknown_share_does_not_disclose_video(self):
        with patch.object(portal, "ROOT", self.root):
            response = self.client.get("/watch/" + "a" * 43)
            self.assertEqual(response.status_code, 404)
            response = self.client.get("/api/public/" + "a" * 43 + "/video")
            self.assertEqual(response.status_code, 404)

    def test_public_player_and_short_lived_r2_redirect(self):
        token = create_share(self.root, "a" * 32, "processed/" + "a" * 32 + "/cleaned.mp4")
        mock_client = MagicMock()
        mock_client.generate_presigned_url.return_value = "https://example.invalid/signed"
        with patch.object(portal, "ROOT", self.root), patch.object(portal, "r2_client", return_value=(mock_client, "bucket")):
            player = self.client.get("/watch/" + token)
            self.assertEqual(player.status_code, 200)
            self.assertIn("Download HD Video", player.text)
            stream = self.client.get("/api/public/" + token + "/video", follow_redirects=False)
            self.assertEqual(stream.status_code, 302)
            self.assertEqual(stream.headers["location"], "https://example.invalid/signed")
            self.assertEqual(mock_client.generate_presigned_url.call_args.kwargs["ExpiresIn"], 300)

    def test_browser_preflight_allows_share_revocation(self):
        response = self.client.options(
            "/api/shares/example",
            headers={
                "Origin": "http://localhost:5500",
                "Access-Control-Request-Method": "DELETE",
                "Access-Control-Request-Headers": "authorization",
            },
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("DELETE", response.headers.get("access-control-allow-methods", ""))

    def test_admin_only_share_creation(self):
        with patch.dict("os.environ", {"PORTAL_ADMIN_TOKEN": "test-token"}):
            response = self.client.post("/api/links/" + "a" * 32 + "/share")
            self.assertEqual(response.status_code, 401)
            response = self.client.post("/api/links/" + "a" * 32 + "/share",
                                        headers={"Authorization": "Bearer wrong"})
            self.assertEqual(response.status_code, 403)

    def test_revocation_disables_public_player(self):
        token = create_share(self.root, "a" * 32, "processed/" + "a" * 32 + "/cleaned.mp4")
        with patch.object(portal, "ROOT", self.root), patch.dict("os.environ", {"PORTAL_ADMIN_TOKEN": "test-token"}):
            response = self.client.delete("/api/shares/" + token, headers={"Authorization": "Bearer test-token"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(self.client.get("/watch/" + token).status_code, 404)


if __name__ == "__main__":
    unittest.main()
