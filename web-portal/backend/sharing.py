"""Persistent unlisted public share tokens for successfully uploaded R2 videos."""
from __future__ import annotations

import secrets
import sqlite3
import time
from pathlib import Path

def database(root: Path):
    root.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(root / "shares.sqlite3", timeout=10)
    conn.execute("""CREATE TABLE IF NOT EXISTS shares (
        token TEXT PRIMARY KEY, job_id TEXT NOT NULL, object_key TEXT NOT NULL,
        created_at INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0)""")
    return conn

def create_share(root: Path, job_id: str, object_key: str) -> str:
    if not object_key.startswith("processed/") or not object_key.endswith("/cleaned.mp4"):
        raise ValueError("Invalid R2 object key")
    token = secrets.token_urlsafe(32)
    with database(root) as conn:
        conn.execute("INSERT INTO shares (token,job_id,object_key,created_at) VALUES (?,?,?,?)",
                     (token, job_id, object_key, int(time.time())))
    return token

def lookup_share(root: Path, token: str) -> dict | None:
    if not (32 <= len(token) <= 128) or not all(c.isalnum() or c in "-_" for c in token):
        return None
    with database(root) as conn:
        row = conn.execute("SELECT job_id,object_key FROM shares WHERE token=? AND revoked=0",
                           (token,)).fetchone()
    return {"job_id": row[0], "key": row[1]} if row else None

def revoke_share(root: Path, token: str) -> bool:
    with database(root) as conn:
        cursor = conn.execute("UPDATE shares SET revoked=1 WHERE token=? AND revoked=0", (token,))
        return cursor.rowcount > 0
