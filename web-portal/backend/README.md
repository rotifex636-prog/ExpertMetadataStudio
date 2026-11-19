# Expert Video Portal — Backend milestone M1

This is a **local development prototype**, not a deployed or production-ready service.

## Requirements
- Python 3.11+
- FFmpeg and FFprobe available on PATH

## Start
```bash
cd web-portal/backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export PORTAL_ADMIN_TOKEN='replace-with-a-long-random-secret'
uvicorn app:app --host 127.0.0.1 --port 8000
```
On Windows, use the appropriate venv activation and environment variable syntax.

## Test upload
```bash
curl -H "Authorization: Bearer $PORTAL_ADMIN_TOKEN" -F "file=@test.mp4" http://127.0.0.1:8000/api/jobs/upload
```
Use the returned job ID with GET /api/jobs/{id}, /report and /download (all require the Bearer token). Open /docs for interactive API documentation.

## Limitations
- Upload processing is synchronous and blocking; persistent database and background queue are not implemented.
- Jobs are held in memory; output files remain on local disk without lifecycle cleanup.
- The metadata cleaner uses FFmpeg stream-copy and removes common container metadata. It does **not** guarantee APK parity, all metadata removal, synthetic USA GPS or iPhone model metadata.
- VP9 video and unsupported audio formats are rejected rather than transcoded or mislabeled.
- The existing frontend is not wired to this API.
- No Facebook/YouTube/TikTok downloader, visible watermark remover, R2 upload, admin login page or public video pages yet.
- Do not expose this development service publicly until authentication, file safety, resource controls and deployment security have been reviewed.
