"use strict";
(() => {
  // src/core.js
  function createAct(renderer2) {
    let tagCounter = 1;
    let rootComponent = null;
    let rootProps = {};
    let renderQueued = false;
    let isRendering = false;
    let componentState = {};
    let hookCursor = {};
    let pendingEffects = [];
    let pendingLayoutEffects = [];
    let currentPath = "root";
    let suspenseStack = [];
    let nextUnitOfWork = null;
    let wipRoot = null;
    let currentRoot = null;
    let deletions = [];
    const SUSPENSE_TYPE = typeof Symbol !== "undefined" ? /* @__PURE__ */ Symbol.for("act.suspense") : "__act_suspense__";
    const FRAGMENT_TYPE = typeof Symbol !== "undefined" ? /* @__PURE__ */ Symbol.for("act.fragment") : "__act_fragment__";
    function log(level, message) {
      try {
        const g2 = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : {};
        const msg = `[act] ${message}`;
        if (g2.console && typeof g2.console.log === "function") {
          g2.console.log(msg);
        }
        if (typeof g2.__android_log === "function") {
          g2.__android_log(level.toUpperCase(), msg);
        } else if (g2.__android_log) {
          try {
            g2.__android_log(level.toUpperCase(), msg);
          } catch (e) {
          }
        }
      } catch (e) {
      }
    }
    function emitError(message) {
      log("error", message);
    }
    function flattenChildren(args) {
      const out = [];
      for (let i = 2; i < args.length; i++) {
        const child = args[i];
        if (Array.isArray(child)) {
          for (let j = 0; j < child.length; j++) {
            const c = child[j];
            if (c !== void 0 && c !== null && c !== false) {
              out.push(c);
            }
          }
        } else if (child !== void 0 && child !== null && child !== false) {
          out.push(child);
        }
      }
      return out;
    }
    function createElement(type, props, ...childrenArgs) {
      const p = props || {};
      let children = flattenChildren([type, props, ...childrenArgs]);
      const testId2 = p["test-id"] || p["testId"] || "";
      if (children.length === 0 && p.children) {
        log("info", `[createElement] ${type}${testId2 ? " (" + testId2 + ")" : ""}: Using p.children, isArray=${Array.isArray(p.children)}, type=${typeof p.children}`);
        if (Array.isArray(p.children)) {
          for (let i = 0; i < p.children.length; i++) {
            const c = p.children[i];
            if (Array.isArray(c)) {
              log("info", `[createElement] ${type}: p.children[${i}] is nested array with ${c.length} items`);
              for (let j = 0; j < c.length; j++) {
                if (c[j] !== void 0 && c[j] !== null && c[j] !== false) {
                  children.push(c[j]);
                }
              }
            } else if (c !== void 0 && c !== null && c !== false) {
              children.push(c);
            }
          }
        } else {
          children = [p.children];
        }
      }
      if (children.length > 0) {
        p.children = children.length === 1 ? children[0] : children;
      }
      log("info", `[createElement] ${type}${testId2 ? " (" + testId2 + ")" : ""}: final children count=${children.length}`);
      return { type, props: p, children };
    }
    function resetTags() {
      tagCounter = 1;
    }
    function nextTag() {
      return tagCounter++;
    }
    function makePath(parent, key) {
      return parent ? parent + "." + key : String(key);
    }
    function resetHookCursor(path) {
      hookCursor[path] = 0;
    }
    function nextHookIndex(path) {
      const idx = hookCursor[path] !== void 0 ? hookCursor[path] : 0;
      hookCursor[path] = idx + 1;
      return idx;
    }
    function getHookSlot(path, index) {
      let state = componentState[path];
      if (!state) {
        state = { hooks: [] };
        componentState[path] = state;
      }
      if (!state.hooks[index]) {
        state.hooks[index] = {};
      }
      return state.hooks[index];
    }
    function shallowDepsChanged(prev, next) {
      if (!prev || !next) return true;
      if (prev.length !== next.length) return true;
      for (let i = 0; i < prev.length; i++) {
        if (prev[i] !== next[i]) return true;
      }
      return false;
    }
    function shallowEqual(objA, objB) {
      if (objA === objB) return true;
      if (typeof objA !== "object" || objA === null || typeof objB !== "object" || objB === null) return false;
      const keysA = Object.keys(objA);
      const keysB = Object.keys(objB);
      if (keysA.length !== keysB.length) return false;
      for (let i = 0; i < keysA.length; i++) {
        if (!Object.prototype.hasOwnProperty.call(objB, keysA[i]) || objA[keysA[i]] !== objB[keysA[i]]) {
          return false;
        }
      }
      return true;
    }
    function scheduleRender() {
      if (!rootComponent) return;
      if (renderQueued) return;
      renderQueued = true;
      const g2 = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : {};
      if (typeof g2.requestAnimationFrame === "function") {
        g2.requestAnimationFrame(renderNow);
      } else {
        setTimeout(renderNow, 0);
      }
    }
    function workLoop(deadline) {
      let shouldYield = false;
      while (nextUnitOfWork && !shouldYield) {
        nextUnitOfWork = performUnitOfWork(nextUnitOfWork);
        shouldYield = deadline.timeRemaining() < 1;
      }
      if (!nextUnitOfWork && wipRoot) {
        commitRoot();
      }
      if (nextUnitOfWork) {
        const g2 = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : {};
        const ric = g2.requestIdleCallback || ((cb) => {
          const start = Date.now();
          return setTimeout(() => {
            cb({
              didTimeout: false,
              timeRemaining: () => Math.max(0, 50 - (Date.now() - start))
            });
          }, 1);
        });
        ric(workLoop);
      }
    }
    function performUnitOfWork(fiber) {
      try {
        const isFunctionComponent = typeof fiber.type === "function";
        if (isFunctionComponent) {
          updateFunctionComponent(fiber);
        } else if (fiber.type === SUSPENSE_TYPE) {
          updateSuspenseComponent(fiber);
        } else {
          updateHostComponent(fiber);
        }
      } catch (e) {
        if (e && typeof e.then === "function") {
          let suspenseFiber = fiber;
          while (suspenseFiber && suspenseFiber.type !== SUSPENSE_TYPE) {
            suspenseFiber = suspenseFiber.parent;
          }
          if (suspenseFiber && !suspenseFiber.didThrow) {
            suspenseFiber.didThrow = true;
            e.then(() => {
              scheduleRender();
            }, () => {
              scheduleRender();
            });
            return suspenseFiber;
          }
        }
        let boundary = fiber.parent;
        while (boundary) {
          if (boundary.props && typeof boundary.props.componentDidCatch === "function") {
            if (!boundary.hasError) {
              boundary.hasError = true;
              boundary.error = e;
              boundary.props.componentDidCatch(e, {
                componentStack: boundary.path || "unknown"
              });
              return boundary;
            }
          }
          boundary = boundary.parent;
        }
        throw e;
      }
      if (fiber.child) {
        return fiber.child;
      }
      let nextFiber = fiber;
      while (nextFiber) {
        if (nextFiber.sibling) {
          return nextFiber.sibling;
        }
        nextFiber = nextFiber.parent;
      }
      return null;
    }
    function updateFunctionComponent(fiber) {
      const path = fiber.path || "root";
      resetHookCursor(path);
      const prevPath = currentPath;
      currentPath = path;
      let children;
      if (fiber.hasError) {
        if (fiber.props && fiber.props.fallback) {
          const fallbackResult = typeof fiber.props.fallback === "function" ? fiber.props.fallback(fiber.error) : fiber.props.fallback;
          children = Array.isArray(fallbackResult) ? fallbackResult : [fallbackResult];
        } else {
          children = [];
        }
      } else {
        try {
          const result = fiber.type(fiber.props);
          children = Array.isArray(result) ? result : [result];
        } catch (e) {
          currentPath = prevPath;
          if (e && typeof e.then === "function") {
            throw e;
          } else {
            throw e;
          }
        } finally {
          if (currentPath === path) {
            currentPath = prevPath;
          }
        }
      }
      reconcileChildren(fiber, children);
    }
    function updateSuspenseComponent(fiber) {
      let children;
      if (fiber.didThrow) {
        const fallback = fiber.props.fallback;
        children = fallback !== void 0 && fallback !== null ? Array.isArray(fallback) ? fallback : [fallback] : [];
        fiber.showingFallback = true;
      } else {
        const rawChildren = fiber.props.children;
        children = rawChildren !== void 0 && rawChildren !== null ? Array.isArray(rawChildren) ? rawChildren : [rawChildren] : [];
        fiber.showingFallback = false;
      }
      const prevPath = currentPath;
      currentPath = makePath(fiber.path, "s");
      reconcileChildren(fiber, children);
      currentPath = prevPath;
    }
    function updateHostComponent(fiber) {
      if (fiber.hasError && fiber.props && fiber.props.fallback) {
        const fallbackResult = typeof fiber.props.fallback === "function" ? fiber.props.fallback(fiber.error) : fiber.props.fallback;
        const children2 = Array.isArray(fallbackResult) ? fallbackResult : [fallbackResult];
        reconcileChildren(fiber, children2);
        return;
      }
      if (!fiber.dom && renderer2.createInstance && typeof fiber.type === "string") {
        fiber.dom = renderer2.createInstance(fiber.type, fiber.props || {}, {
          nextTag,
          log
        });
      }
      let children = fiber.props && fiber.props.children !== void 0 && fiber.props.children !== null ? fiber.props.children : [];
      if (!Array.isArray(children)) {
        children = [children];
      }
      const flatChildren = [];
      for (let i = 0; i < children.length; i++) {
        const child = children[i];
        if (Array.isArray(child)) {
          flatChildren.push(...child);
        } else {
          flatChildren.push(child);
        }
      }
      reconcileChildren(fiber, flatChildren);
    }
    function reconcileChildren(wipFiber, elements) {
      let index = 0;
      let oldFiber = wipFiber.alternate && wipFiber.alternate.child;
      let prevSibling = null;
      let flatElements = [];
      if (Array.isArray(elements)) {
        for (let i = 0; i < elements.length; i++) {
          const item = elements[i];
          if (Array.isArray(item)) {
            flatElements.push(...item);
          } else {
            flatElements.push(item);
          }
        }
      } else if (elements !== void 0 && elements !== null && elements !== false) {
        flatElements = [elements];
      }
      if (wipFiber.props && wipFiber.props["test-id"]) {
        log("info", `Reconciling children for ${wipFiber.type} (${wipFiber.props["test-id"]}): ${flatElements.length} elements`);
      }
      while (index < flatElements.length || oldFiber != null) {
        let element = flatElements[index];
        let newFiber = null;
        const isText = typeof element === "string" || typeof element === "number";
        if (isText) {
          element = {
            type: "TEXT_ELEMENT",
            props: {
              nodeValue: String(element),
              children: []
            }
          };
        }
        const sameType = oldFiber && element && element.type == oldFiber.type;
        if (sameType) {
          for (let d = 0; d < deletions.length; d++) {
            if (deletions[d] === oldFiber) {
              deletions.splice(d, 1);
              break;
            }
          }
          newFiber = {
            type: oldFiber.type,
            props: element.props,
            dom: oldFiber.dom,
            parent: wipFiber,
            alternate: oldFiber,
            effectTag: "UPDATE",
            path: makePath(wipFiber.path, index)
          };
        } else {
          if (element) {
            newFiber = {
              type: element.type,
              props: element.props,
              dom: null,
              parent: wipFiber,
              alternate: null,
              effectTag: "PLACEMENT",
              path: makePath(wipFiber.path, index)
            };
          }
          if (oldFiber) {
            let alreadyInDeletions = false;
            for (let d = 0; d < deletions.length; d++) {
              if (deletions[d] === oldFiber) {
                alreadyInDeletions = true;
                break;
              }
            }
            if (!alreadyInDeletions) {
              oldFiber.effectTag = "DELETION";
              deletions.push(oldFiber);
            }
          }
        }
        if (oldFiber) {
          oldFiber = oldFiber.sibling;
        }
        if (index === 0) {
          wipFiber.child = newFiber;
        } else if (newFiber) {
          prevSibling.sibling = newFiber;
        }
        if (newFiber) {
          prevSibling = newFiber;
        }
        index++;
      }
    }
    function commitRoot() {
      deletions.forEach(commitWork);
      commitWork(wipRoot);
      currentRoot = wipRoot;
      wipRoot = null;
      flushLayoutEffects();
      flushEffects();
    }
    function commitWork(fiber) {
      if (!fiber) return;
      let domParentFiber = fiber.parent;
      while (domParentFiber && !domParentFiber.dom) {
        domParentFiber = domParentFiber.parent;
      }
      const domParent = domParentFiber ? domParentFiber.dom : renderer2.container;
      if (fiber.effectTag === "PLACEMENT" && fiber.dom != null) {
        if (renderer2.appendChild) {
          renderer2.appendChild(domParent, fiber.dom);
        }
      } else if (fiber.effectTag === "UPDATE" && fiber.dom != null) {
        if (renderer2.commitUpdate) {
          renderer2.commitUpdate(fiber.dom, fiber.alternate ? fiber.alternate.props : {}, fiber.props);
        }
      } else if (fiber.effectTag === "DELETION") {
        commitDeletion(fiber, domParent);
        return;
      }
      commitWork(fiber.child);
      commitWork(fiber.sibling);
    }
    function commitDeletion(fiber, domParent) {
      if (!fiber) return;
      if (fiber.dom) {
        if (renderer2.removeChild) {
          try {
            renderer2.removeChild(domParent, fiber.dom);
          } catch (e) {
            if (e.message.indexOf("not a child") === -1) {
              log("error", `removeChild failed: ${e.message}`);
            }
          }
        }
      } else {
        let child = fiber.child;
        while (child) {
          commitDeletion(child, domParent);
          child = child.sibling;
        }
      }
    }
    function flushWork() {
      if (renderQueued) {
        renderNow();
      }
    }
    function pushSuspense(fallback) {
      suspenseStack.push(fallback);
    }
    function popSuspense() {
      if (suspenseStack.length > 0) {
        suspenseStack.pop();
      }
    }
    function currentSuspenseFallback() {
      return suspenseStack.length > 0 ? suspenseStack[suspenseStack.length - 1] : null;
    }
    function memo(fn, compare) {
      return {
        type: "memo",
        fn,
        compare: compare || shallowEqual
      };
    }
    function renderComponent(fn, props, path) {
      let componentFn = fn;
      let isMemo = false;
      let compare = null;
      if (fn && typeof fn === "object" && fn.type === "memo") {
        componentFn = fn.fn;
        isMemo = true;
        compare = fn.compare;
      }
      if (typeof componentFn !== "function") return fn;
      let state = componentState[path];
      if (state && state.type !== componentFn) {
        state = { hooks: [], type: componentFn };
        componentState[path] = state;
      } else if (!state) {
        state = { hooks: [], type: componentFn };
        componentState[path] = state;
      }
      if (isMemo && state.prevProps && compare(state.prevProps, props)) {
        if (state.prevVNode) {
          return state.prevVNode;
        }
      }
      state.prevProps = props;
      resetHookCursor(path);
      const prevPath = currentPath;
      currentPath = path;
      try {
        const vnode = componentFn(props || {});
        state.prevVNode = vnode;
        currentPath = prevPath;
        return vnode;
      } catch (e) {
        currentPath = prevPath;
        if (e && typeof e.then === "function") {
          throw e;
        }
        emitError(`renderComponent failed at ${path}: ${e.message || String(e)}`);
        throw e;
      }
    }
    function flushEffects() {
      const effects = [...pendingEffects];
      pendingEffects.length = 0;
      for (const item of effects) {
        if (!item || !item.hook || typeof item.effect !== "function") continue;
        if (typeof item.hook.cleanup === "function") {
          try {
            item.hook.cleanup();
          } catch (e) {
            log("error", `effect cleanup failed: ${e.message}`);
          }
        }
        try {
          const nextCleanup = item.effect();
          if (typeof nextCleanup === "function") {
            item.hook.cleanup = nextCleanup;
          } else {
            item.hook.cleanup = null;
          }
          item.hook.deps = item.deps;
        } catch (e) {
          log("error", `effect error: ${e.message}`);
        }
      }
    }
    function flushLayoutEffects() {
      const effects = [...pendingLayoutEffects];
      pendingLayoutEffects.length = 0;
      for (const item of effects) {
        if (!item || !item.hook || typeof item.effect !== "function") continue;
        if (typeof item.hook.layoutCleanup === "function") {
          try {
            item.hook.layoutCleanup();
          } catch (e) {
            log("error", `layout effect cleanup failed: ${e.message}`);
          }
        }
        try {
          const nextCleanup = item.effect();
          if (typeof nextCleanup === "function") {
            item.hook.layoutCleanup = nextCleanup;
          } else {
            item.hook.layoutCleanup = null;
          }
          item.hook.deps = item.deps;
        } catch (e) {
          log("error", `layout effect error: ${e.message}`);
        }
      }
    }
    function renderNow() {
      renderQueued = false;
      if (isRendering) return;
      if (!rootComponent) return;
      isRendering = true;
      try {
        let children;
        if (rootComponent && typeof rootComponent === "object" && rootComponent.type) {
          children = [rootComponent];
        } else {
          children = [createElement(rootComponent, rootProps)];
        }
        wipRoot = {
          dom: currentRoot ? currentRoot.dom : renderer2.container || null,
          props: {
            children
          },
          alternate: currentRoot,
          path: "root"
        };
        nextUnitOfWork = wipRoot;
        deletions = [];
        while (nextUnitOfWork) {
          nextUnitOfWork = performUnitOfWork(nextUnitOfWork);
        }
        if (wipRoot) {
          commitRoot();
        }
      } catch (e) {
        if (e && typeof e.then === "function") {
          e.then(scheduleRender, scheduleRender);
        } else {
          log("error", `renderNow failed: ${e.message || String(e)}`);
        }
      } finally {
        isRendering = false;
      }
    }
    function useState(initialValue) {
      const path = currentPath;
      const idx = nextHookIndex(path);
      const hook = getHookSlot(path, idx);
      if (!("value" in hook)) {
        hook.value = typeof initialValue === "function" ? initialValue() : initialValue;
      }
      const setter = (next) => {
        const nextValue = typeof next === "function" ? next(hook.value) : next;
        hook.value = nextValue;
        scheduleRender();
      };
      return [hook.value, setter];
    }
    function useReducer(reducer, initialArg, init) {
      const initialState = init !== void 0 ? init(initialArg) : initialArg;
      const [state, setState] = useState(initialState);
      const dispatch = (action) => {
        setState((currentState) => reducer(currentState, action));
      };
      return [state, dispatch];
    }
    function useEffect(effect, deps) {
      const path = currentPath;
      const idx = nextHookIndex(path);
      const hook = getHookSlot(path, idx);
      const shouldRun = shallowDepsChanged(hook.deps, deps);
      if (shouldRun) {
        pendingEffects.push({ hook, effect, deps });
      }
    }
    function useLayoutEffect(effect, deps) {
      const path = currentPath;
      const idx = nextHookIndex(path);
      const hook = getHookSlot(path, idx);
      const shouldRun = shallowDepsChanged(hook.deps, deps);
      if (shouldRun) {
        pendingLayoutEffects.push({ hook, effect, deps });
      }
    }
    function useRef(initialValue) {
      const path = currentPath;
      const idx = nextHookIndex(path);
      const hook = getHookSlot(path, idx);
      if (!("ref" in hook)) {
        hook.ref = { current: initialValue };
      }
      return hook.ref;
    }
    function useMemo(factory, deps) {
      const path = currentPath;
      const idx = nextHookIndex(path);
      const hook = getHookSlot(path, idx);
      if (!("value" in hook) || shallowDepsChanged(hook.deps, deps)) {
        hook.value = factory();
        hook.deps = deps;
      }
      return hook.value;
    }
    function useCallback(fn, deps) {
      return useMemo(() => fn, deps);
    }
    function useImperativeHandle(ref, createHandle, deps) {
      useEffect(() => {
        if (ref) {
          const handle = createHandle();
          if (typeof ref === "function") {
            ref(handle);
          } else if (ref && typeof ref === "object" && "current" in ref) {
            ref.current = handle;
          }
        }
      }, deps);
    }
    function lazy(loader) {
      let resolved = null;
      let loading = null;
      let failed = null;
      return function LazyComponent(props) {
        if (resolved) {
          return createElement(resolved, props);
        }
        if (failed) {
          throw failed;
        }
        if (!loading) {
          loading = Promise.resolve(loader()).then((mod) => {
            resolved = mod && mod.default ? mod.default : mod;
            loading = null;
            scheduleRender();
          }).catch((err) => {
            failed = err || new Error("lazy loader failed");
            loading = null;
            scheduleRender();
          });
        }
        throw loading;
      };
    }
    function Suspense(props) {
      let children = props && props.children;
      if (Array.isArray(children) && children.length === 1) {
        children = children[0];
      }
      return {
        type: SUSPENSE_TYPE,
        props: { fallback: props && props.fallback, children },
        children: []
      };
    }
    function createContext(defaultValue) {
      const context = {
        _currentValue: defaultValue,
        Provider: (props) => {
          if ("value" in props) {
            context._currentValue = props.value;
          }
          return props.children;
        }
      };
      return context;
    }
    function useContext(context) {
      return context._currentValue;
    }
    function render(element, container) {
      if (container) {
        rootComponent = element;
        rootProps = {};
        if (renderer2 && !renderer2.container) {
          renderer2.container = container;
        }
      } else {
        rootComponent = element;
        rootProps = {};
      }
      scheduleRender();
      flushWork();
    }
    function unmount() {
      for (const path in componentState) {
        const state = componentState[path];
        if (state && state.hooks) {
          for (const hook of state.hooks) {
            if (hook && typeof hook.cleanup === "function") {
              try {
                hook.cleanup();
              } catch (e) {
                log("error", `hook cleanup failed: ${e.message}`);
              }
            }
          }
        }
      }
      if (renderer2.clear) renderer2.clear();
      rootComponent = null;
      rootProps = {};
      currentRoot = null;
      wipRoot = null;
      nextUnitOfWork = null;
      deletions = [];
      resetTags();
      componentState = {};
      hookCursor = {};
      suspenseStack = [];
    }
    const StyleSheet2 = {
      create: (styles) => styles
    };
    return {
      createElement,
      render,
      unmount,
      useState,
      useEffect,
      useLayoutEffect,
      useRef,
      useMemo,
      useCallback,
      useImperativeHandle: (ref, create, deps) => {
        if (ref) {
          ref.current = create();
        }
      },
      useReducer,
      createContext,
      useContext,
      Fragment: FRAGMENT_TYPE,
      Suspense,
      lazy,
      memo,
      forwardRef: (comp) => comp,
      StyleSheet: StyleSheet2,
      ActUtils: {
        act: (cb) => {
          const res = cb();
          flushWork();
          return res;
        }
      }
    };
  }

  // src/renderer-android.js
  function createAndroidRenderer() {
    const normalizeType = (type) => {
      if (typeof type === "string") return type;
      return "view";
    };
    const getBridge = () => {
      const g2 = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : {};
      return g2.bridge;
    };
    function mountNode(node, parentTag, index, parentType, path, helpers) {
      if (node === null || node === void 0 || node === false) return;
      if (node && node.type === helpers.suspenseType) {
        helpers.pushSuspense(node.props ? node.props.fallback : null);
        try {
          const child = node.props ? node.props.children : null;
          mountNode(child, parentTag, index, parentType, helpers.makePath(path, "s"), helpers);
        } finally {
          helpers.popSuspense();
        }
        return;
      }
      if (Array.isArray(node)) {
        for (let i = 0; i < node.length; i++) {
          mountNode(node[i], parentTag, index + i, parentType, helpers.makePath(path, i), helpers);
        }
        return;
      }
      const nb = getBridge();
      if (!nb) {
        helpers.log("error", "bridge missing");
        return;
      }
      if (typeof node === "string" || typeof node === "number") {
        const textVal = String(node);
        if (parentType === "span" || parentType === "text" || parentType === "button") {
          nb.updateProps(parentTag, { text: textVal });
        } else {
          const textTag = helpers.nextTag();
          nb.createView(textTag, "span", { text: textVal, width: "wrap_content", height: "wrap_content" });
          nb.addChild(parentTag, textTag, index);
        }
        return;
      }
      if (node.type === helpers.fragmentType) {
        const kids2 = node.children || [];
        const flatKids2 = [];
        for (let i = 0; i < kids2.length; i++) {
          if (Array.isArray(kids2[i])) {
            for (let j = 0; j < kids2[i].length; j++) {
              flatKids2.push(kids2[i][j]);
            }
          } else {
            flatKids2.push(kids2[i]);
          }
        }
        for (let i = 0; i < flatKids2.length; i++) {
          mountNode(flatKids2[i], parentTag, index + i, parentType, helpers.makePath(path, i), helpers);
        }
        return;
      }
      if (typeof node.type === "function") {
        const compPath = helpers.makePath(path, `c${index}`);
        try {
          const rendered = helpers.renderComponent(node.type, node.props || {}, compPath);
          mountNode(rendered, parentTag, index, parentType, compPath, helpers);
        } catch (e) {
          helpers.emitError(`Failed to mount component: ${e.message || String(e)}`);
        }
        return;
      }
      const type = normalizeType(node.type);
      const tag = helpers.nextTag();
      const props = Object.assign({}, node.props || {});
      const kids = node.children || [];
      const onClick = props.onClick;
      const onChange = props.onChange || props.onInput;
      delete props.onClick;
      delete props.onChange;
      delete props.onInput;
      delete props.children;
      if (!props.width && parentTag === -1) props.width = "match_parent";
      if (!props.height && parentTag === -1) props.height = "match_parent";
      nb.createView(tag, type, props);
      if (typeof onClick === "function") {
        nb.addEventListener(tag, "click", onClick);
      }
      if (typeof onChange === "function") {
        nb.addEventListener(tag, "change", onChange);
      }
      const flatKids = [];
      for (let i = 0; i < kids.length; i++) {
        if (Array.isArray(kids[i])) {
          for (let j = 0; j < kids[i].length; j++) {
            flatKids.push(kids[i][j]);
          }
        } else {
          flatKids.push(kids[i]);
        }
      }
      if (testId) {
        helpers.log("info", `Node ${tag} (${type}) has ${flatKids.length} flat children`);
      }
      for (let i = 0; i < flatKids.length; i++) {
        mountNode(flatKids[i], tag, i, type, helpers.makePath(path, i), helpers);
      }
      nb.addChild(parentTag, tag, index);
    }
    return {
      mountNode,
      createInstance(type, props, helpers) {
        const nb = getBridge();
        if (!nb) return null;
        const tag = helpers.nextTag();
        if (type === "TEXT_ELEMENT") {
          nb.createView(tag, "span", { text: String(props.nodeValue || ""), width: "wrap_content", height: "wrap_content" });
          return { tag, type: "span" };
        }
        const androidType = normalizeType(type);
        const p = Object.assign({}, props || {});
        delete p.children;
        nb.createView(tag, androidType, p);
        if (typeof props.onClick === "function") {
          nb.addEventListener(tag, "click", props.onClick);
        }
        return { tag, type: androidType };
      },
      appendChild(parent, child) {
        const nb = getBridge();
        if (!nb || !child) return;
        const parentTag = parent ? parent.tag : -1;
        nb.addChild(parentTag, child.tag, -1);
      },
      commitUpdate(instance, oldProps, newProps) {
        const nb = getBridge();
        if (!nb || !instance) return;
        if (instance.type === "span" && "nodeValue" in newProps) {
          nb.updateProps(instance.tag, { text: String(newProps.nodeValue || "") });
          return;
        }
        const p = Object.assign({}, newProps || {});
        delete p.children;
        nb.updateProps(instance.tag, p);
        if (newProps.onClick !== oldProps.onClick) {
          if (typeof newProps.onClick === "function") {
            nb.addEventListener(instance.tag, "click", newProps.onClick);
          }
        }
        if (newProps.onChange !== oldProps.onChange || newProps.onInput !== oldProps.onInput) {
          const nextChange = newProps.onChange || newProps.onInput;
          if (typeof nextChange === "function") {
            nb.addEventListener(instance.tag, "change", nextChange);
          }
        }
      },
      removeChild(parent, child) {
        const nb = getBridge();
        if (!nb || !child) return;
        const parentTag = parent ? parent.tag : -1;
        nb.removeChild(parentTag, child.tag);
      },
      clear() {
        const g2 = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : {};
        if (typeof g2.__clearViews === "function") {
          g2.__clearViews();
          return;
        }
        const nb = g2.bridge;
        if (nb && nb.removeChild) {
          try {
            nb.removeChild(-1, -1);
          } catch (e) {
          }
        }
      }
    };
  }

  // src/index.android.js
  var renderer = createAndroidRenderer();
  var Act = createAct(renderer);
  var View = (props) => Act.createElement("view", props);
  var Text = (props) => Act.createElement("text", props);
  var Image = (props) => Act.createElement("image", props);
  var ScrollView = (props) => Act.createElement("scroll", props);
  var StyleSheet = Act.StyleSheet;
  var AppRegistry = {
    registerComponent: (name, factory) => {
      const Component = factory();
      Act.render(Component);
    }
  };
  var g = typeof globalThis !== "undefined" ? globalThis : typeof global !== "undefined" ? global : void 0;
  if (g) {
    g.Act = Act;
    g.React = Act;
    g.Android = {
      View,
      Text,
      Image,
      ScrollView,
      StyleSheet,
      AppRegistry
    };
    const jsx = (type, props, key) => {
      const p = props || {};
      console.log(`[ACT.jsx] type=${type}, key=${key}, props.children type=${typeof p.children}, isArray=${Array.isArray(p.children)}`);
      if (Array.isArray(p.children)) {
        console.log(`[ACT.jsx] children array length=${p.children.length}, first=${JSON.stringify(p.children[0])?.substring(0, 200)}`);
      }
      if (p.children !== void 0) {
        return Act.createElement(type, p, p.children);
      }
      return Act.createElement(type, p);
    };
    g.__hook_jsx_runtime = {
      jsx,
      jsxs: jsx,
      Fragment: Act.Fragment
    };
    g.__jsx = jsx;
    g.__jsxs = jsx;
    g.__Fragment = Act.Fragment;
  }
  var index_android_default = Act;
})();
