# Expert Video Portal — Web development plan

Status: Initial development specification; no deployed web service yet.

## Goal
A separately deployable web application, reusing and matching Expert Metadata Studio's documented metadata-cleaning behavior where technically feasible. Do not change the existing Android APK.

## Pipeline
Authorized Facebook URL(s) / admin file upload → queued sequential download → codec/container inspection → metadata cleaning → verification → Cloudflare R2 upload → unique public playback/download page.

## Architecture
- Frontend: responsive admin dashboard and public share pages.
- Backend: authenticated REST API, background job worker, persistent job/file database.
- Media engine: FFprobe inspection and FFmpeg stream-copy remux for supported codecs; metadata removal must be defined by explicit fields and verified against Android reference outputs. Stream copy does not guarantee removal of every proprietary/internal metadata field.
- Downloads: permitted Facebook URLs, per-item error isolation, sequential processing, strict URL/redirect validation to prevent SSRF; respect platform access and copyright restrictions.
- Storage: private R2 credentials on server only, public playback via controlled delivery endpoint/custom domain; lifecycle and quota controls.
- Security: admin-only uploads, authentication, CSRF protections where relevant, rate limiting, input validation, file limits, safe filenames, job retention, no secrets in repository.

## Proposed API
- POST /api/auth/login
- POST /api/jobs/urls
- POST /api/jobs/upload
- GET /api/jobs/{id}
- GET /api/videos
- DELETE /api/videos/{id}
- GET /v/{public_id} (public share page)
- GET /media/{public_id} (playback/download delivery)

## Acceptance tests
1. Process five authorized Facebook URLs; one failed link must not stop others.
2. Compare original, APK-cleaned and web-cleaned test videos using ffprobe and documented metadata checks.
3. Preserve source dimensions, codecs and quality via stream copy when supported.
4. Confirm public playback and HD download on a separate phone without login.
5. Verify R2 upload, object deletion, authentication and cost controls.

## Technical caveats
- MP4 is a container, not a guarantee of H.264; VP9 may require a compatible source stream or transcoding.
- Web/server FFmpeg remuxing is not necessarily byte-identical to Android MediaMuxer.
- R2 pricing, egress and request allowances must be checked against the current Cloudflare plan.
- No promise that metadata cleaning changes copyright status, Facebook duplication detection or monetization.

## Development milestones
M1: web backend scaffold + video inspection/cleaning + local tests.
M2: admin upload and authenticated API.
M3: sequential authorized URL downloader and job queue.
M4: R2 storage and public player/download pages.
M5: APK parity tests, five-link acceptance test, deployment and documentation.
