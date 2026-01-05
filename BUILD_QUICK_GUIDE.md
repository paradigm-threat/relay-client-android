# React Client Android - Quick Build Guide

## APK Version System

The app now displays its version in the **bottom-right corner** of the screen (e.g., `v1.0.2`).

## Auto-Increment Build Script

Use the `build-and-deploy.sh` script to automatically:
1. Increment the version number (patch version)
2. Build the APK
3. Install to connected device

### Usage

```bash
# From project root
./build-and-deploy.sh
```

### What it does:
- Reads current version from `android/app/build.gradle`
- Increments versionCode (2 → 3)
- Increments versionName (1.0.2 → 1.0.3)
- Updates build.gradle
- Runs `./gradlew clean assembleDebug`
- Installs to connected device via `adb install -r`

### Manual Build

If you prefer to build manually:

```bash
cd android
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then manually update versionCode and versionName in `android/app/build.gradle`.

## Current Version

Current version: **1.0.2** (versionCode: 2)

## Version History

- 1.0.2 - Added Peers view UI matching web client, version display in bottom right
- 1.0.1 - Initial release
