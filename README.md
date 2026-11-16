# Expert Metadata Studio — Android Foundation v0.1

Premium dark-blue Android UI with multi-file selection using Android Storage Access Framework.

## Current implementation
- Kotlin + Jetpack Compose home screen
- Multi-select images/videos using system picker
- Selected filename and size display
- Explicit pipeline preview
- GitHub Actions debug APK build

## NOT implemented yet
Metadata reading/writing, USA profile generation, export, progress tracking, large-file worker, verification, and format compatibility. The prototype never modifies media.

## Build
1. Upload this project's contents to a GitHub repository (keep `.github` folder).
2. Open Actions > Android Debug APK > Run workflow (or push a commit).
3. Download `ExpertMetadataStudio-debug-apk` artifact after a successful run.
4. Install the debug APK on Android; allow installation from your chosen source.

A signed release requires a separate keystore and signing configuration. Validate the GitHub build before treating the project as production-ready.
