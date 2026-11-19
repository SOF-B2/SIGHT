# SIGHT — Build Verification Report

**Date:** 2026-11-19
**Environment checked:** Debian sandbox, OpenJDK 11 only, no Android SDK,
1.9 GB RAM, 21 GB free disk, internet available.

## APK status: NOT built in this sandbox (honest)

No APK file exists yet. This sandbox **cannot** compile Android apps:
- Android Gradle Plugin 8.5 requires **JDK 17** (only JDK 11 installed).
- No Android SDK / `sdkmanager` / `adb` present.
- 1.9 GB RAM is below what Gradle + Kotlin + Compose need (OOM risk).

Pretending otherwise would be dishonest. Instead, this deliverable is the
**complete Android Studio project** plus a **GitHub Actions workflow** that
builds the APK on a proper JDK 17 + SDK runner. Push to GitHub → Actions →
download `SIGHT-debug-apk`. Or open in Android Studio and press Run.

## What WAS verified here

| Check | Result |
|---|---|
| Project structure (manifest, Gradle, wrapper jar, 27 Kotlin files, resources, tests, CI) | ✅ Present, listed in verification output |
| Bundled model `coco_ssd_mobilenet.tflite` (4.0 MB) + `labelmap.txt` from TensorFlow model zoo | ✅ Downloaded & verified (`unzip -l` shows `detect.tflite` 4,183,312 bytes) |
| Gradle wrapper jar valid zip | ✅ `file` reports Zip archive, 43 KB |
| Kotlin brace balance (all 27 files) | ✅ Balanced |
| Algorithm verification (Python port of shipped logic) | ✅ Kalman convergence PASS (x=20.00, vx=1.00), stationary PASS, direction PASS, IoU PASS, velocity PASS, OCR categorization PASS |
| Bug found & fixed during verification | ✅ Initial Kalman `P` update missed `F·P·Fᵀ` (velocity stayed 0). Fixed with full matrix propagation; re-verified PASS. |
| Dependency fixes | ✅ Added `kotlinx-coroutines-play-services` (ML Kit `await()`), fixed `material-icons-extended` version, fixed CameraX lifecycle binding, fixed TFLite asset loading (stream, not mmap) |

## What still needs a real Android build to confirm

- `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` (runs in CI / Android Studio).
- On-device: TFLite inference speed, CameraX binding, ML Kit OCR download on first use.
- The 13 JVM unit tests in `VisionUnitTests.kt` mirror the Python-verified logic.

## Files delivered

- `SIGHT/` — full project (source, model, tests, README, CI workflow, scripts).
- `SIGHT.zip` — zipped copy for easy download.
- `README.md` — build/install instructions, architecture, limitations.
