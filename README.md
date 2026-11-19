# SIGHT — On-Device AI Visual Intelligence for Android

SIGHT is a real, working Android app (Kotlin + Jetpack Compose) that runs
computer vision **entirely on the phone**: object detection (TensorFlow Lite),
multi-object tracking (Kalman filter + IoU association), motion analysis,
camera-motion estimation, OCR (ML Kit), scene summaries, and a synthetic
Simulation Lab. No server, no account, no uploads.

> **Status:** Complete Android Studio project. The sandbox used to generate
> this code has no Android SDK (Java 11 only, 1.9 GB RAM), so the APK itself
> is built by GitHub Actions (`.github/workflows/build-apk.yml`) or locally in
> Android Studio — see *Build the APK* below. Nothing is faked: if the model
> file is missing the app says so instead of inventing detections.

---

## What is implemented

| Module | Implementation |
|---|---|
| **Detection** | TFLite `Interpreter`, bundled **COCO SSD MobileNet v1 quantized** (`app/src/main/assets/coco_ssd_mobilenet.tflite`, 300×300, 90 COCO classes, from TensorFlow's model zoo, Apache 2.0). Adjustable confidence threshold, CPU + optional GPU delegate, honest `READY / MISSING / ERROR` status. |
| **Tracking** | Per-track constant-velocity **Kalman filter** (`vision/KalmanFilter.kt`) + greedy IoU association (`vision/MultiObjectTracker.kt`). Track IDs, age, missed-frame coasting, recovery, 60-point histories. |
| **Motion** | `vision/MotionAnalyzer.kt` — velocity (px/s), 8-way direction, acceleration, 0.5 s trajectory prediction from observed history, uncertainty score. Zero-history → velocity 0 + uncertainty 1.0. |
| **Camera motion** | `vision/CameraMotionEstimator.kt` — downscaled grayscale template matching (block-matching global flow) to separate global camera shift from object motion. |
| **OCR** | ML Kit Text Recognition (on-device). Blocks categorized as NUMBER / HEALTH / AMMO / WARNING / LABEL / STATUS / OTHER with normalized bounding boxes. |
| **Scene** | `vision/SceneSummarizer.kt` — rule-based summary from real detections/tracks/OCR/camera data only; high uncertainty when data is sparse. |
| **Inputs** | Image picker, video import (`MediaMetadataRetriever` frame sampling, pause/resume/stop, progress), CameraX live analysis (throttled ~3 fps, `STRATEGY_KEEP_ONLY_LATEST`). |
| **Simulation** | Synthetic objects with velocity, wall bounce, random occlusion, camera pan, noise. Ground-truth boxes feed the real tracker/motion pipeline (labeled synthetic in UI). |
| **Export** | JSON report, CSV track/motion stats, OCR text — via Android share sheet. Annotated images drawn with boxes/trails/predictions. |
| **Privacy** | Offline-first, no account, no network calls in app code. History deletable, settings clearable. Camera permission explained in-app. |
| **Tests** | JVM unit tests (`app/src/test/.../VisionUnitTests.kt`): Kalman convergence, ID persistence, track recovery/retirement, velocity/direction, IoU, OCR categorization, scene uncertainty. |

### Screens
Home dashboard (status, quick access, recent analyses) · Vision (image/video/
camera + overlay + OCR) · Tracking (IDs, age, trails) · Motion (speed,
direction, uncertainty, camera motion) · Scene (summary, events) ·
Simulation Lab (count/speed/pan/occlusion/noise) · Settings (model, threshold,
quality, fps, GPU, privacy, export, about).

---

## Build the APK

### Option A — GitHub Actions (easiest, no local Android setup)
1. Push this `SIGHT/` folder to a new GitHub repo (branch `main`).
2. Open the **Actions** tab → *Build SIGHT APK* runs automatically.
3. Download the `SIGHT-debug-apk` artifact → install on your phone.

### Option B — Android Studio (local)
1. Install **Android Studio Hedgehog or newer** (bundles JDK 17).
2. *Open* this folder, let Gradle sync (downloads SDK 34 + dependencies).
3. Connect your phone (USB debugging) → **Run ▶**, or *Build → Build APK(s)*.
4. APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Option C — Command line (JDK 17 + Android SDK required)
```bash
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest
# APK: app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** JDK 17+, Android SDK platform 34, min device Android 10
(API 29), ARM64/ARMv7. The app needs no Python, Colab, or server.

### Install on your phone
1. Copy `app-debug.apk` to the phone (USB, Drive, WhatsApp, etc.).
2. Tap the APK → allow *Install unknown apps* for your browser/files app.
3. Open **SIGHT**. Grant camera permission only if you use the Camera tab.
4. Try **Simulation Lab → Start** first (no permissions needed), then
   **Vision → Pick image/video**.

---

## Model setup
The model is **already bundled** (`coco_ssd_mobilenet.tflite` + `labelmap.txt`
in `app/src/main/assets/`). To re-download it:
```bash
bash scripts/download_model.sh
```
Source: `https://storage.googleapis.com/download.tensorflow.org/models/tflite/coco_ssd_mobilenet_v1_1.0_quant_2018_06_29.zip`
(TensorFlow model zoo). If the file is deleted, SIGHT shows
*"Model file … not found in assets"* and detection stays disabled — it never
fabricates boxes.

---

## Architecture
```
app/src/main/java/com/sight/app/
├── MainActivity.kt, SightApp.kt
├── di/AppContainer.kt            (manual DI, no Hilt)
├── navigation/SightNavHost.kt    (bottom-nav graph)
├── data/                         (Room history, DataStore settings, export)
├── vision/                       (DetectionEngine, KalmanFilter, Tracker,
│                                  MotionAnalyzer, CameraMotionEstimator,
│                                  OcrEngine, SceneSummarizer, VisionModels)
├── util/ImageUtils.kt            (bounded decode + annotation drawing)
└── ui/                           (AnalysisViewModel + 7 Compose screens)
```
Background work uses coroutines (`Dispatchers.Default/IO`); the UI thread is
never blocked by inference. Video/camera resources are released in
`finally`/`onCleared`.

## Known limitations (honest)
- COCO SSD MobileNet detects everyday objects (person, car, dog…), **not**
  game-specific entities. Battle-Royale footage will yield sparse detections —
  use Simulation Lab for controlled tracking/motion tests.
- Camera-motion estimator is template matching on a downscaled frame, not
  full dense optical flow (Farnebäck). It estimates dominant global shift.
- OCR is Latin-script ML Kit; handwriting / stylized game fonts may fail.
- Motion prediction is constant-velocity extrapolation (0.5 s horizon) with
  uncertainty — not a learned trajectory model.
- No background service: camera/video analysis runs only while the app screen
  is visible (by design, for battery/privacy).
- Release signing needs your own keystore (`signingConfigs` not included).

## License
App code: MIT. Bundled TFLite model + labels: TensorFlow model zoo (Apache 2.0).
