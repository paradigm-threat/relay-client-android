# Development Setup Guide

## Environment Configuration

### Android NDK Linker (CRITICAL for Rust Builds)

The Android NDK linker must be configured to build Rust native libraries. Without this, compilation fails with "file in wrong format" errors.

#### Find Your NDK Installation

```bash
# Common locations
ls -d ~/Android/Sdk/ndk/*           # macOS/Linux
ls -d "/c/Users/[USER]/AppData/Local/Android/Sdk/ndk"  # Windows
```

#### Set Environment Variables

Add these to your shell profile (`.bashrc`, `.zshrc`, etc.) or run before each build:

```bash
# Export NDK path (replace with your actual NDK version)
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"

# Configure Cargo to use NDK's clang for Android target
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
```

#### Verify Configuration

```bash
# Check NDK exists
ls -la "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

# Output: Should show the clang executable
# -rwxrwxr-x 1 ari ari 201 Dec  5 06:57 .../aarch64-linux-android21-clang
```

### Build Rust Components

```bash
cd react-client-android/rust

# Clean previous builds (recommended)
cargo clean

# Build for Android target
cargo build --release --target aarch64-linux-android

# Verify symbols were generated
strings target/aarch64-linux-android/release/libandroid_jni.so | \
  grep Java_com_relay_pure_ThemedStylerModule_nativeGetAndroidStyles

# Output: Should show the JNI symbol
# Java_com_relay_pure_ThemedStylerModule_nativeGetAndroidStyles
```

### Deploy Libraries to APK

```bash
# Copy built .so files to Android's jniLibs directory
cp target/aarch64-linux-android/release/libandroid_jni.so \
   ../android/app/src/main/jniLibs/arm64-v8a/

cp target/aarch64-linux-android/release/librelay_hook_transpiler.so \
   ../android/app/src/main/jniLibs/arm64-v8a/

cp target/aarch64-linux-android/release/libthemed_styler.so \
   ../android/app/src/main/jniLibs/arm64-v8a/
```

## Building the Android App

### Prerequisites

- **Android Studio** 4.2+ or command-line tools
- **Gradle** 7.0+ (included with Android Studio)
- **Java** 11+ JDK
- **NDK** 25+ (configured as above)
- **Rust** with `aarch64-linux-android` target:
  ```bash
  rustup target add aarch64-linux-android
  ```

### Build Steps

```bash
cd react-client-android/android

# Full clean build
./gradlew clean assembleDebug

# Incremental build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Troubleshooting Build Issues

#### "Relocations in generic ELF (EM: 183)" Error
**Cause**: NDK linker not configured  
**Fix**: 
1. Verify `CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER` is set
2. Run `cargo clean` in `rust/` directory
3. Rebuild Rust: `cargo build --release --target aarch64-linux-android`

#### "No implementation found for nativeGetAndroidStyles"
**Cause**: JNI symbol missing from `.so` library  
**Fix**:
1. Verify Rust build succeeded and generated symbols: `strings libandroid_jni.so | grep nativeGetAndroidStyles`
2. Copy the latest `.so` file to `jniLibs/arm64-v8a/`
3. Clean and rebuild APK: `./gradlew clean assembleDebug`

#### "UnsatisfiedLinkError: Unable to load library"
**Cause**: `.so` file not in correct directory or wrong architecture  
**Fix**:
1. Check library exists: `ls -la android/app/src/main/jniLibs/arm64-v8a/libandroid_jni.so`
2. Verify file size > 1MB (not corrupted)
3. Rebuild and reinstall: `./gradlew installDebug`

## Running & Debugging

### Launch on Device

```bash
# Clear device logs
adb logcat -c

# Force-stop previous instance
adb shell am force-stop com.relay.pure

# Launch app
adb shell am start -n com.relay.pure/.MainActivity

# Monitor logs
adb logcat | grep -E "QuickJSManager|NativeRenderer|ThemedStyler"
```

### View UI on Screen

```bash
# Take screenshot
adb exec-out screencap -p > screenshot.png

# Open in image viewer
open screenshot.png  # macOS
eog screenshot.png   # Linux (Eye of GNOME)
```

### Inspect Logcat

Key tags to monitor:
- `QuickJSManager` - Hook transpilation and execution
- `NativeRenderer` - View creation and styling
- `ThemedStylerModule` - Style computation
- `AndroidRuntime` - Crashes and exceptions

```bash
# Show all logs
adb logcat

# Filter by tag
adb logcat | grep NativeRenderer

# Show last 500 lines
adb logcat -d | head -500

# Search for errors
adb logcat | grep -i "error\|exception\|crash"
```

## Layout & Styling

### Current Layout System

The app uses Android's `LinearLayout` with the following features:

- **Root container**: `LinearLayout` with `VERTICAL` orientation
- **Flex direction**: Supports `row` and `column` layouts
- **Width/Height**: `MATCH_PARENT`, `WRAP_CONTENT`, or pixel values
- **Flex weight**: Proportional space distribution

### Common Layout Issues

#### UI is Stacked Vertically
**Cause**: All views defaulting to `WRAP_CONTENT` width  
**Fix**: Set `width="match_parent"` on container divs or use `flexDirection="row"` with `flex` properties

#### Components Not Responding to Click
**Cause**: Event listeners not registered or `isClickable=false`  
**Fix**: Verify `addEventListener` is called and `clickable=true` is set

#### Text Not Visible
**Cause**: Text color same as background or text size too small  
**Fix**: Check themed-styler output includes `color` and `fontSize` properties

## Testing Changes

### Automatic Rebuild & Run

```bash
# Watch for changes and rebuild
cd rust && cargo build --release --target aarch64-linux-android && \
cp target/aarch64-linux-android/release/libandroid_jni.so ../android/app/src/main/jniLibs/arm64-v8a/ && \
cd ../android && ./gradlew assembleDebug && ./gradlew installDebug
```

### Quick Development Loop

1. Make changes to Rust code (`client-core/src/lib.rs` or `android-jni/src/lib.rs`)
2. Run: `cargo build --release --target aarch64-linux-android`
3. Copy `.so` to `jniLibs/arm64-v8a/`
4. Run: `./gradlew assembleDebug && ./gradlew installDebug`
5. Check logs: `adb logcat | grep NativeRenderer`

### Testing Styled Components

Create a simple test component and verify styling:

```jsx
// test.jsx
export default function Test() {
  return (
    <div className="p-4 bg-blue-500">
      <span className="text-white">Styled Text</span>
    </div>
  )
}
```

Verify in logs:
```
applyThemedStyles: div with className "p-4 bg-blue-500"
styles: { padding: 16, backgroundColor: "#3b82f6" }
NativeRenderer: applying styles to LinearLayout
```

## Next Steps

- See [ACT_LIBRARY_MIGRATION_PLAN.md](ACT_LIBRARY_MIGRATION_PLAN.md) for planned React replacement
- See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for architectural decisions
- See [README.md](README.md) for project overview
