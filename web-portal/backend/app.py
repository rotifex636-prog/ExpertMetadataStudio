"""Expert Video Portal: first local backend milestone.
Requires installed ffmpeg and ffprobe. This is not deployed; URL downloads require supported source access.
"""
from __future__ import annotations

import json
import os
import secrets
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path

from fastapi import FastAPI, File, Header, HTTPException, UploadFile, Request
from fastapi.responses import FileResponse, HTMLResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from downloader import add_links, get_job
from storage import upload_cleaned, presigned_download, r2_client
from sharing import create_share, lookup_share, revoke_share

app = FastAPI(title="Expert Video Portal API", version="0.1.0")
# Configure an exact HTTPS admin dashboard origin for hosted deployments.
# Wildcard origins are intentionally not supported for authenticated API calls.
allowed_origins = ["null", "http://127.0.0.1:5500", "http://localhost:5500"]
hosted_origin = os.getenv("PORTAL_ADMIN_ORIGIN", "").strip().rstrip("/")
if hosted_origin:
    from urllib.parse import urlsplit
    parsed_origin = urlsplit(hosted_origin)
    if (parsed_origin.scheme != "https" or not parsed_origin.hostname
            or parsed_origin.username or parsed_origin.password
            or parsed_origin.path or parsed_origin.query or parsed_origin.fragment):
        raise RuntimeError("PORTAL_ADMIN_ORIGIN must be a bare HTTPS origin")
    allowed_origins.append(hosted_origin)
app.add_middleware(CORSMiddleware, allow_origins=allowed_origins, allow_credentials=False,
                   allow_methods=["GET", "POST", "DELETE"],
                   allow_headers=["Authorization", "Content-Type"])
ROOT = Path(os.getenv("PORTAL_DATA_DIR", "./portal-data")).resolve()
MAX_BYTES = int(os.getenv("PORTAL_MAX_UPLOAD_BYTES", str(1024 * 1024 * 1024)))
SUPPORTED = {".mp4", ".mov", ".m4v", ".mkv"}
JOBS: dict[str, dict] = {}


def authorize(authorization: str | None) -> None:
    token = os.getenv("PORTAL_ADMIN_TOKEN")
    if not token:
        raise HTTPException(503, "Admin token not configured")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Bearer token required")
    if not secrets.compare_digest(authorization[7:], token):
        raise HTTPException(403, "Invalid admin token")


def run_command(args: list[str]) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=1800, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError("Media tool unavailable or timed out") from exc
    if result.returncode:
        raise RuntimeError((result.stderr or "Media processing failed")[-1000:])
    return result


def inspect(path: Path) -> dict:
    result = run_command([
        "ffprobe", "-v", "error", "-show_entries",
        "format=duration,size,format_name:stream=index,codec_name,codec_type,width,height",
        "-of", "json", str(path),
    ])
    return json.loads(result.stdout)


def clean_video(source: Path, destination: Path) -> dict:
    before = inspect(source)
    streams = before.get("streams", [])
    if not any(s.get("codec_type") == "video" for s in streams):
        raise RuntimeError("No video stream detected")
    # MP4 stream-copy supports only a subset of input codecs. Do not silently transcode.
    video_codecs = {s.get("codec_name") for s in streams if s.get("codec_type") == "video"}
    audio_codecs = {s.get("codec_name") for s in streams if s.get("codec_type") == "audio"}
    if not video_codecs.issubset({"h264", "hevc", "mpeg4", "av1"}) or not audio_codecs.issubset({"aac", "mp3", "alac", "ac3", "eac3"}):
        raise RuntimeError("Input codecs are not supported for MP4 stream-copy; no re-encoding performed")
    run_command([
        "ffmpeg", "-hide_banner", "-nostdin", "-y", "-i", str(source),
        "-map", "0:v?", "-map", "0:a?", "-c", "copy",
        "-map_metadata", "-1", "-map_chapters", "-1",
        "-metadata", "title=", "-metadata", "comment=",
        "-movflags", "+faststart", str(destination),
    ])
    after = inspect(destination)
    in_streams = [(s.get("codec_type"), s.get("codec_name"), s.get("width"), s.get("height")) for s in streams if s.get("codec_type") in ("video", "audio")]
    out_streams = [(s.get("codec_type"), s.get("codec_name"), s.get("width"), s.get("height")) for s in after.get("streams", []) if s.get("codec_type") in ("video", "audio")]
    if in_streams != out_streams:
        destination.unlink(missing_ok=True)
        raise RuntimeError("Output verification failed: stream characteristics changed")
    return {"before": before, "after": after, "stream_copy": True,
            "note": "Synthetic USA/iPhone metadata not yet implemented; metadata removal is not exhaustive."}


@app.get("/api/admin/verify")
def verify_admin(authorization: str | None = Header(default=None)):
    """Verify the supplied bearer token without returning any secret."""
    authorize(authorization)
    return {"authenticated": True, "role": "admin"}


@app.get("/api/health")
def health():
    return {"status": "ok", "stage": "local-backend-m1", "deployed": False}


@app.post("/api/jobs/upload")
async def upload_video(file: UploadFile = File(...), authorization: str | None = Header(default=None)):
    authorize(authorization)
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in SUPPORTED:
        raise HTTPException(415, "Unsupported file extension")
    job_id = uuid.uuid4().hex
    ROOT.mkdir(parents=True, exist_ok=True)
    folder = ROOT / job_id
    folder.mkdir(mode=0o700)
    source = folder / ("input" + suffix)
    destination = folder / "cleaned.mp4"
    size = 0
    try:
        with source.open("wb") as target:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > MAX_BYTES:
                    raise HTTPException(413, "File exceeds upload limit")
                target.write(chunk)
        if size == 0:
            raise HTTPException(400, "Empty upload")
        report = clean_video(source, destination)
        (folder / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        JOBS[job_id] = {"id": job_id, "status": "completed", "bytes": destination.stat().st_size,
                        "download_url": f"/api/jobs/{job_id}/download"}
        return JOBS[job_id]
    except HTTPException:
        shutil.rmtree(folder, ignore_errors=True)
        raise
    except Exception as exc:
        shutil.rmtree(folder, ignore_errors=True)
        raise HTTPException(422, str(exc)) from exc
    finally:
        await file.close()


@app.get("/api/jobs/{job_id}")
def job_status(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(404, "Job not found in current server process")
    return job


@app.get("/api/jobs/{job_id}/download")
def download(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    if job_id not in JOBS:
        raise HTTPException(404, "Job not found")
    path = ROOT / job_id / "cleaned.mp4"
    if not path.is_file():
        raise HTTPException(404, "Output missing")
    return FileResponse(path, media_type="video/mp4", filename="cleaned.mp4")


@app.get("/api/jobs/{job_id}/report")
def report(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    if job_id not in JOBS:
        raise HTTPException(404, "Job not found")
    path = ROOT / job_id / "report.json"
    if not path.is_file():
        raise HTTPException(404, "Report missing")
    return json.loads(path.read_text(encoding="utf-8"))


class LinkBatch(BaseModel):
    urls: list[str]


@app.post("/api/links")
def submit_links(payload: LinkBatch, authorization: str | None = Header(default=None)):
    authorize(authorization)
    try:
        return {"jobs": add_links(payload.urls, ROOT)}
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.get("/api/links/{job_id}")
def link_status(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    job = get_job(job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    return job


@app.get("/api/links/{job_id}/download")
def link_download(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    job = get_job(job_id)
    if not job or job["status"] != "completed":
        raise HTTPException(404, "Completed job not found")
    path = ROOT / "links" / job_id / "cleaned.mp4"
    if not path.is_file():
        raise HTTPException(404, "Output missing")
    return FileResponse(path, media_type="video/mp4", filename="cleaned.mp4")


@app.post("/api/jobs/{job_id}/r2")
def upload_job_to_r2(job_id: str, authorization: str | None = Header(default=None)):
    """Admin-only manual cloud upload after local video cleaning."""
    authorize(authorization)
    if job_id not in JOBS or JOBS[job_id]["status"] != "completed":
        raise HTTPException(404, "Completed job not found")
    try:
        uploaded = upload_cleaned(ROOT / job_id / "cleaned.mp4", job_id)
    except (RuntimeError, OSError, ValueError) as exc:
        raise HTTPException(503, "R2 upload unavailable; verify server configuration") from exc
    JOBS[job_id]["r2_key"] = uploaded["key"]
    return {"id": job_id, "uploaded": True, "bytes": uploaded["bytes"]}


@app.post("/api/links/{job_id}/r2")
def upload_link_to_r2(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    job = get_job(job_id)
    if not job or job["status"] != "completed":
        raise HTTPException(404, "Completed job not found")
    try:
        uploaded = upload_cleaned(ROOT / "links" / job_id / "cleaned.mp4", job_id)
    except (RuntimeError, OSError, ValueError) as exc:
        raise HTTPException(503, "R2 upload unavailable: " + str(exc)) from exc
    return {"id": job_id, "uploaded": True, "bytes": uploaded["bytes"],
            "r2_key": uploaded["key"]}


@app.get("/api/jobs/{job_id}/r2-download")
def signed_r2_download(job_id: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    job = JOBS.get(job_id)
    if not job or not job.get("r2_key"):
        raise HTTPException(404, "Uploaded object not found")
    try:
        return {"url": presigned_download(job["r2_key"]), "expires_in_seconds": 900}
    except (RuntimeError, ValueError) as exc:
        raise HTTPException(503, "R2 unavailable") from exc


@app.post("/api/links/{job_id}/share")
def create_link_share(job_id: str, authorization: str | None = Header(default=None)):
    """Create a public, unlisted link only after successful cloud upload."""
    authorize(authorization)
    job = get_job(job_id)
    if not job or job["status"] != "completed":
        raise HTTPException(404, "Completed job not found")
    # Check object availability in R2 before issuing a share link.
    key = "processed/" + job_id + "/cleaned.mp4"
    try:
        client, bucket = r2_client()
        client.head_object(Bucket=bucket, Key=key)
    except Exception as exc:
        raise HTTPException(409, "Video not available in R2") from exc
    token = create_share(ROOT, job_id, key)
    return {"share_url": f"/watch/{token}", "token": token}


@app.delete("/api/shares/{token}")
def delete_share(token: str, authorization: str | None = Header(default=None)):
    authorize(authorization)
    if not revoke_share(ROOT, token):
        raise HTTPException(404, "Share not found")
    return {"revoked": True}


WATCH_PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer"><title>Expert Video Portal | Watch Video</title>
<style>body{margin:0;background:#090b17;color:#f6f2ff;font-family:system-ui,sans-serif}
main{max-width:940px;margin:6vh auto;padding:24px}h1{color:#bd81ff}
video{width:100%;background:black;border:1px solid #44305b;border-radius:16px}
a{display:inline-block;background:#873be2;color:white;padding:14px 22px;border-radius:10px;
text-decoration:none;margin-top:18px}p{color:#b8b1c7}</style></head><body>
<main><h1>Expert Video Portal</h1><p>Shared HD video</p>
<video id="player" controls playsinline preload="metadata"></video>
<p><a id="download" download>Download HD Video</a></p></main>
<script>const token=location.pathname.split("/").pop();
const url="/api/public/"+encodeURIComponent(token)+"/video";
document.getElementById("player").src=url;
document.getElementById("download").href=url+"?download=1";</script></body></html>"""


@app.get("/watch/{token}", response_class=HTMLResponse)
def public_watch(token: str):
    if not lookup_share(ROOT, token):
        raise HTTPException(404, "Video not found")
    return HTMLResponse(WATCH_PAGE, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer",
                                            "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; media-src 'self' https:; connect-src 'self'; img-src 'self'; base-uri 'none'; frame-ancestors 'none'"})


@app.get("/api/public/{token}/video")
def public_video(token: str, download: bool = False):
    record = lookup_share(ROOT, token)
    if not record:
        raise HTTPException(404, "Video not found")
    try:
        # The signed URL is short-lived. The R2 object stays private.
        client, bucket = r2_client()
        params = {"Bucket": bucket, "Key": record["key"]}
        if download:
            params["ResponseContentDisposition"] = 'attachment; filename="video.mp4"'
        else:
            params["ResponseContentDisposition"] = 'inline; filename="video.mp4"'
        signed = client.generate_presigned_url("get_object", Params=params, ExpiresIn=300)
    except Exception as exc:
        raise HTTPException(503, "Video temporarily unavailable") from exc
    return RedirectResponse(signed, status_code=302, headers={"Cache-Control": "no-store",
                                                               "Referrer-Policy": "no-referrer"})
