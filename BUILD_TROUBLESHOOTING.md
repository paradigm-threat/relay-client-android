# Build & Troubleshooting Guide

## Quick Reference

### Environment Setup (One-Time)

```bash
# Set NDK path (find your version in ~/Android/Sdk/ndk/)
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

# Add to ~/.bashrc or ~/.zshrc to persist across sessions
```

### Full Build & Deploy

```bash
cd react-client-android
# 1. Build Rust JNI libraries
cd rust && cargo build --release --target aarch64-linux-android

# 2. Copy to Android
cp target/aarch64-linux-android/release/lib*.so ../android/app/src/main/jniLibs/arm64-v8a/

# 3. Build & install APK
cd ../android
./gradlew clean assembleDebug && ./gradlew installDebug

# 4. Launch and view logs
adb logcat -c
adb shell am force-stop com.relay.pure
adb shell am start -n com.relay.pure/.MainActivity
sleep 2
adb logcat | grep -E "QuickJSManager|NativeRenderer|Rendering complete"
```

---

## Common Issues & Solutions

### Issue 1: "error: linking with `cc` failed"

**Symptom**:
```
/usr/bin/ld: file in wrong format
/usr/bin/ld: ... Relocations in generic ELF (EM: 183) ...
error: could not compile `android-jni` (lib) due to 1 previous error
```

**Root Cause**: Android NDK linker not configured; using system `cc` instead of NDK's clang.

**Solution**:
```bash
# Verify environment variables are set
echo $ANDROID_NDK_HOME                                    # Should print NDK path
echo $CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER          # Should print clang path

# Verify linker exists
ls -la "$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"      # Should show file exists

# If not set, export them:
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

# Clean and rebuild
cd react-client-android/rust
cargo clean
cargo build --release --target aarch64-linux-android
```

**Permanent Fix** (add to `~/.bashrc` or `~/.zshrc`):
```bash
# Android NDK Configuration
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/27.0.12077973}"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
```

---

### Issue 2: "UnsatisfiedLinkError: No implementation found for nativeGetAndroidStyles"

**Symptom**:
```
AndroidRuntime: java.lang.UnsatisfiedLinkError: No implementation found for 
java.lang.String com.relay.pure.ThemedStylerModule.nativeGetAndroidStyles(...)
```

App crashes on startup when trying to style the first view.

**Root Cause**: 
1. Rust JNI library built, but `.so` file not copied to `jniLibs/`
2. Or old `.so` file without the symbol
3. Or linker failed silently during Rust build

**Solution**:

```bash
# 1. Verify symbol exists in compiled library
strings react-client-android/rust/target/aarch64-linux-android/release/libandroid_jni.so | \
  grep Java_com_relay_pure_ThemedStylerModule_nativeGetAndroidStyles

# Output should show:
# Java_com_relay_pure_ThemedStylerModule_nativeGetAndroidStyles
# (appears twice - one for each calling convention)

# 2. Copy the .so files (all three)
cp react-client-android/rust/target/aarch64-linux-android/release/libandroid_jni.so \
   react-client-android/android/app/src/main/jniLibs/arm64-v8a/

cp react-client-android/rust/target/aarch64-linux-android/release/librelay_hook_transpiler.so \
   react-client-android/android/app/src/main/jniLibs/arm64-v8a/

cp react-client-android/rust/target/aarch64-linux-android/release/libthemed_styler.so \
   react-client-android/android/app/src/main/jniLibs/arm64-v8a/

# 3. Verify files are in jniLibs
ls -lh react-client-android/android/app/src/main/jniLibs/arm64-v8a/

# Should show all three .so files, each > 1MB

# 4. Rebuild APK
cd react-client-android/android
./gradlew clean assembleDebug
./gradlew installDebug

# 5. Check logs
adb logcat -d | grep -E "FATAL|UnsatisfiedLink"
```

---

### Issue 3: "Unable to resolve module" or "Module not found" in QuickJS

**Symptom**:
```
QuickJS: ReferenceError: [module '@clevertree/themed-styler'] is not defined
```

**Root Cause**: Runtime loader not injecting globals before executing hook code.

**Solution**:
1. Verify `HermesManager.kt` initializes `globalThis` with required modules
2. Check `runtimeLoader.ts` is applied before hook execution
3. Ensure transpiler output includes global injection

```kotlin
// In HermesManager.kt, before executing hook code:
engine.evaluate("""
  globalThis.React = React;
  globalThis.nativeBridge = nativeBridge;
  globalThis.__relay_meta = { dirname, url, filename };
""", "init.js")
```

---

### Issue 4: "Relocations in generic ELF (EM: 183)" with themed-styler

**Symptom**: During `cargo build --release --target aarch64-linux-android`:
```
/usr/bin/ld: .../libthemed_styler.rlib: Relocations in generic ELF (EM: 183) file in wrong format
```

**Root Cause**: `themed-styler` crate built with `cdylib` (shared library) instead of `rlib` (static library) for JNI builds.

**Solution**:
```bash
# Check themed-styler/Cargo.toml has correct crate-type
cat themed-styler/Cargo.toml | grep -A2 "\[lib\]"

# Should show:
# [lib]
# crate-type = ["rlib"]  # NOT ["cdylib", "rlib"]

# If wrong, edit it and rebuild
cargo clean
cargo build --release --target aarch64-linux-android
```

---

### Issue 5: App Won't Start - Black Screen or Immediate Crash

**Symptom**: App launches but immediately crashes or shows black screen.

**Debug Steps**:

```bash
# 1. Clear all state
adb shell am force-stop com.relay.pure
adb shell rm -rf /data/data/com.relay.pure

# 2. Reinstall APK
cd react-client-android/android
./gradlew uninstallAll
./gradlew installDebug

# 3. Monitor startup
adb logcat -c
adb shell am start -n com.relay.pure/.MainActivity
sleep 1

# 4. Check for crashes
adb logcat -d | grep -E "FATAL|Exception|Error" | head -20

# 5. Check for specific issues
adb logcat -d | grep -E "QuickJSManager|NativeRenderer|ThemedStyler"
```

**Common Causes**:
- Missing JNI libraries → check `jniLibs/arm64-v8a/` not empty
- React not loaded → check `globalThis.React` is set
- Native bridge not initialized → check `MainActivity` calls `initialize()`

---

### Issue 6: UI Still Stacked Vertically

**Symptom**: All views appear stacked in a vertical line instead of using proper layout.

**Root Cause**: 
1. `width` defaulting to `WRAP_CONTENT`
2. `flexDirection` not being applied
3. Themed-styler not returning layout properties

**Solution**:

```bash
# 1. Verify styled-styler output includes layout properties
adb logcat -d | grep "applyThemedStyles" | head -5

# Should show output like:
# E: styles: { padding: 16, backgroundColor: "#3b82f6", width: "match_parent" }

# 2. If missing width, update themed-styler logic
# Edit react-client-android/rust/client-core/src/lib.rs
# Function: get_android_styles()
# Add: styles.insert("width", "match_parent") for divs

# 3. Rebuild and test
cargo build --release --target aarch64-linux-android
cp target/aarch64-linux-android/release/libandroid_jni.so ../android/app/src/main/jniLibs/arm64-v8a/
cd ../android && ./gradlew installDebug

# 4. Screenshot to verify
adb exec-out screencap -p > ui-test.png
```

---

### Issue 7: Style Changes Not Appearing

**Symptom**: Modified themed-styler code, but styles still show old values.

**Solution**:
```bash
# 1. Full clean of Rust build artifacts
cd react-client-android/rust
cargo clean
rm -rf target/

# 2. Rebuild
cargo build --release --target aarch64-linux-android

# 3. Copy updated libraries
cp target/aarch64-linux-android/release/libthemed_styler.so ../android/app/src/main/jniLibs/arm64-v8a/

# 4. Clean Android build
cd ../android
./gradlew clean

# 5. Rebuild APK and install
./gradlew assembleDebug
./gradlew installDebug
```

---

## Verification Checklist

### Before Reporting a Bug

- [ ] Run `cargo build --release --target aarch64-linux-android` successfully
- [ ] All three `.so` files copied to `jniLibs/arm64-v8a/`
  ```bash
  ls -lh android/app/src/main/jniLibs/arm64-v8a/lib*.so  # Should show 3 files
  ```
- [ ] APK installs without errors: `./gradlew installDebug`
- [ ] App launches: `adb shell am start -n com.relay.pure/.MainActivity`
- [ ] Logcat shows no `UnsatisfiedLinkError`: `adb logcat | grep UnsatisfiedLink`
- [ ] Logcat shows `[JS] Rendering complete!`: `adb logcat | grep "Rendering complete"`

### If Issue Persists

1. **Collect logs**:
   ```bash
   adb logcat -d > full_logcat.txt
   cat full_logcat.txt | grep -E "Error|Exception|FATAL" > errors.txt
   ```

2. **Take screenshot**:
   ```bash
   adb exec-out screencap -p > screenshot.png
   ```

3. **Check .so files**:
   ```bash
   file android/app/src/main/jniLibs/arm64-v8a/libandroid_jni.so
   strings android/app/src/main/jniLibs/arm64-v8a/libandroid_jni.so | grep Java_com
   ```

4. **Share these with developer**

---

## Performance Optimization

### Reducing APK Size

```bash
# Build release APK (minified)
cd android
./gradlew assembleRelease

# Check APK size
ls -lh app/build/outputs/apk/release/app-release.apk
```

Current breakdown:
- React: ~40KB (minified + gzipped)
- hook-transpiler: ~150KB
- themed-styler: ~80KB
- Android app code: ~200KB

Target with ACT library: ~5KB (vs 40KB React)

---

## References

- [Android NDK Documentation](https://developer.android.com/ndk/downloads)
- [Rust Android Target](https://github.com/rust-lang/rust/wiki/Installation-Instructions-for-Targets)
- [JNI Best Practices](https://developer.android.com/training/articles/perf-jni)
- [Gradle Build System](https://developer.android.com/build)
