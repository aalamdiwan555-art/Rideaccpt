# Dr. Clicker — Android Studio Setup Guide

## Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 35 (API 35)
- A physical Android device running Android 11+ (API 30+) — emulators cannot run AccessibilityService gestures or hardware screenshots reliably.

---

## Step 1 — Add OpenCV Android SDK

Dr. Clicker uses OpenCV for template matching. You must add the OpenCV Android AAR manually:

1. Download **OpenCV Android SDK 4.x** from https://opencv.org/releases/  
   (choose the "Android" release, e.g. `opencv-4.10.0-android-sdk.zip`)

2. Unzip the archive. Locate the file:  
   `OpenCV-android-sdk/sdk/OpenCV.aar`

3. Copy `OpenCV.aar` into the project at:  
   `app/libs/OpenCV.aar`

4. The `app/build.gradle.kts` already contains:
   ```kotlin
   implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
   ```
   This picks it up automatically — no additional `settings.gradle.kts` changes needed.

---

## Step 2 — Open in Android Studio

1. **File → Open** → select the `DrClicker/` folder.
2. Let Gradle sync complete.
3. If prompted to add `gradle-wrapper.jar`, download it:  
   `./gradlew wrapper` (or let Android Studio fetch it automatically).

---

## Step 3 — Build & Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or press **▶ Run** in Android Studio.

---

## Step 4 — First-Launch Permissions

Grant these in order:

| Permission | How |
|---|---|
| **Accessibility Service** | Settings → Accessibility → Downloaded Apps → Dr. Clicker Auto-Accept Engine → Enable |
| **Display over other apps** | Settings → Apps → Dr. Clicker → Display over other apps → Allow |
| **Photos/Media** | Granted automatically when you tap the template picker |

---

## Step 5 — Load a Template

1. Open the Rapido Captain app.
2. Wait for a ride request popup to appear.
3. Take a **screenshot** and **crop it tightly** around only the yellow "Accept" button.
4. Open Dr. Clicker → tap the template picker → select the cropped image.
5. The template thumbnail will appear in the dashboard.

---

## Step 6 — Set Filters (Optional)

| Field | Default (blank = unlimited) |
|---|---|
| Min Price | 0 |
| Max Price | 99999 |
| Min Pickup | 0.0 km |
| Max Drop | 999.0 km |

---

## Step 7 — Activate

Toggle **ENGINE ACTIVATION** to ON. Dr. Clicker will:
1. Verify accessibility is enabled.
2. Verify overlay permission is granted.
3. Start the floating overlay widget.
4. Begin watching every Rapido Captain window update for the Accept button.

---

## Architecture Notes

| File | Role |
|---|---|
| `MainActivity.kt` | Dashboard UI, permission checks, SharedPrefs |
| `FloatingOverlayService.kt` | WindowManager overlay widget, foreground service |
| `AutoAcceptEngineService.kt` | AccessibilityService, OpenCV matching, gesture dispatch |
| `res/xml/accessibility_service_config.xml` | Accessibility flags (gestures, screenshot, window content) |

### Critical Bug Fixes Implemented

1. **Instance Fix** — `companion object { var instance }` pattern; never instantiate the service manually.
2. **Bitmap Memory Fix** — Hardware bitmap copied to `ARGB_8888` before `Utils.bitmapToMat()`.
3. **Click Fix** — `moveTo(x,y) + lineTo(x, y+1f)` creates a valid non-zero vector.
4. **Android 15 Stuck-Pointer Fix** — Multi-touch reset gesture dispatched after 3 idle cycles or a cancelled gesture.
5. **Null Filter Safe-Zone** — Blank inputs default to `0 / 99999 / 0.0f / 999.0f`.
