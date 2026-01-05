# Phase 1 Build & Test Instructions

> Note: Runtime now uses QuickJS (Kotlin) instead of the Hermes stub. Update log filters and expectations accordingly while the testing guide is refreshed.

## Prerequisites

### System Requirements
- Android SDK 24+ (API level)
- Android NDK r25 or later
- Gradle 7.5+
- Java 11
- Rust toolchain (for transpiler)

### Environment Setup

```bash
# Install NDK (if not already installed)
sdkmanager "ndk;25.1.8937393"

# Set environment variables
export ANDROID_HOME=~/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.1.8937393

# Verify installation
echo $ANDROID_NDK_HOME
ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/
```

## Build Instructions

### Option 1: Build with Gradle (Recommended)

```bash
cd /home/ari/dev/relay-client-android/android

# Clean build
./gradlew clean

# Build debug APK (with native libraries)
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Build from Command Line (Advanced)

```bash
cd /home/ari/dev/relay-client-android/android

# Build with explicit NDK path
./gradlew assembleDebug \
  -DANDROID_NDK_HOME=$ANDROID_NDK_HOME

# Or set in gradle.properties
echo "android.ndkVersion=25.1.8937393" >> app/gradle.properties
./gradlew assembleDebug
```

### Option 3: Build Release APK

```bash
cd /home/ari/dev/relay-client-android/android

./gradlew assembleRelease

# You'll need a keystore for signing:
# See android/app/proguard-rules.pro for config
```

## Deployment to Device/Emulator

### Prerequisites
- Device connected via USB with USB debugging enabled, OR
- Android emulator running (API 24+)

### Commands

```bash
# List connected devices
adb devices

# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Install with force kill of previous app
adb install -r -f app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.relay.client/com.relay.client.MainActivity

# View real-time logs
adb logcat | grep -E "HermesManager|NativeRenderer|RelayJSI|start\.jsx"

# Save logs to file
adb logcat > app.log &

# Clear previous logs and start fresh
adb logcat -c
adb logcat > app.log &
adb shell am start -n com.relay.client/com.relay.client.MainActivity
sleep 5
kill %1  # Stop logcat
cat app.log
```

## Troubleshooting

### Build Fails: "NDK not found"

```bash
# Solution 1: Set NDK path in gradle.properties
echo "ndk.dir=$ANDROID_NDK_HOME" >> android/app/gradle.properties

# Solution 2: Set environment variable before build
export ANDROID_NDK=$ANDROID_NDK_HOME
./gradlew assembleDebug

# Solution 3: Download NDK via sdkmanager
sdkmanager "ndk;25.1.8937393"
```

### Build Fails: "CMake not found"

```bash
# Install CMake via sdkmanager
sdkmanager "cmake;3.22.1"

# Or let gradle download it automatically
# (gradle will prompt if missing)
```

### Compilation Errors in jsi_bridge.cpp

**Error**: `error: use of undeclared identifier 'String'`

```bash
# Cause: JSI headers not included
# Solution: Verify build.gradle has React dependencies
grep "react-android\|react-core" android/app/build.gradle

# Should output:
# implementation "com.facebook.react:react-android:0.72.4"
# implementation "com.facebook.react:react-core:0.72.4"
```

**Error**: `no matching function for call to 'String::createFromUtf8'`

```bash
# Cause: JSI version mismatch
# Solution: Check CMakeLists.txt includes correct packages
# May need to update React version to match Hermes version
```

### APK Installation Fails

```bash
# Error: "Timeout waiting for lock"
# Solution: Kill gradle daemon
./gradlew --stop

# Error: "INSTALL_FAILED_INVALID_APK"
# Solution: Clean build
./gradlew clean assembleDebug adb install -r

# Error: "INSTALL_FAILED_VERSION_DOWNGRADE"
# Solution: Uninstall previous version first
adb uninstall com.relay.client
adb install app/build/outputs/apk/debug/app-debug.apk
```

### App Crashes on Launch

**Check logs first**:
```bash
adb logcat | grep -A 5 "FATAL\|CRASH\|Exception"
```

**Common issues**:

1. **"Failed to load native library"**
   - Cause: JNI library not built
   - Fix: Ensure CMakeLists.txt is in app folder and gradle is building it
   - Check: `app/build/intermediates/cxx/` for native build output

2. **"Hermes initialization failed"**
   - Cause: Hermes runtime creation failed
   - Fix: Verify Hermes dependency in build.gradle
   - Check: `adb logcat | grep HermesManager`

3. **"start.jsx not found"**
   - Cause: Asset file missing
   - Fix: Ensure `android/app/src/main/assets/start.jsx` exists
   - Check: `adb shell ls -la /data/app/com.relay.client-*/base/assets/`

4. **"nativeBridge not available"**
   - Cause: JSI bridge setup failed
   - Fix: Check C++ compilation succeeded
   - Check: `adb logcat | grep "RelayJSIBridge\|RelayJSI"`

## Verification: What to Expect

### Console Output (adb logcat)

```
HermesManager: Initializing Hermes runtime
HermesManager: Hermes runtime created, pointer: 0x12345678
HermesManager: Loading React library
HermesManager: React loaded successfully (XXX bytes)
RelayJSIBridge: Setting up JSI bridge with Hermes runtime
RelayJSIBridge: JSI bridge setup complete
HermesManager: Loading start.jsx
HermesManager: Transpiling start.jsx (XXX bytes)
HermesManager: Executing transpiled start.jsx (XXX bytes)
HermesManager: start.jsx executed successfully
HermesManager: Hermes initialization complete
```

### UI Expectations

1. **App launches without crashing** ✓
2. **White screen appears** ✓
3. **Text "Hello from Hermes!" visible in center** ✓
4. **No error dialogs** ✓
5. **App doesn't ANR (freeze)** ✓

## Success Criteria

Phase 1 is complete when:

- [ ] APK builds without errors
- [ ] APK installs on device/emulator
- [ ] App launches without crashing
- [ ] "Hello from Hermes!" text appears on white background
- [ ] Console shows all "HermesManager" and "RelayJSIBridge" logs
- [ ] No "Exception" or "CRASH" messages in logs

## Next: Phase 2

Once Phase 1 is verified:

1. Run: `cd /home/ari/dev && ./gradlew assembleDebug -p relay-client-android/android`
2. Test on tablet: Connect tablet and install APK
3. Verify rendering on larger screen (responsive behavior)
4. Proceed to Phase 2: Styling System

See `PHASE_1_COMPLETE.md` for what was implemented.
