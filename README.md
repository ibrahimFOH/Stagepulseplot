# StagePulse Plot

StagePulse Plot is an Android stage-plot and technical-rider application for live production work.

## Repository layout

- `planner/` contains the web planner and Technical Rider UI.
- `android/` contains the Android application shell and the bundled planner assets.
- `.github/workflows/ci.yml` validates the planner, checks that bundled Android assets stay synchronized, builds the signed release APK, verifies it, and uploads the APK artifact.
- `.github/workflows/manual-release.yml` builds the production release and creates the GitHub release for version `2.0.0`.

## Local web planner

Open `planner/index.html` in a modern browser or serve the `planner/` directory from a local web server.

## Android build

Open `android/` in Android Studio and build the `app` module. The release configuration uses the `STAGEPULSE_*` environment variables supplied by CI for release signing and falls back to the debug signing configuration when those variables are not present.

## Validation

The CI pipeline checks JavaScript syntax, verifies that the web planner and Android-bundled planner files are identical, builds the release APK, verifies its Android signature and package metadata, and stores the APK as a workflow artifact.

Physical X32/M32 and clean-install/reconnect testing remain required for production hardware validation.
