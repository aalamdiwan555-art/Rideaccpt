# Dr. Clicker — Android Studio Project

## Overview
Dr. Clicker is a 100% offline, on-device Android utility app that auto-accepts ride requests in the **Rapido Captain** app (`com.rapido.rider`) using Accessibility Services + OpenCV template matching.

No Firebase, no login/signup, no ads, no external redirects.

---

## Project Structure
```
DrClicker/
├── app/
│   ├── build.gradle.kts               # App module — OpenCV AAR, Material, Coroutines
│   ├── libs/                          # Place OpenCV.aar here (see SETUP.md)
│   └── src/main/
│       ├── AndroidManifest.xml        # Permissions + service declarations
│       ├── kotlin/com/drclicker/
│       │   ├── MainActivity.kt        # Dashboard UI + permission checks
│       │   ├── FloatingOverlayService.kt  # WindowManager overlay widget
│       │   └── AutoAcceptEngineService.kt # Core AccessibilityService + OpenCV engine
│       └── res/
│           ├── layout/activity_main.xml   # Dark-themed dashboard
│           ├── layout/overlay_layout.xml  # Floating widget
│           ├── xml/accessibility_service_config.xml
│           ├── values/{colors,strings,themes,attrs}.xml
│           ├── drawable/              # Shapes, icons, selectors
│           └── color/                 # Switch color state lists
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts
├── gradle/libs.versions.toml          # Version catalog
└── SETUP.md                           # Full setup walkthrough
```

## User Preferences
- No Firebase, login screens, ads, or tracking — ever
- Production-ready, zero placeholders, first-compile-safe
- All critical bug fixes (bitmap, gesture path, lifecycle, Android 15 stuck pointer) implemented as specified
