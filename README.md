# Background Video Recorder — Android App

A clean, Play Store-ready Android app that records video + audio silently in the background, controllable via a home screen widget.

---

## Features

- 🎥 **Background recording** using Camera2 API + `ForegroundService`
- 🎙️ **Audio recording** via microphone (AAC codec)
- 📱 **Home screen widget** — Start/Stop with one tap, no need to open the app
- 🔔 **Persistent notification** with live timer + Stop action
- 📂 **Recordings list** — browse, play, share, or delete saved videos
- ⚙️ **Settings** — choose video quality (480p / 720p / 1080p) and camera (front/back)
- 🌙 **Dark theme** throughout

---
--- 

## Project Structure

```
app/src/main/
├── java/com/bgrecorder/app/
│   ├── MainActivity.java           # Main UI, permission handling
│   ├── RecordingsActivity.java     # Browse saved recordings
│   ├── SettingsActivity.java       # Video quality & camera settings
│   ├── service/
│   │   └── RecordingService.java   # Core foreground service (Camera2 + MediaRecorder)
│   ├── widget/
│   │   └── RecorderWidget.java     # AppWidgetProvider (start/stop from home screen)
│   └── utils/
│       ├── FileUtils.java          # File management + media scanner
│       └── RecordingAdapter.java   # RecyclerView adapter for recordings list
└── res/
    ├── layout/                     # All XML layouts
    ├── drawable/                   # Vector icons + shape drawables
    ├── xml/                        # Widget info, preferences, file_paths, backup rules
    ├── values/                     # Strings, colors, themes, arrays
    └── menu/                       # Main menu
```

---

## How to Build

### Requirements
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34

### Steps

1. **Open project** in Android Studio:
   ```
   File → Open → select BackgroundVideoRecorder/
   ```

2. **Sync Gradle** (Android Studio will prompt automatically)

3. **Build Debug APK:**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

4. **Build Release (Play Store):**
   - Generate a signing keystore: `keytool -genkey -v -keystore release.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000`
   - Add signing config to `app/build.gradle` under `signingConfigs`
   - `Build → Generate Signed Bundle / APK → Android App Bundle (.aab)`

---

## Play Store Checklist

| Item | Status |
|------|--------|
| `targetSdk 34` | ✅ |
| Foreground service types declared | ✅ (`camera`, `microphone`) |
| `POST_NOTIFICATIONS` permission | ✅ |
| Scoped storage (Android 10+) | ✅ |
| ProGuard / R8 minification | ✅ |
| `android:exported` declared on all receivers/activities | ✅ |
| FileProvider for sharing | ✅ |
| Backup rules (`data_extraction_rules.xml`) | ✅ |
| Adaptive launcher icon | ✅ |

---

## Permissions Explained (for Play Store listing)

| Permission | Why |
|---|---|
| `CAMERA` | Required to capture video frames |
| `RECORD_AUDIO` | Required to capture audio alongside video |
| `FOREGROUND_SERVICE` | Keeps the recording alive when the app is in background |
| `POST_NOTIFICATIONS` | Shows the persistent recording notification (Android 13+) |
| `WRITE_EXTERNAL_STORAGE` | Saves video to device (Android 9 and below only) |

---

## Technical Notes

- **Camera2 API** is used instead of the deprecated Camera API, ensuring compatibility with all modern Android devices.
- **MediaRecorder** is configured with H264 video + AAC audio encoded in an MP4 container.
- The **widget** communicates with the service via explicit intents — no sticky broadcasts.
- Recordings are stored in `Movies/BGRecorder/` on external storage. On Android 10+, scoped storage is used (`getExternalFilesDir()`).
- **Live timer** in the notification updates every second via a `Timer` on the background thread.
- The `RecordingService.isRunning` static flag is used to sync widget and UI state — suitable for single-process apps.

---

## Customisation

- **Add stealth mode**: Remove the notification display (not recommended for Play Store compliance)
- **Add scheduled recording**: Use `AlarmManager` to trigger `RecordingService` at a set time
- **Add cloud upload**: Integrate AWS S3 or Google Drive API after `stopRecording()` completes
- **Add max duration**: Set a `Timer` in `RecordingService` to call `stopRecording()` after N minutes
