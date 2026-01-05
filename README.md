# <img src="android/app/src/main/assets/icon.png" width="32" height="32" align="center" /> Relay Android Client

A cross-platform Android application built entirely without Android/iOS Native, using **hook-transpiler** and **themed-styler** as the core rendering system. This architecture provides true React-to-Native compilation while maintaining multi-OS UI consistency.

## Core Architecture: Hook-Transpiler (Android) + Themed-Styler + Host-Bundled React

### The Problem We Solved

Android/iOS Native introduced unnecessary complexity—heavy bridging, slow loading, platform inconsistencies, and maintenance headaches. Instead, we use a **compile-once, render-native** approach where:

1. **Host app bundles React once** (in APK)
2. **JSX hooks are transpiled on-device** (only code, no libraries)
3. **All hooks share the same React instance** (memory efficient, consistent state)
4. **styled via themed-styler** (native-optimized properties)

```
┌──────────────────────────┐
│  Host APK                │
│  ├─ React (bundled)      │
│  ├─ start.jsx            │
│  └─ Rust JNI libs        │
└────────┬─────────────────┘
         │ provides React to all:
    ┌────┴────────────────────┐
    │                         │
    ▼                         ▼
[start.jsx]           [Remote Hooks]
(bundled)             (fetched from peers)
```

### Why This Works

1. **hook-transpiler** (Rust, Android QuickJS/JNI)
   - Transpiles JSX/TSX to platform-agnostic JavaScript at runtime
   - Rewrites `import React from 'react'` → `const React = globalThis.React`
   - Runs inside the embedded QuickJS runtime (on-device), no thread overhead
   - Reusable across web (WASM), Android (JNI), iOS (soon)

2. **themed-styler** (Rust Library)
   - Generates CSS/styles from theme definitions and class names
   - Returns native-friendly style objects (DP values, Android color formats)
   - Supports responsive breakpoints (`sm:`, `md:`, `lg:`)
   - Zero JavaScript dependencies

3. **Host-Bundled React** (Production Build)
   - Single React instance for all UI (start.jsx + remote hooks)
   - Minimal APK bloat (React ~40KB minified + gzipped)
   - Consistent state management across all components
   - All hooks can use React hooks (`useState`, `useEffect`, etc.)

4. **Custom React Renderer** (JavaScript + Kotlin)
   - React reconciler that maps elements to native Android Views
   - Element types: `View`, `Text`, `TSDiv`, etc.
   - Props drive both layout and styling
   - Event delegation through native bridge

5. **Native Bridge** (Kotlin JNI + QuickJS)
   - Exposes native APIs to JavaScript (file I/O, networking, etc.)
   - Routes styling requests to themed-styler
   - Manages View hierarchy with native renderer
   - Async promise-based for smooth UX

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    React Component                          │
│  <View className="p-4 bg-blue"><Text>Hello</Text></View>  │
└────────────────────┬────────────────────────────────────────┘
                     │ (All hooks use host's React)
        ┌────────────▼─────────────┐
        │  React Reconciler        │
        │ (Custom Host Config)     │
        └────────────┬─────────────┘
                     │
         ┌───────────▼────────────────┐
         │  Native Bridge (JSI)       │
         │  nativeBridge.{          │
         │    createView()           │
         │    updateProps()          │
         │    addChild()             │
         │    transpile()            │
         │    getStyles()            │
         │  }                        │
         └───────────┬────────────────┘
                     │
        ┌────────────┼────────────────┐
        │            │                │
   ┌────▼────┐  ┌───▼────┐   ┌──────▼──────┐
   │ QuickJS │  │ Kotlin  │   │ JNI Bridge  │
   │ Engine  │  │ Android │   │  (Rust)     │
   │         │  │ Runtime │   └──┬───┬──────┘
   │React    │  │         │      │   │
   │bundled  │  └────┬────┘      │   │
   │in APK   │       │      ┌────▼───▼──────┐
   └─────────┘       │      │ Rust Crates  │
                     │      ├──────────────┤
                     │      │hook-transpiler
                     │      │themed-styler │
                     │      │network-lib   │
                     │      └──────────────┘
                     │
             ┌───────▼──────────┐
             │  Native Views    │
             │  FrameLayout     │
             │  TextView        │
             │  ImageView       │
             │  ... (mapped)    │
             └──────────────────┘
```

## Key Features

### 1. **Universal Hooks (Bundled + Remote)**
- **start.jsx**: Bundled in APK, defines app shell
- **Remote hooks**: Fetched from peers at runtime
- **All use same React**: No duplicate libraries
- Transpiled on-device in milliseconds
- Render immediately without app restart

Example: Same hook code works whether bundled or fetched:

```jsx
import React, { useState } from 'react'  // Host's React

export default function Counter() {
  const [count, setCount] = useState(0)
  return (
    <div onClick={() => setCount(count + 1)}>
      Count: {count}
    </div>
  )
}
```

### 2. **Responsive Multi-Device UI**
````
- Single JSX codebase for mobile, tablet, desktop
- Themed-styler handles breakpoints automatically
- Window size reported to JS environment
- Styles adapt in real-time

### 3. **Universal Styling**
- Write CSS-like classes: `bg-blue-500 text-white p-4 rounded`
- Themed-styler generates native style objects
- Automatic color format conversion (hex → Android int)
- Support for shadows, gradients, borders via native APIs

### 4. **Peer-to-Peer Network**
- Discover relay servers on local network
- Open remote hooks in tabs
- No central server required
- WebSocket support for real-time sync

### 5. **Native Capabilities Bridge**
- File I/O with scoped storage
- WebSocket connections via OkHttp
- Camera, location, sensors (extensible)
- All exposed as JS promises

## Project Structure

```
relay-client-android/
├── android/                           # Android Gradle project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/relay/pure/  # Kotlin code
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NativeRenderer.kt
│   │   │   │   ├── RustTranspilerModule.kt
│   │   │   │   ├── ThemedStylerModule.kt
│   │   │   │   └── NetworkModule.kt
│   │   │   ├── assets/
│   │   │   │   ├── start.jsx         # Entry point app
│   │   │   │   ├── HookRenderer.jsx  # Hook loader component
│   │   │   │   └── renderer.js       # React reconciler
│   │   │   └── AndroidManifest.xml
│   │   ├── build.gradle
│   │   └── proguard-rules.pro
│   ├── build.gradle
│   └── settings.gradle
│
├── rust/                              # Rust JNI + core logic
│   ├── Cargo.toml
│   ├── client-core/                   # Business logic
│   │   ├── src/lib.rs                 # Transpile, style, networking
│   │   └── Cargo.toml
│   └── android-jni/                   # JNI bindings
│       ├── src/lib.rs                 # JNI function implementations
│       └── Cargo.toml
│
└── android-client-plan.md             # Original architecture plan
```

## Build & Deployment

### Prerequisites
- Android SDK 24+ (API level)
- NDK r25 or later (for Rust compilation)
- Rust toolchain with `aarch64-linux-android` target

### Building the Rust JNI Libraries

The Rust libraries require the Android NDK linker to be configured. Before building, set the environment:

```bash
# Find your Android NDK path (typically in ~/Android/Sdk/ndk/<version>)
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

# Verify the linker exists
ls -la "$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"

# Build the JNI libraries
cd rust && cargo build --release --target aarch64-linux-android

# Copy the generated .so files to jniLibs
cp target/aarch64-linux-android/release/libandroid_jni.so ../android/app/src/main/jniLibs/arm64-v8a/
cp target/aarch64-linux-android/release/librelay_hook_transpiler.so ../android/app/src/main/jniLibs/arm64-v8a/
cp target/aarch64-linux-android/release/libthemed_styler.so ../android/app/src/main/jniLibs/arm64-v8a/
```

**Note**: The NDK linker is critical for building Android native libraries from Rust. Without it, you'll get linker errors like "Relocations in generic ELF" or missing symbols.

### Quick Build & Run

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
adb logcat -d | grep -E "Rendering complete|UnsatisfiedLinkError"
```

- Rust toolchain with `rustup target add aarch64-linux-android`
- Cargo NDK for cross-compilation: `cargo install cargo-ndk`

### Build Steps

1. **Setup Rust targets**
   ```bash
   rustup target add aarch64-linux-android
   rustup target add armv7-linux-androideabi
   rustup target add x86_64-linux-android
   ```

2. **Build Rust crates**
   ```bash
   cd rust
   cargo build --release --target aarch64-linux-android
   ```

3. **Build APK**
   ```bash
   cd ../android
   ./gradlew build  # Debug
   ./gradlew assemble --variant=release  # Release
   ```

4. **Deploy to device**
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

## Development Workflow

### Editing JSX
Edit `android/app/src/main/assets/start.jsx` and rebuild:
```bash
cd android && ./gradlew installDebug
```

### Testing Hook Transpilation
The `RustTranspilerModule` exposes JSX transpilation. Test with:
```kotlin
val code = "import React from 'react'; export default () => <div>Hello</div>"
val transpiled = RustTranspilerModule.transpile(code, "test.jsx")
Log.d("Transpile", transpiled)
```

### Debugging Native Bridge
Enable verbose logging in `NativeRenderer.kt` and `MainActivity.kt`:
```kotlin
Log.d("NativeBridge", "Creating view: type=$type, props=$props")
```

## Implementation Status

### ✅ Completed
- [x] Rust core library (transpile, style, network)
- [x] JNI bindings for all core functions
- [x] Kotlin module wrappers
- [x] Basic Native Renderer skeleton
- [x] JSX entry point and components
- [x] QuickJS runtime bootstrap (Kotlin + asset loading)

### 🚧 In Progress
- [ ] QuickJS ↔ Kotlin nativeBridge plumbing (events, async)
- [ ] Complete NativeRenderer (layout, events)
- [ ] React reconciler implementation
- [ ] Network module JNI bindings
- [ ] APK compilation and testing

### 📋 Not Started
- [ ] Responsive breakpoint detection
- [ ] Advanced styling (shadows, gradients)
- [ ] File I/O module
- [ ] WebSocket support
- [ ] Camera/location/sensor integration

## How Hook-Transpiler Becomes the Rendering Engine

### Load Phase
1. `start.jsx` (bundled in APK) loads in QuickJS
2. Transpiler initializes via JNI: `RustTranspilerModule.getVersion()`
3. React reconciler connects to native bridge (Android QuickJS)

### Render Phase
1. Component renders `<View className="flex-row p-4">Hello</View>`
2. React calls reconciler's `createInstance("View", props)`
3. Reconciler calls `nativeBridge.createView(tag, "View", props)`
4. Kotlin `NativeRenderer.createView()` creates FrameLayout
5. Props include className, so reconciler requests styles:
   - Calls `ThemedStylerModule.getRnStyles("View", ["flex-row", "p-4"], themes)`
   - Rust returns `{ paddingHorizontal: 16, paddingVertical: 16, flexDirection: "row" }`
6. Kotlin applies styles: `view.setPadding(16, 16, 16, 16)` etc.

### Hook Loading Phase
1. User taps peer in Peers tab
2. `HookRenderer` fetches `/hooks/client/get-client.jsx`
3. Calls `nativeBridge.transpile(code, filename)`
4. Rust transpiler converts JSX imports and returns clean JavaScript
5. HookRenderer executes transpiled code: `new Function(...)(transpiled)`
6. Component mounts, goes through render phase above

## Cross-Platform Future

This architecture is designed for expansion:

- **iOS**: Replace Kotlin with Swift, keep QuickJS core consistent, themed-styler produces UIView-compatible styles
- **Web**: Swap QuickJS for V8/SpiderMonkey, themed-styler produces CSS, custom web renderer maps to DOM
- **Desktop**: Tauri/Electron variant with same Rust core

The core transpiler and styler are **platform-agnostic**. Only the renderer changes.

## References

- [Hook-Transpiler](../hook-transpiler) - JSX/TSX transpiler written in Rust
- [Themed-Styler](../themed-styler) - Theme and style engine in Rust
- [Android JNI Docs](https://developer.android.com/training/articles/on-device-compilation)
- [QuickJS (Bellard/Odéon)](https://bellard.org/quickjs/)
- [React Reconciler](https://github.com/facebook/react/tree/main/packages/react-reconciler)

## Next Steps

1. Finish QuickJS nativeBridge wiring (events, async callbacks)
2. Expand NativeRenderer with full view type support
3. Implement React reconciler properly
4. Test on tablet with live hook loading
5. Add responsive breakpoint detection
6. Implement file I/O and WebSocket modules

## License

Part of the Relay project ecosystem.
