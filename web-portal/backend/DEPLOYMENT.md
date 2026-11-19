# Expert Video Portal — deployment preparation (not yet production approved)

## Local Docker smoke test

1. Install Docker Desktop or Docker Engine with Compose.
2. Copy `backend/.env.example` to `backend/.env`. Replace all placeholders. Generate a long random `PORTAL_ADMIN_TOKEN`. **Never commit or share real credentials in chat.**
3. From `web-portal/`, run `docker compose up --build`.
4. Confirm `http://127.0.0.1:8000/api/health`. Open the frontend `index.html` locally for testing.
5. Test only your own/authorized video URLs and inspect video streams with ffprobe.
6. Stop with `docker compose down` (do not use `-v` unless intentionally deleting stored files and share tokens).

## Cloudflare R2
- Create a **private** R2 bucket.
- Create R2 S3 credentials scoped to that bucket only.
- Set `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`.
- Set `PORTAL_AUTO_R2_UPLOAD=true` only after a successful private test upload.
- R2 is storage, not application hosting.

## Public hosting prerequisites
- Use a VPS or container host with persistent storage, HTTPS, domain, backups, and secret management.
- Place the backend behind a TLS reverse proxy; do not publish the container port directly.
- Set a domain and use the backend's `/watch/{token}` pages for unlisted sharing.
- **Do not expose admin dashboard publicly yet.** The current frontend is a local prototype using a manually entered bearer token, not a hardened admin login.
- Restrict public endpoints with rate limits and abuse protections.
- Public sharing tokens act as access secrets; anyone holding a link can watch/download. Revocation prevents new redirects but cannot revoke an already-issued short-lived signed URL.
- The database is SQLite on the persistent volume; backups are required.
- Downloader jobs are in-memory; restarts lose queue state. Only run one backend worker. Use a persistent job queue before scaling.
- YouTube/Facebook/TikTok availability varies; no bypass of DRM, account restrictions, or copyright permissions.
- MP4/H.264 stream selection can fail on some videos. TikTok visible watermark removal, APK parity and synthetic USA/iPhone metadata are not yet implemented.
- Do not call this production ready until automated tests, real authorized-media tests, security review and mobile playback verification pass.
