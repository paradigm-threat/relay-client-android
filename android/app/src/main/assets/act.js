(function (global) {
    var tagCounter = 1;
    var rootComponent = null;
    var rootProps = {};
    var renderQueued = false;
    var isRendering = false;
    var componentState = {};
    var hookCursor = {};
    var pendingEffects = [];
    var currentPath = "root";

    function log(level, message) {
        try {
            var logger = (global.console && global.console[level]) ? global.console[level] : null;
            if (logger) {
                logger('[act] ' + message);
            }
            // Also emit error-level messages to native for display
            if (level === 'error' && typeof global.__nativeLog === 'function') {
                global.__nativeLog('error', '[act] ' + message);
            }
        } catch (e) {
            /* ignore */
        }
    }

    function emitError(message) {
        log('error', message);
        if (typeof global.__nativeLog === 'function') {
            global.__nativeLog('error', message);
        }
    }

    function flattenChildren(args) {
        var out = [];
        for (var i = 2; i < args.length; i++) {
            var child = args[i];
            if (Array.isArray(child)) {
                for (var j = 0; j < child.length; j++) out.push(child[j]);
            } else if (child !== undefined && child !== null && child !== false) {
                out.push(child);
            }
        }
        return out;
    }

    function createElement(type, props) {
        var children = flattenChildren(arguments);
        return { type: type, props: props || {}, children: children };
    }

    function resetTags() {
        tagCounter = 1;
    }

    function nextTag() {
        return tagCounter++;
    }

    function clearNativeViews() {
        if (typeof global.__nativeClearViews === 'function') {
            global.__nativeClearViews();
            return;
        }
        var nb = global.nativeBridge;
        if (!nb || !nb.removeChild) {
            log('warn', 'No native clear available');
            return;
        }
        try {
            nb.removeChild(-1, -1);
        } catch (e) {
            log('warn', 'Failed to clear views: ' + e.message);
        }
    }

    function makePath(parent, key) {
        return parent ? parent + '.' + key : String(key);
    }

    function resetHookCursor(path) {
        hookCursor[path] = 0;
    }

    function nextHookIndex(path) {
        var idx = hookCursor[path] !== undefined ? hookCursor[path] : 0;
        hookCursor[path] = idx + 1;
        return idx;
    }

    function getHookSlot(path, index) {
        var state = componentState[path];
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
        for (var i = 0; i < prev.length; i++) {
            if (prev[i] !== next[i]) return true;
        }
        return false;
    }

    function normalizeType(type) {
        if (typeof type === 'string') return type;
        if (typeof type === 'function') return 'view';
        return 'view';
    }

    function scheduleRender() {
        if (!rootComponent) return;
        if (renderQueued) return;
        renderQueued = true;
        renderNow();
    }

    function renderComponent(fn, props, path) {
        resetHookCursor(path);
        var prevPath = currentPath;
        currentPath = path;
        try {
            var vnode = fn(props || {});
            currentPath = prevPath;
            return vnode;
        } catch (e) {
            currentPath = prevPath;
            emitError('renderComponent failed: ' + (e.message || String(e)));
            throw e;
        }
    }

    function flushEffects() {
        var effects = pendingEffects.slice();
        pendingEffects.length = 0;
        for (var i = 0; i < effects.length; i++) {
            var item = effects[i];
            if (!item || !item.hook || typeof item.effect !== 'function') continue;
            if (typeof item.hook.cleanup === 'function') {
                try {
                    item.hook.cleanup();
                } catch (e) {
                    log('error', 'effect cleanup failed: ' + e.message);
                }
            }
            try {
                var nextCleanup = item.effect();
                if (typeof nextCleanup === 'function') {
                    item.hook.cleanup = nextCleanup;
                } else {
                    item.hook.cleanup = null;
                }
                item.hook.deps = item.deps;
            } catch (e) {
                log('error', 'effect error: ' + e.message);
            }
        }
    }

    function mountNode(node, parentTag, index, parentType, path) {
        if (node === null || node === undefined || node === false) return;
        var nb = global.nativeBridge;
        if (!nb) {
            log('error', 'nativeBridge missing');
            return;
        }

        if (typeof node === 'string' || typeof node === 'number') {
            var textVal = String(node);
            if (parentType === 'span' || parentType === 'text' || parentType === 'button') {
                nb.updateProps(parentTag, { text: textVal });
            } else {
                var textTag = nextTag();
                nb.createView(textTag, 'span', { text: textVal, width: 'wrap_content', height: 'wrap_content' });
                nb.addChild(parentTag, textTag, index);
            }
            return;
        }

        if (typeof node.type === 'function') {
            var compPath = makePath(path, 'c' + index);
            try {
                var rendered = renderComponent(node.type, node.props || {}, compPath);
                mountNode(rendered, parentTag, index, parentType, compPath);
            } catch (e) {
                emitError('Failed to mount component: ' + (e.message || String(e)));
            }
            return;
        }

        var type = normalizeType(node.type);
        var tag = nextTag();
        var props = Object.assign({}, node.props || {});
        var onClick = props.onClick;
        delete props.onClick;
        delete props.children;

        if (!props.width && parentTag === -1) props.width = 'match_parent';
        if (!props.height && parentTag === -1) props.height = 'match_parent';

        nb.createView(tag, type, props);
        if (typeof onClick === 'function') {
            nb.addEventListener(tag, 'click', onClick);
        }

        var kids = node.children || [];
        for (var i = 0; i < kids.length; i++) {
            mountNode(kids[i], tag, i, type, makePath(path, i));
        }

        nb.addChild(parentTag, tag, index);
    }

    function cleanupAll() {
        for (var path in componentState) {
            var state = componentState[path];
            if (state && state.hooks) {
                for (var i = 0; i < state.hooks.length; i++) {
                    var hook = state.hooks[i];
                    if (hook && typeof hook.cleanup === 'function') {
                        try {
                            hook.cleanup();
                        } catch (e) {
                            log('error', 'hook cleanup failed: ' + e.message);
                        }
                    }
                }
            }
        }
    }

    function renderNow() {
        renderQueued = false;
        if (isRendering) return;
        if (!rootComponent) return;
        isRendering = true;
        try {
            // Don't clear views here - the native runtime already clears before calling Act.render
            // clearNativeViews();
            resetTags();
            hookCursor = {};
            var vnode = renderComponent(rootComponent, rootProps || {}, 'root');
            mountNode(vnode, -1, 0, null, 'root');
            flushEffects();
        } catch (e) {
            var errorMsg = 'render failed: ' + (e.message || String(e));
            log('error', errorMsg);
            emitError(errorMsg);
        } finally {
            isRendering = false;
        }
    }

    function useState(initialValue) {
        var path = currentPath;
        var idx = nextHookIndex(path);
        var hook = getHookSlot(path, idx);
        if (!('value' in hook)) {
            hook.value = (typeof initialValue === 'function') ? initialValue() : initialValue;
        }
        var setter = function (next) {
            var nextValue = (typeof next === 'function') ? next(hook.value) : next;
            hook.value = nextValue;
            scheduleRender();
        };
        return [hook.value, setter];
    }

    function useRef(initialValue) {
        var path = currentPath;
        var idx = nextHookIndex(path);
        var hook = getHookSlot(path, idx);
        if (!('ref' in hook)) {
            hook.ref = { current: initialValue };
        }
        return hook.ref;
    }

    function useMemo(factory, deps) {
        var path = currentPath;
        var idx = nextHookIndex(path);
        var hook = getHookSlot(path, idx);
        if (!('value' in hook) || shallowDepsChanged(hook.deps, deps)) {
            hook.value = factory();
            hook.deps = deps;
        }
        return hook.value;
    }

    function useCallback(fn, deps) {
        return useMemo(function () { return fn; }, deps);
    }

    function useEffect(effect, deps) {
        var path = currentPath;
        var idx = nextHookIndex(path);
        var hook = getHookSlot(path, idx);
        var shouldRun = shallowDepsChanged(hook.deps, deps);
        if (shouldRun) {
            pendingEffects.push({ hook: hook, effect: effect, deps: deps });
        }
    }

    function render(component, props) {
        rootComponent = component;
        rootProps = props || {};
        scheduleRender();
    }

    function unmount() {
        cleanupAll();
        clearNativeViews();
        resetTags();
        componentState = {};
        hookCursor = {};
    }

    var Act = {
        createElement: createElement,
        render: render,
        unmount: unmount,
        useState: useState,
        useEffect: useEffect,
        useRef: useRef,
        useMemo: useMemo,
        useCallback: useCallback
    };

    global.Act = Act;
    // Alias React to Act for compatibility
    global.React = Act;
    global.__runtime = { mode: 'act' };
    log('info', 'Act runtime ready');
})(typeof globalThis !== 'undefined' ? globalThis : this);
