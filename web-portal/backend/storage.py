"""Private Cloudflare R2 storage integration using S3-compatible API.

Never put R2 credentials or presigned URLs in logs or source control.
Objects remain private. Admin can request short-lived download URLs.
"""
from __future__ import annotations

import os
from pathlib import Path

import boto3
from botocore.config import Config


def r2_client():
    account = os.environ.get("R2_ACCOUNT_ID")
    access = os.environ.get("R2_ACCESS_KEY_ID")
    secret = os.environ.get("R2_SECRET_ACCESS_KEY")
    bucket = os.environ.get("R2_BUCKET")
    if not all((account, access, secret, bucket)):
        raise RuntimeError("R2 storage is not configured")
    if not account.replace("-", "").isalnum():
        raise RuntimeError("Invalid R2 account identifier")
    client = boto3.client(
        "s3",
        endpoint_url=f"https://{account}.r2.cloudflarestorage.com",
        aws_access_key_id=access,
        aws_secret_access_key=secret,
        region_name="auto",
        config=Config(signature_version="s3v4", retries={"max_attempts": 3}),
    )
    return client, bucket


def upload_cleaned(path: Path, job_id: str) -> dict:
    if not path.is_file():
        raise FileNotFoundError("Cleaned output missing")
    if len(job_id) != 32 or any(c not in "0123456789abcdef" for c in job_id):
        raise ValueError("Invalid job identifier")
    client, bucket = r2_client()
    key = f"processed/{job_id}/cleaned.mp4"
    client.upload_file(str(path), bucket, key, ExtraArgs={"ContentType": "video/mp4"})
    return {"bucket": bucket, "key": key, "bytes": path.stat().st_size}


def presigned_download(key: str, expires: int = 900) -> str:
    if not key.startswith("processed/") or not key.endswith("/cleaned.mp4") or ".." in key:
        raise ValueError("Invalid storage key")
    client, bucket = r2_client()
    return client.generate_presigned_url(
        "get_object",
        Params={"Bucket": bucket, "Key": key, "ResponseContentDisposition": 'attachment; filename="cleaned.mp4"'},
        ExpiresIn=min(max(expires, 60), 3600),
    )
