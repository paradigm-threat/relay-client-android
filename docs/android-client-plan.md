# Plan: Pure Android Relay Client (Without Android/iOS Native)

## 1. Overview
The goal is to create a new Android repository for a pure Android application that renders its UI using React, transpiled on-the-fly via the `hook-transpiler` Rust crate, and styled using `themed-styler`. This architecture avoids the Android/iOS Native framework while providing a similar "React-to-Native" experience.

## 2. Core Architecture
- **Language**: Kotlin for the native shell, Rust for performance-critical transpilation and styling logic.
- **JS Engine**: **QuickJS** (standalone). Chosen for small footprint, fast startup, and portability across Android/iOS.
- **UI Rendering**: A custom React Host Renderer (using `react-reconciler`) that maps React elements (`View`, `Text`, `TSDiv`, etc.) to native `android.view.View` components.
- **Transpilation**: Integration of the `hook-transpiler` Rust crate via JNI to handle TSX/JSX transpilation in the background.
- **Styling**: Adaptation of `themed-styler` to produce style definitions that can be applied directly to Android Views via a Kotlin-based Style Applier.

## 3. Component Analysis & Strategy

### 3.1 hook-transpiler
- **Strategy**: Leverage the existing JNI bindings in `hook-transpiler/src/android_jni.rs`.
- **Package Update**: Update JNI method signatures to match the new app's package name (e.g., `com.relay.client.RustTranspilerModule`).
- **JS Integration**: Expose `nativeTranspile` to the QuickJS runtime as a global function (e.g., `globalThis.__hook_transpile_jsx`).

### 3.2 themed-styler
- **Changes Needed**:
    - **Android Style Support**: Extend the Rust logic to provide style objects optimized for Android's View system (e.g., handling DP vs PX, Android-specific color formats).
    - **Native Style Applier**: In Kotlin, implement a `StyleApplier` that takes the JSON output from `themed-styler` and calls native View methods (`setBackgroundColor`, `setPadding`, `setElevation` for shadows, etc.).
    - **Direct Styling**: Remove dependencies on Android/iOS Native's `StyleSheet` and instead use a direct mapping from `themed-styler` classes to View properties.

### 3.3 Hook Renderer & JSX Bundling
- **Start File**: A bundled `start.jsx` in the APK's `assets/` directory will serve as the entry point.
- **Boot Sequence**:
    1. Kotlin app starts and initializes the JS engine.
    2. Loads the React runtime and the custom Android Host Renderer.
    3. Transpiles `assets/start.jsx` using `hook-transpiler`.
    4. Executes the transpiled JS to render the root UI.
- **Hook Loading**: The `HookRenderer` component (ported from the RN version) will handle fetching remote hooks, transpiling them via JNI, and rendering them as children.

## 4. Feature Implementation

### A. Peer List & Port Checks
- **Implementation**: A React component within `start.jsx` that uses native-exposed `fetch` or `Socket` APIs to check peer availability.
- **Async Workflow**: Use React's `useEffect` and `useState` to trigger checks and update the UI incrementally without blocking the main list load.

### B. Tabs & Remote Hooks
- **Tab System**: Implement a custom Tab View in React (or use a lightweight web-compatible library) that manages multiple `HookRenderer` instances.
- **Remote Rendering**: Opening a peer socket will create a new tab and pass the host URL to a `HookRenderer`, which occupies the full width/height (minus the global menu).

### C. Responsive UI (Mobile/Tablet/Desktop)
- **Strategy**: Use `themed-styler`'s built-in breakpoint support (`sm:`, `md:`, `lg:`).
- **Implementation**: The custom renderer will provide the current window dimensions to the JS environment, allowing `themed-styler` to select the correct CSS/Style rules for the device type.

### D. Settings & Debug Tab
- **Content**: Version info for `hook-transpiler` and `themed-styler` (fetched via JNI), plus the current application state (Zustand or similar).
- **Layout**: Standard list of settings with a dedicated debug section at the bottom.

## 5. Native Capabilities
- **File I/O**: Expose a `FileManager` module to JS via the bridge, supporting read/write in scoped storage and common directories (Documents, Downloads).
- **Networking**:
    - **WebSockets**: Native WebSocket bridge (using OkHttp) to support remote hook communication.
    - **Ephemeral Ports**: Kotlin-level `ServerSocket` management to open ports as needed for peer-to-peer checks.

## 6. Development Workflow
1. **New Repo Setup**: Initialize a pure Android project (Kotlin/Gradle).
2. **Rust Integration**: Link `hook-transpiler` and `themed-styler` as submodules or cargo dependencies; build JNI libraries (`.so` files).
3. **JS Runtime**: Integrate QuickJS and set up the Kotlin nativeBridge surface. Prefer to use Rust interop only where performance is needed; keep runtime wiring in Kotlin for simplicity.
4. **Custom Renderer**: Implement the `react-reconciler` host.
5. **App Logic**: Create `start.jsx` and port the `HookRenderer` component.
6. **Verification**: Test on mobile and tablet emulators to ensure responsive behavior.
