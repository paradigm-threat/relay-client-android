# relay-client-android Copilot Instructions

## Project Overview
Android client for Relay hook execution system. Uses `hook-transpiler` library to render JSX hooks to native Android views.

## Architecture

### Key Components
1. **MainActivity** - Entry point, manages the `HookRenderer` instance.
2. **HookRenderer** (from `hook-transpiler`) - The core engine that executes JS and renders views.
3. **Assets** - Local JSX/TSX hooks bundled with the app in `src/main/assets`.

## Development Workflow

### Live Reload (CRITICAL for Iteration)
The app supports live reloading of JSX files from the host machine to avoid slow APK redeploys.

**Setup Steps:**
1. **ADB Reverse Proxy**: You MUST forward the dev server port:
   ```bash
   adb reverse tcp:8081 tcp:8081
   ```
2. **Start Dev Server**:
   ```bash
   cd scripts
   ./start-dev.sh  # This script runs adb reverse and starts the node server (dev-server.cjs)
   ```
3. **Verify Connection**: Check `adb logcat` for:
   `I HookRenderer: Dev server detected! Enabling live reload.`

### Build & Deploy
If you make changes to Kotlin code or native libraries:
```bash
cd android
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### Dev Server Not Detected
1. Verify `adb reverse tcp:8081 tcp:8081` was run.
2. Ensure the Node.js server is running on port 8081.
3. Check that the app is a `debug` build (live reload is disabled in release).
4. Check `AndroidManifest.xml` has `android:usesCleartextTraffic="true"`.

### Changes Not Reflecting
1. Check dev server logs to see if it detected the file change.
2. Check `adb logcat` for "Reload signal received from dev server".
3. Ensure you are modifying the files in `android/app/src/main/assets/`.

## Key Files
- `android/app/src/main/java/com/relay/client/MainActivity.kt` - App logic
- `android/app/src/main/assets/` - Hook files
- `scripts/dev-server.cjs` - Live reload server
- `scripts/start-dev.sh` - Setup script
