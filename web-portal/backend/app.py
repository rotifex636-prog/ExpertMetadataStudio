"""Expert Video Portal: first local backend milestone.
Requires installed ffmpeg and ffprobe. This is NOT deployed and does not download URLs.
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

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="Expert Video Portal API", version="0.1.0")
app.add_middleware(CORSMiddleware, allow_origins=["null", "http://127.0.0.1:5500", "http://localhost:5500"], allow_credentials=False, allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"])
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
