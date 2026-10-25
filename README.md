# Face Detector (Android)

A real-time, privacy-first Android face detection application built with **Jetpack Compose**, **CameraX 1.3.4**, and **Google ML Kit Face Detection 16.1.6**.

All machine learning processing executes **100% locally on-device**. The application requests **zero network permissions** (`android.permission.INTERNET` is omitted) and **zero storage permissions** (leveraging Android Photo Picker and Scoped Storage MediaStore APIs). The application strictly performs face bounding box and expression metric detection—it does **not** recognize, identify, or catalog personal biometric templates.

---

## 🌟 Key Features

### 📷 Camera & Detection Quality
- **Front & Back Camera Toggle**: Defaults to the Front Camera for instant selfie/user framing with seamless horizontal mirroring. Back camera detection preserves natural orientations.
- **Pause & Resume Detection**: Pause detection to freeze analysis while keeping the camera preview running smoothly with zero stutter.
- **Flicker-Free Face Count**: Features a temporal stabilization algorithm (`FaceCountSmoother`) that requires consistent face count detection across 3 consecutive frames before updating the UI, eliminating single-frame flicker.
- **Configurable Settings Sheet**:
  - **Performance Mode**: Toggle between *Fast Mode* (high FPS, battery efficient) and *Accurate Mode* (deeper landmark and multi-face analysis).
  - **Minimum Face Size**: Interactive slider (10% to 80%) to adjust face detection sensitivity.
  - **Per-Face Metrics**: Show or hide smile probability and left/right eye openness indicators.
- **Full Rotation & Landscape Support**: Normalized coordinate mapping dynamically adapts to orientation changes (portrait, landscape left, landscape right) ensuring bounding boxes always line up accurately.
- **Clean Lifecycle & Resource Cleanup**: Camera providers, background executors, and ML Kit client instances are safely unbound and terminated upon pause, stop, rotation, and background navigation.

### 🖼️ Gallery & Expression Analysis
- **Android Photo Picker**: Select and analyze static images directly from your photo library without granting broad storage permissions.
- **Per-Face Classification**: Displays smile percentage (`😊 92%`) and individual eye openness (`👁️ L:95% R:98%`) beside each detected face.
- **Annotated Screenshot Capture**: Save camera frames and photo detections with overlaid bounding boxes directly into the device's standard `Pictures/FaceDetector` folder using `MediaStore` without runtime storage permissions on Android 10+.

### 🎨 Material 3 Design
- **Theme**: Material 3 Navy & Blue visual identity (`#0D2B5C`, `#2F6FED`, `#4FC3F7`) with full Dark Mode support and dynamic color disabled.
- **Animated Bounding Boxes**: Smooth rounded-corner face brackets with subtle glowing pulse animation.
- **Status Pill Indicator**: Status badge displaying real-time state:
  - 🟢 **Green**: Face detected (with stabilized face count)
  - ⚪ **Gray**: No face detected
  - 🟡 **Amber**: Detection paused
  - 🔵 **Blue**: Camera stopped / Gallery mode
  - 🔴 **Red**: Camera error or permission needed
- **First-Launch Onboarding**: Interactive privacy onboarding sheet introducing on-device processing guarantees.
- **Adaptive Vector Launcher Icon**: Blue face-scan vector frame on navy background.

---

## 🏗️ Project Architecture

```
app/src/main/
├── java/com/example/facedetector/
│   ├── MainActivity.kt         # Compose UI, camera view, controls & dialogs
│   ├── FaceModels.kt           # Domain models: DetectedFace, FaceResult, Settings
│   ├── CoordinateTransform.kt  # Pure, decoupled coordinate transform & count smoother
│   ├── ScreenshotHelper.kt     # Scoped storage MediaStore image saving
│   ├── ImageUtils.kt           # Orientation-aware image decoder & gallery detector
│   └── Theme.kt                # Navy/Blue Material 3 theme & color schemes
└── res/
    ├── drawable/               # Vector icons (adaptive foreground & background)
    ├── mipmap-anydpi-v26/      # Adaptive launcher icons
    └── values/                 # Themes, colors, strings
```

---

## 🚀 Setup & Build Instructions

### Prerequisites
- **JDK**: Java 17 LTS (e.g. Microsoft JDK 17, OpenJDK 17)
- **Android SDK**: Compile SDK 34, Min SDK 24, Build Tools 34.0.0
- **Gradle**: 8.7 (bundled via Gradle Wrapper `gradlew`)

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
The generated APK will be available at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Unit Tests
```bash
./gradlew testDebugUnitTest
```
Tests cover pure coordinate mapping, mirrored front-camera flips, landscape rotations, fill-center aspect ratio crops, and the 3-frame face count smoother.

### 3. Build Minified Release APK (R8 & Resource Shrinking)
```bash
./gradlew assembleRelease
```
The optimized release APK is output to:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 📱 How to Run on an Emulator or Physical Device

### Method A: Install via ADB (Command Line)
1. Ensure your physical device has **Developer Options** and **USB Debugging** enabled, or start an Android Virtual Device (AVD) from Android Studio.
2. Verify connection:
   ```bash
   adb devices
   ```
3. Install the debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch the app directly from your terminal:
   ```bash
   adb shell am start -n com.example.facedetector/.MainActivity
   ```

### Method B: Run from Android Studio
1. Open the project root in **Android Studio**.
2. Wait for Gradle sync to complete.
3. Select your target device in the device dropdown.
4. Click the green **Run (▶)** button or press `Shift + F10`.

---

## 🔒 Privacy & Safety Guarantee

- **No Network Requests**: The `android.permission.INTERNET` permission is completely omitted. The application is isolated from network transmission.
- **No Face Recognition**: The ML Kit Face Detection API bundled with the app calculates facial landmarks and bounding geometry. No identity verification, facial recognition database matching, or biometric profiling is performed.
- **Zero Broad Storage Permissions**: Photo selection uses the modern system Photo Picker, granting access only to the user-selected file. Screenshots are stored using Android 10+ MediaStore Scoped Storage.
