# ACT Library Migration Plan

## Overview

Replace React with a custom minimal library called **'act'** that provides essential reactive primitives optimized for our hook-transpiler + QuickJS architecture. This eliminates ~40KB of React overhead and gives us full control over the rendering lifecycle.

## Goals

1. **Minimal footprint**: <5KB minified (vs React's ~40KB)
2. **Zero dependencies**: Pure ES5/ES6 JavaScript
3. **Hook-compatible API**: Drop-in replacement for common React hooks
4. **Native-optimized**: Designed specifically for nativeBridge rendering
5. **Transpiler-friendly**: Works seamlessly with hook-transpiler's JSX transform

## Core API Surface

### Hooks (Priority 1 - Essential)

```javascript
// State management
const [state, setState] = act.useState(initialValue)

// Side effects
act.useEffect(() => {
  // Effect logic
  return () => {
    // Cleanup
  }
}, [dependencies])

// Refs
const ref = act.useRef(initialValue)

// Memoization
const memoized = act.useMemo(() => expensiveComputation(), [deps])
const callback = act.useCallback(() => handler(), [deps])
```

### Component Model

```javascript
// Functional components (only style supported)
function MyComponent(props) {
  const [count, setCount] = act.useState(0)
  
  return act.createElement('div', {
    className: 'container',
    onClick: () => setCount(count + 1)
  }, [
    act.createElement('span', { text: `Count: ${count}` })
  ])
}

// Or with JSX (transpiled by hook-transpiler)
function MyComponent(props) {
  const [count, setCount] = act.useState(0)
  
  return (
    <div className="container" onClick={() => setCount(count + 1)}>
      <span text={`Count: ${count}`} />
    </div>
  )
}
```

### Rendering

```javascript
// Mount a component to native bridge
act.render(
  act.createElement(MyComponent, { prop: 'value' }),
  rootTag  // Native view tag, e.g., -1 for root container
)

// Unmount
act.unmount(rootTag)
```

## Architecture

### Virtual DOM (Simplified)

```
┌─────────────────────────────────────┐
│  JSX (transpiled by hook-transpiler)│
│  <div className="p-4">Hello</div>   │
└──────────────┬──────────────────────┘
               │ Transpiler outputs:
               ▼
┌──────────────────────────────────────────────────┐
│  act.createElement('div', {                      │
│    className: 'p-4',                             │
│    children: ['Hello']                           │
│  })                                              │
└──────────────┬───────────────────────────────────┘
               │ Creates VNode:
               ▼
┌──────────────────────────────────────────────────┐
│  VNode {                                         │
│    type: 'div',                                  │
│    props: { className: 'p-4' },                  │
│    children: ['Hello'],                          │
│    _tag: null,  // Native tag assigned on mount │
│    _hooks: []   // Hook state storage            │
│  }                                               │
└──────────────┬───────────────────────────────────┘
               │ Reconcile & render:
               ▼
┌──────────────────────────────────────────────────┐
│  nativeBridge.createView(tag, 'div', {           │
│    className: 'p-4',                             │
│    text: 'Hello'                                 │
│  })                                              │
└──────────────────────────────────────────────────┘
```

### Reconciliation (Minimal Diffing)

Instead of React's complex fiber architecture, use a simple tree-diff:

1. **Mount**: Create all new VNodes and call `nativeBridge.createView()`
2. **Update**: Compare old/new VNode props, call `nativeBridge.updateProps()` if changed
3. **Unmount**: Call `nativeBridge.removeView()` and cleanup hooks

**Key optimization**: No VDOM tree traversal needed since native bridge handles actual rendering. We only track which VNodes map to which native tags.

### Hook State Storage

```javascript
// Per-component hook state
const componentState = new Map() // componentId -> { hooks: [], cursor: 0 }

function useState(initial) {
  const component = getCurrentComponent()
  const state = componentState.get(component.id)
  
  if (state.cursor >= state.hooks.length) {
    // First render: initialize
    state.hooks.push({
      type: 'state',
      value: initial,
      setter: (newValue) => {
        state.hooks[state.cursor].value = newValue
        scheduleRerender(component)
      }
    })
  }
  
  const hook = state.hooks[state.cursor++]
  return [hook.value, hook.setter]
}
```

## Implementation Phases

### Phase 1: Core Runtime (Week 1)
- [ ] `createElement()` - Create VNode structure
- [ ] `render()` - Mount component tree to native bridge
- [ ] Basic reconciliation (mount/unmount only)
- [ ] Component instance tracking

**Deliverable**: Can render static JSX trees via `act.render()`

### Phase 2: State Hooks (Week 1-2)
- [ ] `useState()` - State management with setters
- [ ] Hook state storage per component
- [ ] Rerender scheduling and batching
- [ ] `useRef()` - Mutable refs

**Deliverable**: Interactive components with state updates

### Phase 3: Effect Hooks (Week 2)
- [ ] `useEffect()` - Side effect management
- [ ] Dependency tracking
- [ ] Cleanup function handling
- [ ] Effect execution scheduling (after render)

**Deliverable**: Components with lifecycle effects (data fetching, subscriptions)

### Phase 4: Performance Hooks (Week 3)
- [ ] `useMemo()` - Memoize expensive computations
- [ ] `useCallback()` - Memoize callbacks
- [ ] Dependency comparison utilities

**Deliverable**: Optimized re-renders for expensive operations

### Phase 5: Advanced Features (Week 3-4)
- [ ] `useContext()` - Context API for prop drilling
- [ ] `useReducer()` - Complex state management
- [ ] Error boundaries (try/catch in render)
- [ ] Portals (render to different native containers)

**Deliverable**: Feature parity with common React patterns

## Migration Strategy

### Step 1: Parallel Runtime
- Keep React bundled alongside 'act'
- Introduce `act` as `globalThis.Act`
- Test 'act' with new components
- Gradually migrate existing components

### Step 2: Transpiler Updates
Update hook-transpiler to support both:
```javascript
// React mode (default, current)
import React from 'react'
export default () => <div>Hello</div>

// Act mode (new)
import act from 'act'
export default () => <div>Hello</div>
```

Transpiler detects `import act` and rewrites:
- `import act from 'act'` → `const act = globalThis.Act`
- JSX pragma: `_jsx()` → `act.createElement()`

### Step 3: Bundle Optimization
Once all components migrated:
- Remove React from `assets/react.production.min.js`
- Bundle 'act' instead (~5KB vs ~40KB)
- Update `QuickJSManager` to load `act.min.js`

### Step 4: Validation
- [ ] Run all existing hooks through transpiler with 'act'
- [ ] Verify rendering matches React version
- [ ] Performance benchmarks (memory, startup time)
- [ ] Update docs and examples

## File Structure

```
relay-client-android/
├── act/                          # New act library
│   ├── src/
│   │   ├── index.js              # Main entry point
│   │   ├── createElement.js      # VNode creation
│   │   ├── render.js             # Reconciler
│   │   ├── hooks/
│   │   │   ├── useState.js
│   │   │   ├── useEffect.js
│   │   │   ├── useRef.js
│   │   │   ├── useMemo.js
│   │   │   ├── useCallback.js
│   │   │   ├── useContext.js
│   │   │   └── useReducer.js
│   │   └── utils/
│   │       ├── scheduler.js      # Rerender batching
│   │       ├── diff.js           # Minimal diffing
│   │       └── component.js      # Component tracking
│   ├── dist/
│   │   └── act.min.js            # Bundled & minified
│   ├── package.json
│   ├── rollup.config.js          # Bundle config
│   └── README.md
│
├── android/app/src/main/assets/
│   ├── act.min.js                # Replaces react.production.min.js
│   └── start.jsx                 # Update imports to 'act'
```

## Transpiler Integration

### Current JSX Transform (React)
```javascript
// Input:
import React from 'react'
export default () => <div>Hello</div>

// Output (hook-transpiler):
const React = globalThis.React
export default () => React.createElement('div', {}, 'Hello')
```

### New JSX Transform (Act)
```javascript
// Input:
import act from 'act'
export default () => <div>Hello</div>

// Output (hook-transpiler):
const act = globalThis.Act
export default () => act.createElement('div', {}, 'Hello')
```

**Transpiler Changes Required**:
1. Detect `import act from 'act'` (or `import { useState } from 'act'`)
2. Rewrite import to `const act = globalThis.Act`
3. Set JSX pragma to `act.createElement` (instead of `React.createElement`)
4. Handle named imports: `import { useState } = 'act'` → `const { useState } = globalThis.Act`

## Native Bridge Interface

The 'act' library will use the exact same nativeBridge API as React:

```javascript
// Create view
nativeBridge.createView(tag, type, props)

// Update props
nativeBridge.updateProps(tag, props)

// Remove view
nativeBridge.removeView(tag)

// Manage children
nativeBridge.addChild(parentTag, childTag, index)
nativeBridge.removeChild(parentTag, childTag)
```

**No changes needed** in QuickJSManager, NativeBridge, or NativeRenderer!

## Performance Expectations

### Bundle Size
- React production: ~40KB minified + gzipped
- Act library: <5KB minified + gzipped
- **Savings**: ~35KB per APK (~87% reduction)

### Startup Time
- React parse/eval: ~50-80ms on mid-range device
- Act parse/eval: <10ms (estimated)
- **Savings**: ~40-70ms faster cold start

### Memory
- React runtime: ~2-3MB baseline
- Act runtime: <500KB (estimated)
- **Savings**: ~1.5-2.5MB per app instance

### Rendering
- Similar performance (both call same nativeBridge)
- Potential gains from simpler reconciliation
- Less GC pressure from smaller object graphs

## Risks & Mitigations

### Risk 1: Hook Complexity
**Issue**: React's hooks have subtle edge cases (closure capture, stale deps, etc.)
**Mitigation**: 
- Start with simple useState/useEffect
- Add extensive test suite
- Document differences from React

### Risk 2: Ecosystem Compatibility
**Issue**: Third-party hooks expect React internals
**Mitigation**:
- Only use first-party hooks
- Document unsupported patterns
- Provide migration guide

### Risk 3: Development Time
**Issue**: Building a React-like library is non-trivial
**Mitigation**:
- Phased rollout (core → hooks → advanced)
- Keep React as fallback during migration
- Leverage existing transpiler for JSX

## Testing Strategy

### Unit Tests
```javascript
describe('act.useState', () => {
  it('returns initial value', () => {
    const [count] = act.useState(0)
    expect(count).toBe(0)
  })
  
  it('updates value on setState', () => {
    const [count, setCount] = act.useState(0)
    setCount(1)
    // Trigger rerender
    expect(count).toBe(1)
  })
})
```

### Integration Tests
- Render complex component trees
- Test hook interactions (useState + useEffect)
- Verify native bridge calls match expectations
- Memory leak detection

### Compatibility Tests
- Transpile all existing hooks with 'act' mode
- Compare rendered output vs React version
- Performance benchmarks

## Timeline

- **Week 1**: Core runtime + useState
- **Week 2**: useEffect + useRef
- **Week 3**: Memoization hooks + context
- **Week 4**: Testing, docs, migration

**Total**: ~4 weeks to full feature parity

## Success Metrics

- [ ] 90%+ bundle size reduction (vs React)
- [ ] <10ms library load time
- [ ] Zero runtime errors in test suite
- [ ] All existing hooks render identically
- [ ] Documentation complete
- [ ] Migration guide published

## Open Questions

1. **Class components?** 
   - Decision: No. Functional-only. Simpler and lighter.

2. **Concurrent rendering?**
   - Decision: Not initially. Add if needed for performance.

3. **Suspense/lazy loading?**
   - Decision: Phase 2 feature. Focus on core hooks first.

4. **Server-side rendering?**
   - Decision: Out of scope. Mobile-only for now.

5. **DevTools integration?**
   - Decision: Nice-to-have. Chrome extension for debugging VNodes.

## References

- [Preact](https://github.com/preactjs/preact) - Similar minimal React alternative (~3KB)
- [Inferno](https://github.com/infernojs/inferno) - Fast React-like library
- [React Reconciliation](https://reactjs.org/docs/reconciliation.html)
- [React Hooks RFC](https://github.com/reactjs/rfcs/blob/main/text/0068-react-hooks.md)

## WASM/JNI Performance Ideas

- Push VNode diffing and prop normalization into Rust and expose via WASM to cut JS allocations; reuse the existing hook-transpiler wasm-pack pipeline.
- Batch native operations over JNI (e.g., create/update/remove/addChild arrays) to shrink message-queue churn on heavy renders.
- Precompute className → style maps in Rust/themed-styler, cache them in WASM, and send hashed style payloads to JNI to avoid repeated JSON parsing.
- Experiment with a WASM-based hook scheduler (state/effect bookkeeping) to reduce GC churn during frequent setState calls.
- Bench plan: render 10k simple nodes and compare JS vs WASM vs JNI-patched timings on a mid-range Android device.

## Next Steps (Immediate)

1. Create `relay-client-android/act/` directory structure
2. Implement `createElement()` and basic VNode
3. Wire up to nativeBridge in proof-of-concept
4. Test with simple `<div>Hello</div>` component
5. Compare bundle size vs React

---

**Status**: Planning Phase  
**Owner**: TBD  
**Target Start**: Q1 2026  
**Target Completion**: Q1 2026 (4 weeks)
