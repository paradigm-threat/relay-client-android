# Relay Pure Android: Implementation Plan

> Update (Dec 2025): QuickJS is the chosen on-device engine for Android. References to Hermes in this document remain for historical context and will be updated as the QuickJS bridge matures.

## Status Update (Dec 20, 2025)

Summary of current implementation state and next actions:

- Runtime: QuickJS engine initialized on app start; selectable React/Act runtime added to the top bar and persists via SharedPreferences.
- Rendering: Home and Settings JSX render under Act; view creation, props updates, and event listeners flow through the Kotlin bridge.
- Errors: JS `console.error` messages surface in native UI; render paths wrapped with try/catch.
- Transpiler/Styler: Versions injected; WASM-based transpiler and themed-styler JNI modules present and used.

Checklist by Phase (adapted to QuickJS):

- [x] Phase 1: Basic rendering loop working (QuickJS + renderer + bridge)
  - [x] Initialize QuickJS and global runtime objects
  - [x] Render `home.jsx` and `settings.jsx` via Rust transpiler
  - [x] Native bridge for create/update/addChild/clearViews
- [~] Phase 2: Styling system integration
  - [~] Layout/appearance application via ThemedStylerModule (basic styles applied; audit in progress)
  - [ ] Typography and full flex behavior coverage
- [~] Phase 3: Event system
  - [x] Click handler wiring (`addEventListener('click', ...)`)
  - [ ] Long-press, scroll, input change listeners
- [~] Phase 4: Full app integration
  - [x] Versions display in UI; tabs functional; runtime selector implemented
  - [ ] Remote hook loading and peers view (pending)
  - [x] Error handling surfaces to UI
- [ ] Phase 5: Testing & polish
  - [x] Debug APK builds and installs on device
  - [ ] Release build, broader device testing, performance profiling

Next Actions:

1. Verify runtime persistence end-to-end (selector → restart → label/buttons).
2. Implement `onLongPress` event wiring in `NativeRenderer` and the QuickJS bridge.
3. Audit style application path; ensure typography and flex properties are applied.
4. Replace error `TextView` with a dismissible overlay for clearer end-user reporting.


## Vision

A fully functional Android application where:
1. **Host bundles React once** — React, ReactDOM, and utilities shipped in APK, available globally in Hermes
2. **All UI** is defined in JSX (start.jsx and remote hooks) — JSX-only, no library bundling
3. **Styling** is automatic via themed-styler based on className + tagName
4. **Layout** is computed by themed-styler with native view properties (DP, padding, margin, flex)
5. **Events** (clicks, touches, focus) are delegated through the native bridge back to React handlers
6. **Remote hooks are lightweight** — only code and transpiled, not bundled libraries
7. **No React Native overhead** — pure Rust + Kotlin + Hermes

## High-Level Data Flow

```
┌──────────────────────────────────────────────────────────────┐
│  start.jsx (Bundled in APK)                                  │
│  <StartApp>                                                   │
│    <PeersView>                                               │
│      <PeerItem onClick={handleOpenPeer} />                   │
│    </PeersView>                                              │
│  </StartApp>                                                 │
└────────────────┬─────────────────────────────────────────────┘
                 │ Hermes transpiles & executes
                 ▼
┌──────────────────────────────────────────────────────────────┐
│  React Renderer (Custom Host Config)                         │
│  Element: <PeerItem className="flex-row p-4" onClick={...}> │
│  Props: { host, port, onClick, className, ... }             │
└────────────────┬─────────────────────────────────────────────┘
                 │ Reconciler calls createInstance
                 ▼
┌──────────────────────────────────────────────────────────────┐
│  Native Bridge (Kotlin ↔ Rust)                               │
│  nativeBridge.createView({                                   │
│    tag: 42,                                                  │
│    type: "div",                                              │
│    props: {                                                  │
│      className: "flex-row p-4",                              │
│      onClick: <function>,                                    │
│      host: "127.0.0.1",                                      │
│      port: 8080                                              │
│    }                                                         │
│  })                                                          │
└────────────────┬─────────────────────────────────────────────┘
                 │
         ┌───────┴────────────────┐
         │                        │
         ▼                        ▼
┌──────────────────────┐  ┌────────────────────────────┐
│  Kotlin NativeRenderer  │  │ Rust ThemedStylerModule   │
│                      │  │                            │
│  1. createView(...)  │  │ Input:                     │
│  2. addChild(...)    │  │  - selector: "div"         │
│  3. updateProps(...) │  │  - classes: ["flex-row",   │
│                      │  │             "p-4"]         │
│  Returns:           │  │  - currentTheme data       │
│  FrameLayout @ tag42 │  │                            │
│                      │  │ Output (Android):          │
└──────────────────────┘  │  { layoutParams: { ... },  │
                          │    backgroundColor: "...", │
                          │    padding: 16,            │
                          │    ... }                   │
                          └────────────────────────────┘
                 │
                 │ Store styled View in tag map
                 │ Register click listener
                 │
                 ▼
         ┌─────────────────┐
         │  Android ViewTree
         │                │
         │  FrameLayout(42)
         │  ├─ setLayoutParams(...)
         │  ├─ setPadding(16,16,16,16)
         │  └─ setOnClickListener { ->
         │      nativeBridge.emit("view:click", {
         │        tag: 42,
         │        callbacks: [onClick]
         │      })
         │    }
         └─────────────────┘
                 │
                 │ User taps screen
                 │
                 ▼
         ┌─────────────────────────────────┐
         │  Android Click Event             │
         │  → Native Click Listener fires   │
         │  → Calls JS callback via bridge  │
         │  → nativeBridge.emit("click"...) │
         └──────────────┬──────────────────┘
                        │
                        ▼
         ┌──────────────────────────────────┐
         │  Hermes executes onClick handler  │
         │  from React component props       │
         │  → setActiveTab('hook')           │
         │  → setRemoteHost('127.0.0.1')    │
         └──────────────┬───────────────────┘
                        │
                        ▼
         ┌──────────────────────────────────┐
         │  React re-renders                 │
         │  (StartApp now shows HookRenderer)│
         │                                  │
         │  └─ HookRenderer fetches hook    │
         │     from peer & transpiles       │
         │                                  │
         │  └─ New element tree created    │
         │  └─ Cycle repeats (steps 2-7)   │
         └──────────────────────────────────┘
```

## Core Systems

### System 1: Element → View Mapping

**Goal**: React element types → Native Android Views

| React Element | Android View | Purpose |
|---|---|---|
| `<div>` | `FrameLayout` | Container, flexbox layout |
| `<span>` | `TextView` | Text content |
| `<View>` | `FrameLayout` | Generic container |
| `<Text>` | `TextView` | Text (RN-like) |
| `<Button>` | `AppCompatButton` | Clickable button |
| `<Image>` | `ImageView` | Image rendering |
| `<Input>` | `EditText` | Text input |
| `<ScrollView>` | `NestedScrollView` | Scrollable container |

**Implementation Layer**: `NativeRenderer.kt` `createView()` method

```kotlin
fun createView(tag: Int, type: String, props: Map<String, Any>): View {
    val view = when (type.lowercase()) {
        "div", "view" -> FrameLayout(context)
        "span", "text" -> TextView(context)
        "button" -> AppCompatButton(context)
        "image" -> ImageView(context)
        "input" -> EditText(context)
        "scrollview" -> NestedScrollView(context)
        else -> FrameLayout(context) // Default
    }
    view.id = tag
    nodes[tag] = view
    registerEventListeners(tag, type, props)
    applyStylesFromProps(tag, props)
    return view
}
```

---

### System 2: Styling via themed-styler

**Goal**: Convert className + tagName → Native View Properties

**Input**:
- `selector`: "div", "span", "button", etc. (the HTML/React type)
- `classes`: ["flex-row", "p-4", "bg-blue-500", "text-white"]
- `themes`: Current theme configuration
- `breakpoint`: "xs", "sm", "md", "lg" (device-dependent)

**Output** (Android-optimized):
```json
{
  "layout": {
    "width": "match_parent",
    "height": "wrap_content",
    "flexDirection": "row",
    "paddingLeft": 16,
    "paddingTop": 16,
    "paddingRight": 16,
    "paddingBottom": 16
  },
  "appearance": {
    "backgroundColor": -16776961,
    "textColor": -1
  },
  "typography": {
    "fontSize": 16,
    "fontFamily": "sans-serif",
    "fontWeight": 400
  }
}
```

**Call Flow**:
```kotlin
// From NativeRenderer.applyStylesFromProps()
val className = props["className"] as? String ?: ""
val classes = className.split(" ").filter { it.isNotEmpty() }
val classesJson = Json.encode(classes)

// Query themed-styler
val stylesJson = ThemedStylerModule.getRnStyles(
    selector = type.lowercase(),
    classesJson = classesJson,
    themesJson = currentThemesJson
)

// Parse and apply
val styles = Json.decode(stylesJson)
applyLayoutStyles(view, styles["layout"])
applyAppearanceStyles(view, styles["appearance"])
applyTypographyStyles(view, styles["typography"])
```

---

### System 3: Event Delegation

**Goal**: Native touch/click events → React callback handlers

**Architecture**:

```
┌─────────────────────────────────────────┐
│  Component Definition (JSX)              │
│  <PeerItem onClick={handleOpenPeer} />  │
└─────────────────┬───────────────────────┘
                  │ React passes callback in props
                  ▼
┌─────────────────────────────────────────┐
│  React Reconciler                        │
│  Element: {                             │
│    tag: 42,                             │
│    type: "div",                         │
│    props: {                             │
│      onClick: [Function],               │
│      className: "...",                  │
│      ...                                │
│    }                                    │
│  }                                      │
└─────────────────┬───────────────────────┘
                  │ createInstance() called
                  ▼
┌─────────────────────────────────────────┐
│  NativeRenderer.createView()             │
│  1. Create FrameLayout (tag: 42)        │
│  2. Store props.onClick in event map    │
│  3. Register native click listener      │
└─────────────────┬───────────────────────┘
                  │
  ┌───────────────┴────────────────┐
  │                                │
  ▼                                ▼
┌──────────────────┐  ┌───────────────────────────┐
│  Event Map       │  │  Android View Listener    │
│  (In Memory)     │  │                          │
│  {               │  │  view.setOnClickListener{ │
│    42: {         │  │    event ->              │
│      onClick:fn  │  │    nativeBridge.emit(    │
│    }             │  │      "click",            │
│  }               │  │      { tag: 42, ... }    │
│                  │  │    )                     │
│                  │  │  }                       │
└──────────────────┘  └───────────────────────────┘
                  │
                  │ User taps screen
                  │
                  ▼
        ┌──────────────────────┐
        │  Native Layer Event   │
        │  FrameLayout#onClick  │
        │  → Listener fires     │
        └──────────┬────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Native Bridge Emit   │
        │  nativeBridge.emit(   │
        │    "click",          │
        │    {                 │
        │      tag: 42,        │
        │      x: 120,         │
        │      y: 400,         │
        │      timestamp: ..   │
        │    }                 │
        │  )                   │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────────────┐
        │  Hermes executes callback    │
        │                              │
        │  // From event map          │
        │  let callback = eventMap[42] │
        │  callback({ x, y, ... })    │
        │                              │
        │  // Original React handler  │
        │  handleOpenPeer("127.0.0.1") │
        │                              │
        │  // State update            │
        │  setRemoteHost("127.0.0.1") │
        │  setActiveTab("hook")       │
        └──────────┬───────────────────┘
                   │
                   ▼
        ┌──────────────────────────────┐
        │  React State Update           │
        │  → Triggers re-render         │
        │  → New element tree           │
        │  → Reconciler diffs/patches   │
        │  → Views updated on screen    │
        └──────────────────────────────┘
```

**Supported Events**:
- `onClick` / `onPress` - Tap/click
- `onLongPress` - Long tap (500ms+)
- `onFocus` / `onBlur` - Text input focus
- `onChange` / `onChangeText` - Input value changes
- `onScroll` - Scroll position changes
- `onLayout` - View dimensions/position ready
- `onTouchStart`, `onTouchMove`, `onTouchEnd` - Raw touch events

---

### System 4: Host-Provided Libraries (Shared Runtime)

**Goal**: React and utilities bundled once, shared by all hooks

**Architecture**:

The relay-client-android host app bundles and provides a **shared runtime** that all hooks depend on:

```
┌────────────────────────────────────────────────────────────┐
│  Host APK (relay-client-android)                           │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Bundled Libraries (in assets/)                      │ │
│  │  • react@18.x (production build)                     │ │
│  │  • react-dom@18.x (for DOM utilities, if needed)     │ │
│  │  • themed-styler (Rust via JNI)                      │ │
│  │  • hook-transpiler (Rust via JNI)                    │ │
│  │  • custom utilities (maybe a helpers lib)            │ │
│  │  • custom renderer (react-reconciler based)          │ │
│  │  • nativeBridge (JSI)                                │ │
│  └──────────────────────────────────────────────────────┘ │
│                         ↑                                  │
│                    Loaded into Hermes                      │
│                    at app startup                          │
│                                                            │
└────────────────────────────────────────────────────────────┘
                         ↑
                         │ globalThis exports
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ start.jsx    │  │ Remote Hook1  │  │ Remote Hook2  │
  │              │  │               │  │               │
  │ import React │  │ import React  │  │ import React  │
  │ from 'react' │  │ from 'react'  │  │ from 'react'  │
  │              │  │               │  │               │
  │ // Works!    │  │ // Works!     │  │ // Works!     │
  │ // Uses      │  │ // Uses host  │  │ // Uses host  │
  │ // host's    │  │ // React      │  │ // React      │
  │ // React     │  │               │  │               │
  └──────────────┘  └──────────────┘  └──────────────┘
```

**How It Works**:

1. **Host Initialization** (Kotlin, MainActivity)
   ```kotlin
   // Load React into Hermes
   val reactCode = readAsset("react.production.min.js")
   hermesInstance.evaluate(reactCode)
   
   // Expose as global
   // (Hermes automatically exports global variables from evaluated code)
   // globalThis.React = React (happens automatically)
   ```

2. **Hook's Import Statement** (JSX)
   ```javascript
   import React from 'react'
   // This resolves to globalThis.React (the host's copy)
   ```

3. **Result**: All hooks use the **same React instance**, not their own copies

**Benefits**:
- ✅ Single React runtime (memory efficient)
- ✅ Hooks can be tiny (only JSX + logic)
- ✅ No version mismatches
- ✅ Shared state possible via Context
- ✅ Easier testing and debugging

**Important**: Hooks cannot do:
```javascript
// ❌ This won't work - React isn't installed
import React from 'react'  // Where from? There's no node_modules

// ✅ This works - resolved from globalThis
import React from 'react'  // Transpiler rewrites to globalThis.React
```

The transpiler (hook-transpiler) will need to handle the rewrite:

```rust
// In hook-transpiler transpile() function:
// When it sees: import React from 'react'
// Rewrite to: const React = globalThis.React
//
// When it sees: import { useState } from 'react'
// Rewrite to: const { useState } = globalThis.React
```

---

### System 5: Hermes JSI Bridge

**Goal**: Bidirectional JS ↔ Kotlin communication with promises

**Kotlin Side**:
```kotlin
// In MainActivity or custom JSI setup
class NativeBridge {
    // Async functions (return Promise)
    fun createView(tag: Int, type: String, props: Map<String, Any>): Promise<Unit>
    fun updateProps(tag: Int, props: Map<String, Any>): Promise<Unit>
    fun removeView(tag: Int): Promise<Unit>
    fun transpile(code: String, filename: String): Promise<String>
    fun getStyles(selector: String, classes: List<String>): Promise<String>
    fun checkPort(host: String, port: Int): Promise<Boolean>
    
    // Event emission (from native → JS)
    fun emit(eventName: String, data: Map<String, Any>): Unit
}
```

**JavaScript Side**:
```javascript
// Global object injected by JSI
globalThis.nativeBridge = {
  createView(tag, type, props) {
    return new Promise((resolve, reject) => {
      // Native code resolves/rejects
    })
  },
  
  updateProps(tag, props) {
    // ...
  },
  
  removeView(tag) {
    // ...
  },
  
  transpile(code, filename) {
    // Calls Rust transpiler via JNI
  },
  
  getStyles(selector, classes) {
    // Calls Rust themed-styler via JNI
  },
  
  checkPort(host, port) {
    // Calls Rust network check via JNI
  }
}

// Event listener (from native → JS)
globalThis.nativeEventListeners = new Map()
globalThis.onNativeEvent = function(eventName, data) {
  const listeners = globalThis.nativeEventListeners.get(eventName)
  if (listeners) {
    listeners.forEach(cb => cb(data))
  }
}

// Global React (from host)
globalThis.React = React
```

**React Reconciler Integration**:
```javascript
const reconciler = createReconciler({
  // ... host config ...
  
  createInstance(type, props, rootContainer, hostContext) {
    const tag = generateTag()
    
    // Call native async
    nativeBridge.createView(tag, type, props).then(() => {
      console.log(`View created: tag=${tag}, type=${type}`)
    }).catch(err => {
      console.error(`Failed to create view: ${err}`)
    })
    
    return tag
  },
  
  // ...
})
```

---

## Transpiler Modifications Required

The hook-transpiler needs to handle **library imports** for the shared runtime environment:

### Current Behavior
```rust
// In hook-transpiler/src/lib.rs
// Transpiles: import React from 'react'
// To: const React = require('react')
```

### Required Behavior
```rust
// For Android/Hermes environment, detect library imports and rewrite:

// Input:
// import React, { useState } from 'react'
// import { Button } from '@clevertree/ui-kit'

// Output:
// const React = globalThis.React
// const { useState } = React
// const { Button } = globalThis['@clevertree/ui-kit']
```

**Hooks can only import from**:
- ✅ `'react'` → Host's React
- ✅ `'@clevertree/...'` → Host-provided packages
- ❌ `npm_package` → Not available (hooks are JSX-only)

---

## Implementation Phases

### Phase 1: Foundation (Current → Next 2-3 days)
**Goal**: Basic rendering loop working

- [ ] **1.1**: Implement Hermes JSI bridge in C++ (JSI wrapper)
- [ ] **1.2**: Create native bridge Kotlin module with promise support
- [ ] **1.3**: Connect renderer to emit native events
- [ ] **1.4**: Test: Simple `<div>Hello</div>` renders on screen

**Deliverable**: Black screen → white box with text "Hello"

---

### Phase 2: Styling System (Next 3-5 days)
**Goal**: themed-styler integration working

- [ ] **2.1**: Extend `ThemedStylerModule` to compute breakpoints
- [ ] **2.2**: Implement `applyLayoutStyles()` in NativeRenderer (flexbox simulation)
- [ ] **2.3**: Implement `applyAppearanceStyles()` (colors, backgrounds)
- [ ] **2.4**: Implement `applyTypographyStyles()` (fonts, sizes)
- [ ] **2.5**: Test: Styled boxes with colors, padding, text

**Deliverable**: Styled grid of colored boxes, responsive to theme changes

---

### Phase 3: Event System (Next 3-5 days)
**Goal**: Click/touch events working end-to-end

- [ ] **3.1**: Implement event map storage in NativeRenderer
- [ ] **3.2**: Register native click/touch listeners on views
- [ ] **3.3**: Implement event emission back to JS via bridge
- [ ] **3.4**: Test: Button clicks trigger React state changes
- [ ] **3.5**: Add long-press, scroll, input change events

**Deliverable**: Interactive buttons, input fields, list scrolling

---

### Phase 4: Full App Integration (Next 5-7 days)
**Goal**: start.jsx fully functional

- [ ] **4.1**: Ensure HookRenderer works with transpiler
- [ ] **4.2**: Implement Peers view with port checking
- [ ] **4.3**: Implement Debug tab with version display
- [ ] **4.4**: Implement remote hook loading from peers
- [ ] **4.5**: Tab navigation fully working
- [ ] **4.6**: Handle errors gracefully (transpile errors, network errors)

**Deliverable**: APK boots, shows peer list, can load/transpile remote hooks

---

### Phase 5: Testing & Polish (Next 3-5 days)
**Goal**: Stable, tablet-tested

- [ ] **5.1**: Build release APK
- [ ] **5.2**: Deploy to physical tablet (Android 8+)
- [ ] **5.3**: Test peer discovery on local network
- [ ] **5.4**: Test responsive UI (portrait/landscape)
- [ ] **5.5**: Test hook transpilation (complex JSX)
- [ ] **5.6**: Performance profiling (memory, CPU)
- [ ] **5.7**: Bug fixes and stability improvements

**Deliverable**: Stable APK running on tablet, all features functional

---

## Technical Decisions

### Styling Algorithm

Instead of CSS-in-JS or StyleSheet, use themed-styler directly:

```
1. Component renders: <div className="flex-row p-4 bg-blue-500">
2. Reconciler extracts: type="div", className="flex-row p-4 bg-blue-500"
3. NativeRenderer calls: ThemedStylerModule.getRnStyles(
     selector="div",
     classes=["flex-row", "p-4", "bg-blue-500"],
     themes=currentThemes
   )
4. Returns object with all computed properties
5. Apply directly to native view
```

**No caching initially** — compute on every render. Optimize later if needed.

---

### Layout System

Android native layout is FrameLayout-based with custom measurement logic.

```kotlin
// Pseudo-code for layout computation
class AndroidLayoutEngine {
    fun measure(view: View, constraints: LayoutConstraints): Size {
        val style = view.tag as? ViewStyle
        
        // Flex properties from themed-styler
        val flexDirection = style?.flexDirection ?: "column"
        val justifyContent = style?.justifyContent ?: "flex-start"
        val alignItems = style?.alignItems ?: "stretch"
        val flexWrap = style?.flexWrap ?: "nowrap"
        
        // Width/height
        val width = computeWidth(view, style, constraints)
        val height = computeHeight(view, style, constraints)
        
        // Layout children
        val children = view.children
        var childTop = style?.paddingTop ?: 0
        var childLeft = style?.paddingLeft ?: 0
        
        for (child in children) {
            val childSize = measure(child, ...)
            child.layout(childLeft, childTop, childLeft + childSize.width, childTop + childSize.height)
            // Update childTop/childLeft based on flexDirection
        }
        
        return Size(width, height)
    }
}
```

**Assumption**: Themed-styler will return `flexDirection`, `justifyContent`, `alignItems` (TailwindCSS-style).

---

### Event Delegation

Events stored in memory map, not on View object:

```kotlin
class EventMap {
    private val events = mutableMapOf<Int, Map<String, Function>>()
    
    fun register(tag: Int, eventName: String, callback: Function) {
        events.getOrPut(tag) { mutableMapOf() }[eventName] = callback
    }
    
    fun emit(tag: Int, eventName: String, data: Any): Unit = runJS {
        val callback = events[tag]?.get(eventName) ?: return
        callback(data)
    }
    
    fun unregister(tag: Int) {
        events.remove(tag)
    }
}
```

Why? Because Android View objects can't hold arbitrary function references safely across JNI boundaries.

---

### Hook Transpilation at Runtime (Host Bundles React)

**Host App Startup** (Kotlin):

```kotlin
// In MainActivity.onCreate()
val hermesInstance = HermesRuntime.create()

// 1. Load React into Hermes (from bundled assets)
val reactCode = readAsset("react.production.min.js")
hermesInstance.evaluate(reactCode)

// 2. Load custom renderer
val rendererCode = readAsset("renderer.js")
hermesInstance.evaluate(rendererCode)

// 3. Set up JSI bridge (nativeBridge)
setupJSIBridge(hermesInstance)

// 4. Load and transpile start.jsx
val startJsxCode = readAsset("start.jsx")
val transpiledCode = RustTranspilerModule.transpile(startJsxCode, "start.jsx")

// 5. Execute in Hermes
// Now start.jsx can: import React from 'react'
// Because globalThis.React is set from step 1
hermesInstance.evaluate(transpiledCode)
```

**Remote Hook Loading** (JavaScript):

```javascript
// All hooks (bundled + remote) share the same host-provided React

// start.jsx (in assets):
import React, { useState } from 'react'
const { nativeBridge } = globalThis
// Works! Uses host's React

// Remote hook (fetched from peer):
import React, { useState } from 'react'
const { nativeBridge } = globalThis
// Also works! Same shared React

export default function RemoteComponent() {
  const [data, setData] = useState(null)
  
  useEffect(() => {
    nativeBridge.checkPort('peer', 8080)
      .then(online => setData(online))
  }, [])
  
  return <div>{data ? 'Online' : 'Offline'}</div>
}
```

**Transpilation Flow** (hook-transpiler rewrites imports):

```
Hook Code:
  import React, { useState } from 'react'
  export default () => <div>Hello</div>

    ↓ Transpiler rewrites import

Transpiled Code:
  const { React, useState } = globalThis
  // or: const { default: React } = globalThis['react']
  
  globalThis.__hook_default = () => {
    // JSX converted to createElement
  }

    ↓ HookRenderer executes

Result:
  const module = { exports: {} }
  Function(transpiled)(globalThis)
  const HookComponent = globalThis.__hook_default
```

**Key Points**:
1. Host bundles React once into APK
2. All hooks (bundled and remote) use same React instance
3. Transpiler rewrites `import React from 'react'` → `globalThis.React`
4. No duplicate React copies = smaller memory, faster startup

---

## Data Flow Summary

```
User opens app
    ↓
MainActivity initializes Hermes
    ↓
Loads React from assets → globalThis.React
    ↓
Loads & transpiles start.jsx
    ↓
Executes in Hermes (can now use React)
    ↓
React renders <StartApp />
    ↓
Reconciler creates React element tree
    ↓
For each element: nativeBridge.createView(tag, type, props)
    ↓
Kotlin NativeRenderer.createView()
    ├─ Creates Android View (FrameLayout, TextView, etc.)
    ├─ Queries themed-styler for styles based on className
    ├─ Applies layout/appearance/typography styles
    └─ Registers event listeners
    ↓
Android view mounted in hierarchy
    ↓
View rendered on screen
    ↓
User taps/clicks
    ↓
Android listener fires
    ↓
nativeBridge.emit("click", { tag, x, y, ... })
    ↓
Hermes executes React callback
    ↓
State updates
    ↓
React re-renders (goto "React renders")
```

---

## Key Assumptions

1. **themed-styler** will be extended/adapted to output Android-friendly properties (DP values, native color ints, etc.)
2. **Hermes** JSI setup can be done in C++ following React Native patterns
3. **Kotlin** flexbox simulation is acceptable (not full CSS layout engine)
4. **No third-party React libraries** needed (no react-native, no web libraries)
5. **start.jsx** is simple enough to run in standalone Hermes without module system

---

## Open Questions

1. **Responsive breakpoints**: How does device detect it's on a tablet vs phone?
   - **Option A**: Check screen size in pixels, map to breakpoints
   - **Option B**: Hardcode based on device model
   - **Decision**: Option A — compute breakpoint in MainActivity, pass to JS

2. **Image loading**: How are images fetched?
   - **Option A**: URL-based (HTTP), Glide library
   - **Option B**: Base64 embedded in JS
   - **Decision**: Option A — add ImageLoadingModule for async image fetching

3. **Complex layouts**: Can flexbox simulation handle nested layouts?
   - **Option A**: Yes, recursive measurement algorithm
   - **Option B**: No, use Android native ConstraintLayout
   - **Decision**: Option A initially, evaluate performance

4. **Form validation**: How to implement complex forms?
   - **Decision**: Leave as exercise for hook components (can be done in JS)

---

## Testing Strategy

### Unit Tests (Rust)
- `hook-transpiler`: JSX transpilation correctness
- `themed-styler`: Style computation for various class combinations

### Integration Tests (Kotlin + JNI)
- Module loading and JNI communication
- View creation from various element types
- Style application correctness

### E2E Tests (On Device)
- start.jsx renders correctly
- Peer list populates
- Tapping peer opens hook renderer
- Remote hook loads and transpiles
- Remote UI renders and responds to taps

### Manual Testing (Tablet)
- Portrait/landscape rotation
- Tab navigation
- Long press and scroll
- Input field text entry
- Network errors (peer unreachable)

---

## Success Criteria

By end of Phase 5:
- [ ] APK compiles without errors
- [ ] APK installs on tablet (Android 8+)
- [ ] App boots and displays start.jsx UI
- [ ] Peer list shows and updates status
- [ ] Peer tapping opens remote hook
- [ ] Remote hook transpiles and renders
- [ ] All event handlers work (clicks, inputs)
- [ ] Styles applied correctly (colors, spacing)
- [ ] Responsive to orientation changes
- [ ] No crashes or ANR (Application Not Responding)

---

## Next Step

Once this plan is approved, begin with **Phase 1.1**: Implementing the Hermes JSI bridge.

The C++ code will:
1. Create a `nativeBridge` object in JSI
2. Expose methods like `createView()`, `updateProps()`, etc.
3. Handle promise resolution/rejection from native calls
4. Set up event emission callbacks

This is the critical piece — everything else depends on it.
