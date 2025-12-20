# Architecture Decision: Host-Bundled React for All Hooks

> Note (Dec 2025): The Android runtime now uses QuickJS instead of Hermes. The concepts below still apply; references to Hermes describe the previous prototype.

## Decision

**React is bundled once in the host APK** and all hooks (both bundled start.jsx and remote hooks) share the same React instance via globalThis.

## Rationale

### Why Bundle React in Host?
1. **Single copy** → Minimal APK size increase (~40KB minified)
2. **Consistent state** → All hooks use same React, same state management
3. **No versioning conflicts** → Only one version of React loaded
4. **Predictable behavior** → All hooks guaranteed to use latest React in host

### Why Hooks Cannot Bundle Their Own?
1. **Transpilation vs Bundling** → Hooks are transpiled, not bundled
2. **No npm in hook environment** → hook-transpiler runs in Hermes, not Node.js
3. **Library imports not supported** → hooks can only import from host-provided globals
4. **Efficiency** → Avoid duplicate React in memory

## Implementation

### Host Setup (MainActivity.kt)

```kotlin
// 1. Load React into Hermes
val reactCode = readAsset("react.production.min.js")
hermesInstance.evaluate(reactCode)

// 2. Load remote hooks can now do:
//    import React from 'react'
//    // Uses globalThis.React from step 1
```

### Hook-Transpiler Behavior

```javascript
// Hook source code:
import React, { useState } from 'react'
export default () => <div>Hello</div>

// Transpiler rewrites to:
const React = globalThis.React
const { useState } = React
// (rest of code)
```

### What Hooks Can Import

✅ **Can Import:**
- `'react'` → Resolves to `globalThis.React` (host-bundled)
- `'@clevertree/ui-kit'` → Resolves to `globalThis['@clevertree/ui-kit']` (host-provided utilities)

❌ **Cannot Import:**
- `'lodash'` → Not in host, not available
- `'axios'` → Not in host, not available
- Any external npm package → Host decides what's available

## Design Pattern

```
Host Responsibilities:
├─ Bundle React + ReactDOM
├─ Expose native capabilities (file I/O, network, etc.)
├─ Provide optional utilities (@clevertree/*)
└─ Run hook-transpiler for JSX

Hook Responsibilities:
├─ JSX code only (no bundling)
├─ Use React from globalThis.React
├─ Use capabilities from nativeBridge
└─ Implement UI/UX logic
```

## Benefits for Relay Architecture

### Peer-to-Peer Hooks
Multiple peers can send hooks to this client:
```
peer1:8080 → fetch /hooks/app.jsx
peer2:8081 → fetch /hooks/settings.jsx
peer3:8082 → fetch /hooks/gallery.jsx

All transpiled & executed in same Hermes context
All use same React instance
Can communicate via Context/state
```

### App State Sharing
```jsx
// In host's start.jsx
const [userData, setUserData] = useState(null)

<SharedContext.Provider value={userData}>
  <HookRenderer host={peer1} />  // Can access userData
  <HookRenderer host={peer2} />  // Can access userData
</SharedContext.Provider>
```

### Minimal Bandwidth
Remote hooks are JSX-only:
```javascript
// Remote hook file (just transpiled JSX + logic)
export default function() {
  const { React, useState } = globalThis
  const [count, setCount] = useState(0)
  return <div>{count}</div>
}

// Size: ~200 bytes (not 50KB like bundled React)
```

## Changes to IMPLEMENTATION_PLAN.md

1. **System 4** renamed to **System 4: Host-Provided Libraries (Shared Runtime)**
   - Explains React bundling and globalThis exports
   - Shows how hooks resolve imports

2. **System 5** (formerly System 4) **Hermes JSI Bridge**
   - Unchanged, but depends on System 4

3. **Transpiler Modifications Section** added
   - Specifies how hook-transpiler rewrites imports
   - Documents which imports are allowed

4. **Hook Transpilation at Runtime** completely rewritten
   - Shows Android startup sequence
   - Demonstrates React loading before start.jsx
   - Explains remote hook execution with shared React

## Timeline Impact

- **No additional work** in Phase 1 (JSI bridge setup loads React before hooks)
- **Transpiler update** needed in parallel (import rewriting)
- All subsequent phases unaffected

## Future Platforms

This pattern scales to iOS, web, desktop:

**iOS:**
```swift
// Load React into JSValue
let react = try loadAsset("react.js")
jsContext.evaluateScript(react)
// All hooks access globalThis.React
```

**Web:**
```javascript
// Load React in script tag
<script src="react.production.js"></script>
// All hooks access globalThis.React
```

**Hermes** (this project):
```kotlin
val reactCode = readAsset("react.production.min.js")
hermesInstance.evaluate(reactCode)
// All hooks access globalThis.React
```

Same transpiler, same hook format, different host runtimes.
