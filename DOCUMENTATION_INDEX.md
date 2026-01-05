# Android Client Documentation Index

## Quick Start

### For Building
→ **[Quick Build & Run](README.md#quick-build--run)** in README.md
→ If issues occur → **[BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md)**

### For Layout Issues  
→ **[LAYOUT_IMPROVEMENTS.md](LAYOUT_IMPROVEMENTS.md)** for detailed explanation
→ **[LAYOUT_SUMMARY.md](LAYOUT_SUMMARY.md)** for quick reference

### For Development Setup
→ **[DEVELOPMENT_SETUP.md](DEVELOPMENT_SETUP.md)** for complete environment configuration

---

## Documentation Files

### Core Architecture
| File | Purpose | Audience |
|------|---------|----------|
| **README.md** | Main architecture & features | Everyone |
| **android-client-plan.md** | Original design document | Architects |

### Build & Development
| File | Purpose | Audience |
|------|---------|----------|
| **BUILD_TROUBLESHOOTING.md** | Build issues, solutions, verification | Developers |
| **DEVELOPMENT_SETUP.md** | NDK configuration, environment, testing | Developers |
| **BUILD_AND_TEST.md** | Rust build procedures | Developers |

### Layout System (NEW)
| File | Purpose | Audience |
|------|---------|----------|
| **LAYOUT_IMPROVEMENTS.md** | Detailed layout fix explanation (300 lines) | Developers |
| **LAYOUT_SUMMARY.md** | Quick layout reference | Quick lookup |
| **SESSION_LAYOUT_FIXES_SUMMARY.md** | What was accomplished in this session | Everyone |

### Implementation Status
| File | Purpose | Audience |
|------|---------|----------|
| **PHASE_1_COMPLETE.md** | Phase 1 completion report | Project leads |
| **PHASE_1_INDEX.md** | Phase 1 component index | Developers |
| **THEMED_STYLER_INTEGRATION_COMPLETE.md** | Styling integration summary | Developers |
| **EVENT_HANDLING_IMPLEMENTATION.md** | Event system details | Developers |

### Migration & Future
| File | Purpose | Audience |
|------|---------|----------|
| **ACT_LIBRARY_MIGRATION_PLAN.md** | ACT migration roadmap (4 weeks) | Project leads |
| **UPGRADE_NOTES.md** | Version upgrade instructions | DevOps |

### Implementation Guides
| File | Purpose | Audience |
|------|---------|----------|
| **IMPLEMENTATION_PLAN.md** | Detailed implementation roadmap | Developers |
| **NATIVE_MODULES.md** | Native module integration | Developers |
| **TURBOMODULE_ISSUE.md** | TurboModule troubleshooting | Developers |
| **INTEGRATION_EXAMPLES.tsx** | Code examples | Developers |
| **UPDATE_FEATURE.md** | Feature update guide | Developers |

---

## Key Technologies

### Rust JNI Libraries
- **hook-transpiler**: JSX/TSX transpilation (Rust)
- **themed-styler**: CSS/theme generation (Rust)
- **client-core**: Business logic and styling (Rust)
- **android-jni**: JNI bindings (Rust)

### Kotlin/Android
- **MainActivity.kt**: Activity entry point
- **NativeRenderer.kt**: View creation & styling
- **RustTranspilerModule.kt**: Transpiler JNI wrapper
- **ThemedStylerModule.kt**: Styled-styler JNI wrapper
- **QuickJSManager.kt**: JS runtime management
- **NativeBridge.kt**: JS-to-Kotlin communication

### JavaScript/JSX
- **start_enhanced.jsx**: Test entry point
- **start.jsx**: Production entry point (referenced)

---

## Current Status

### ✅ Completed
- [x] Rust JNI compilation with NDK linker
- [x] Symbol verification (nativeGetAndroidStyles present)
- [x] Layout dimension handling (MATCH_PARENT, wrap_content)
- [x] Root view auto-add to container
- [x] Themed-styler integration
- [x] 8 views rendering successfully
- [x] Click events working
- [x] Comprehensive documentation

### 🚧 In Progress
- [ ] ACT library migration (planned)
- [ ] Advanced layout support (ScrollView, etc.)
- [ ] Responsive breakpoints (sm:, md:, lg:)

### 📋 Not Started
- [ ] File I/O module
- [ ] WebSocket support
- [ ] Camera/location/sensor integration

---

## Environment Setup

For first-time setup, see [DEVELOPMENT_SETUP.md](DEVELOPMENT_SETUP.md).

For quick build, set these environment variables:

```bash
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
```

---

## Common Workflows

### Building APK
```bash
cd relay-client-android
# Set environment (see above)
cd android && ./gradlew assembleDebug && ./gradlew installDebug
```
**Issue?** → [BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md)

### Debugging Layout
```bash
# Capture screenshot
adb exec-out screencap -p > screenshot.png

# Check logcat
adb logcat -d | grep "NativeRenderer"
```
**Issue?** → [LAYOUT_IMPROVEMENTS.md](LAYOUT_IMPROVEMENTS.md)

### Rebuilding JNI after Rust changes
```bash
cd rust && cargo build --release --target aarch64-linux-android
cp target/aarch64-linux-android/release/lib*.so ../android/app/src/main/jniLibs/arm64-v8a/
cd ../android && ./gradlew assembleDebug && ./gradlew installDebug
```
**Issue?** → [BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md#issue-1-error-linking-with-cc-failed)

### Testing Event Handling
See **start_enhanced.jsx** for button click test example.

---

## Directory Structure

```
relay-client-android/
├── android/                          # Gradle project
│   ├── app/src/main/
│   │   ├── java/com/relay/pure/     # Kotlin code (NativeRenderer, etc.)
│   │   ├── assets/                  # JSX entry points
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── settings.gradle
│
├── rust/                             # Rust JNI & core logic
│   ├── client-core/                  # Business logic
│   ├── android-jni/                  # JNI bindings
│   ├── Cargo.toml
│   └── README.md
│
├── screenshots/                      # UI screenshots
│   ├── ui-layout-fixed.png
│   ├── ui-debug-layout.png
│   └── final-layout.png
│
├── docs/                             # Generated documentation (optional)
│
├── README.md                         # Main architecture guide
├── BUILD_TROUBLESHOOTING.md          # Build issue solutions
├── DEVELOPMENT_SETUP.md              # Environment setup
├── LAYOUT_IMPROVEMENTS.md            # Layout system details
├── LAYOUT_SUMMARY.md                 # Quick layout reference
├── SESSION_LAYOUT_FIXES_SUMMARY.md   # This session's work
│
└── [other documentation files...]
```

---

## Quick Reference: Common Errors

| Error | Solution | Doc |
|-------|----------|-----|
| `UnsatisfiedLinkError: nativeGetAndroidStyles` | Rebuild Rust with NDK linker | [BUILD_TROUBLESHOOTING.md#issue-2](BUILD_TROUBLESHOOTING.md#issue-2-unsatisfiedlinkerror-no-implementation-found-for-nativegetandroidstyles) |
| `Relocations in generic ELF` | Export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER | [BUILD_TROUBLESHOOTING.md#issue-1](BUILD_TROUBLESHOOTING.md#issue-1-error-linking-with-cc-failed) |
| UI stacked vertically | Check width dimension parsing | [LAYOUT_IMPROVEMENTS.md#issue-views-still-stacking-vertically](LAYOUT_IMPROVEMENTS.md#issue-views-still-stacking-vertically) |
| Width not applied | Verify parseLayoutDimension handles 100% | [LAYOUT_IMPROVEMENTS.md#testing-layout](LAYOUT_IMPROVEMENTS.md#testing-layout) |

---

## Next Session Checklist

- [ ] Review ACT_LIBRARY_MIGRATION_PLAN.md
- [ ] Set up ACT library scaffolding
- [ ] Begin phase 1 ACT integration
- [ ] Keep NativeRenderer as fallback during migration

---

## Contact & Resources

- **Hook-Transpiler**: [../hook-transpiler](../hook-transpiler)
- **Themed-Styler**: [../themed-styler](../themed-styler)
- **Android NDK**: https://developer.android.com/ndk/downloads
- **QuickJS**: https://bellard.org/quickjs/
- **React Reconciler**: https://github.com/facebook/react/tree/main/packages/react-reconciler

---

*Last Updated: December 20, 2024*  
*Layout Fixes & Documentation Improvements*
