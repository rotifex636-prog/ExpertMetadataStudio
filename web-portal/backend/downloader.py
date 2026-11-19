"""Authorized social-video URL download queue, powered by yt-dlp.

This module does not bypass DRM or authentication. Source access and copyright
permissions are the operator's responsibility. Downloads remain private.
"""
from __future__ import annotations

import os
import subprocess
import socket
import threading
import uuid
from pathlib import Path
from urllib.parse import urlsplit

PLATFORMS = {
    "facebook": ("facebook.com", "fb.watch"),
    "youtube": ("youtube.com", "youtu.be"),
    "tiktok": ("tiktok.com",),
}
MAX_URLS = 20
MAX_QUEUE = 100
queue_lock = threading.Lock()
jobs: dict[str, dict] = {}
pending: list[str] = []
worker_running = False


def classify_url(raw: str) -> str:
    if len(raw) > 2048 or any(ord(c) < 32 for c in raw):
        raise ValueError("Invalid video URL")
    url = urlsplit(raw)
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.port not in (None, 443):
        raise ValueError("Only supported HTTPS video URLs are accepted")
    host = url.hostname.rstrip(".").lower()
    for platform, domains in PLATFORMS.items():
        if any(host == d or host.endswith("." + d) for d in domains):
            return platform
    raise ValueError("Unsupported platform")


def validate_public_host(raw: str) -> None:
    """Reject video-page hostnames resolving to loopback or private networks.

    This is a preflight check, not a complete redirect/DNS-rebinding sandbox.
    Production deployments must additionally enforce outbound egress controls.
    """
    import ipaddress
    host = urlsplit(raw).hostname
    if not host:
        raise ValueError("Missing hostname")
    try:
        addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ValueError("Video hostname could not be resolved") from exc
    for record in addresses:
        address = ipaddress.ip_address(record[4][0])
        if not address.is_global:
            raise ValueError("Video hostname resolves to a non-public IP")


def add_links(urls: list[str], root: Path) -> list[dict]:
    global worker_running
    if not urls or len(urls) > MAX_URLS:
        raise ValueError("Submit between 1 and 20 URLs")
    accepted = [(url, classify_url(url)) for url in urls]
    with queue_lock:
        if len(pending) + len(accepted) > MAX_QUEUE:
            raise ValueError("Queue is full")
        created = []
        for url, platform in accepted:
            job_id = uuid.uuid4().hex
            job = {"id": job_id, "platform": platform, "source_url": url,
                   "status": "queued", "error": None, "download_url": None}
            jobs[job_id] = job
            pending.append(job_id)
            created.append(job.copy())
        if not worker_running:
            worker_running = True
            threading.Thread(target=_worker, args=(root,), daemon=True).start()
    return created


def _worker(root: Path) -> None:
    global worker_running
    while True:
        with queue_lock:
            if not pending:
                worker_running = False
                return
            job_id = pending.pop(0)
            job = jobs[job_id]
            job["status"] = "processing"
        folder = root / "links" / job_id
        folder.mkdir(parents=True, exist_ok=True)
        try:
            validate_public_host(job["source_url"])
            # Restrict external tool to exact prevalidated URLs, with no shell.
            # Prefer H.264/AAC MP4; do not fall back to VP9/AV1 silently.
            cmd = [
                "yt-dlp", "--no-playlist", "--no-config",
                "--restrict-filenames", "--no-overwrites", "--no-cache-dir",
                "--max-filesize", os.getenv("PORTAL_MAX_DOWNLOAD_SIZE", "1G"),
                "--socket-timeout", "20", "--retries", "2",
                "--format", "bestvideo[vcodec^=avc1][ext=mp4]+bestaudio[acodec^=mp4a]/best[vcodec^=avc1][ext=mp4]",
                "--merge-output-format", "mp4",
                "--output", str(folder / "source.%(ext)s"),
                job["source_url"],
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=1800, check=False)
            if result.returncode:
                raise RuntimeError((result.stderr or "Downloader failed")[-500:])
            candidates = list(folder.glob("source.*"))
            if len(candidates) != 1 or candidates[0].suffix.lower() != ".mp4":
                raise RuntimeError("No compatible MP4 output; no codec fallback allowed")
            max_bytes = int(os.getenv("PORTAL_MAX_UPLOAD_BYTES", str(1024 * 1024 * 1024)))
            if candidates[0].stat().st_size > max_bytes:
                raise RuntimeError("Downloaded file exceeds processing size limit")
            # Import lazily to avoid circular dependency.
            from app import clean_video
            destination = folder / "cleaned.mp4"
            report = clean_video(candidates[0], destination)
            import json
            (folder / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            cloud_key = None
            cloud_error = None
            share_token = None
            if os.getenv("PORTAL_AUTO_R2_UPLOAD", "false").lower() == "true":
                try:
                    from storage import upload_cleaned
                    from sharing import create_share
                    cloud_key = upload_cleaned(destination, job_id)["key"]
                    share_token = create_share(root, job_id, cloud_key)
                except Exception:
                    # Do not expose cloud credentials or SDK error details to clients.
                    cloud_error = "Cloud upload unavailable; local cleaned file remains available"
            with queue_lock:
                job["status"] = "completed"
                job["download_url"] = f"/api/links/{job_id}/download"
                job["r2_uploaded"] = bool(cloud_key)
                job["share_url"] = f"/watch/{share_token}" if share_token else None
                job["cloud_error"] = cloud_error
        except Exception:
            with queue_lock:
                job["status"] = "failed"
                job["error"] = "Video download or processing failed; check server logs"


def get_job(job_id: str) -> dict | None:
    with queue_lock:
        job = jobs.get(job_id)
        return job.copy() if job else None
