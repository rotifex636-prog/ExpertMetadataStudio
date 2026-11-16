# Expert Metadata Studio v0.6 — development build

This branch replaces the scan-only prototype with an initial working media-processing engine.

Implemented:
- Gallery and document multi-selection
- MP4 remux of compatible audio/video streams (without re-encoding)
- JPEG/PNG raster re-export (recompression may change quality)
- Optional synthetic MP4 geolocation: New York, Los Angeles, Chicago
- Foreground service with notification, progress and cancellation
- MediaStore output to Movies/Pictures/ExpertMetadataStudio; original input preserved

Limitations: MP4 remux is codec/device-dependent; not every embedded tag is removed. Image export is not lossless. Not all video formats are supported. No verified long-file resilience, persistent job queue, manual tag editing, or completed five-screen design. Synthetic coordinates are not authentic provenance.

GitHub Actions builds a debug APK. Test on an Android device before relying on the outputs.
