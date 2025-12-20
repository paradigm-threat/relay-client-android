package com.relay.pure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import java.io.File

@RunWith(AndroidJUnit4::class)
@SmallTest
class ActRuntimeInstrumentedTest {
    private val gson = Gson()

    @Test
    fun actInitializationAndRenderMessages() {
        val engine = QuickJs.create()
        installNativeStubs(engine)
        installConsole(engine)
        installJsxRuntime(engine)
        installRenderer(engine)
        loadActRuntime(engine)

        val mode = engine.evaluate("(function(){ return (globalThis.__runtime && globalThis.__runtime.mode) || 'unknown'; })()", "check_mode") as String
        assertEquals("act", mode)
        val reactAlias = engine.evaluate("(function(){ return globalThis.React === globalThis.Act; })()", "react_alias") as Boolean
        assertTrue(reactAlias)

        val comp = """
            function Comp(props){
                return __hook_jsx_runtime.jsx('div', { className: 'p-4', children: ['Hello'] });
            }
            Act.render(Comp, {});
        """.trimIndent()
        engine.evaluate(comp, "comp_render.js")

        val queueJson = drainQueue(engine)
        val messages: List<Map<String, Any>> = gson.fromJson(queueJson, List::class.java) as List<Map<String, Any>>
        assertTrue(messages.isNotEmpty())
        val types = messages.map { it["type"] as String }
        assertTrue(types.contains("createView"))
        assertTrue(types.contains("addChild"))

        engine.close()
    }

    private fun installNativeStubs(engine: QuickJs) {
        engine.evaluate(
            """
            globalThis.__messageQueue = [];
            globalThis.__pushMessage = function(type, payload) {
                __messageQueue.push({ type: type, payload: payload });
            };
            globalThis.__nativeLog = function(level, message) {
                __pushMessage('log', { level: level, message: message });
            };
            globalThis.__nativeCreateView = function(tag, type, propsJson) {
                __pushMessage('createView', { tag: tag, type: type, props: propsJson });
            };
            globalThis.__nativeUpdateProps = function(tag, propsJson) {
                __pushMessage('updateProps', { tag: tag, props: propsJson });
            };
            globalThis.__nativeRemoveView = function(tag) {
                __pushMessage('removeView', { tag: tag });
            };
            globalThis.__nativeAddChild = function(parent, child, index) {
                __pushMessage('addChild', { parent: parent, child: child, index: index });
            };
            globalThis.__nativeRemoveChild = function(parent, child) {
                __pushMessage('removeChild', { parent: parent, child: child });
            };
            globalThis.__nativeClearViews = function() {
                __pushMessage('clearViews', {});
            };
            globalThis.__nativeSetRuntime = function(mode) {
                __pushMessage('setRuntime', { mode: mode });
            };
            globalThis.__nativeAddEventListener = function(tag, eventName) {
                __pushMessage('addEventListener', { tag: tag, eventName: eventName });
            };
            globalThis.__nativeRemoveEventListener = function(tag, eventName) {
                __pushMessage('removeEventListener', { tag: tag, eventName: eventName });
            };
            """,
            "native_stubs_test.js"
        )
    }

    private fun installConsole(engine: QuickJs) {
        engine.evaluate(
            """
            globalThis.console = {
              log: function() { globalThis.__nativeLog('log', Array.from(arguments).join(' ')); },
              info: function() { globalThis.__nativeLog('info', Array.from(arguments).join(' ')); },
              warn: function() { globalThis.__nativeLog('warn', Array.from(arguments).join(' ')); },
              error: function() { globalThis.__nativeLog('error', Array.from(arguments).join(' ')); }
            };
            """,
            "console_test.js"
        )
    }

    private fun installRenderer(engine: QuickJs) {
        engine.evaluate(
            """
            (function(){
                var __tagCounter = 1;
                function nextTag(){ return __tagCounter++; }
                function h(type, props){ var kids = Array.prototype.slice.call(arguments,2); return { type: type, props: props||{}, children: kids }; }
                function normalizeType(t){ return (typeof t === 'function') ? 'div' : String(t).toLowerCase(); }
                function mountNode(node, parentTag, index, parentType){
                    var nb = globalThis.nativeBridge; if(!nb || node == null) return;
                    if(typeof node === 'string' || typeof node === 'number'){
                        var textVal = String(node);
                        if(parentType === 'span' || parentType === 'text' || parentType === 'button'){
                            nb.updateProps(parentTag, { text: textVal });
                        } else {
                            var t = nextTag();
                            nb.createView(t, 'span', { text: textVal, width:'wrap_content', height:'wrap_content' });
                            nb.addChild(parentTag, t, index);
                        }
                        return;
                    }
                    var isComponent = typeof node.type === 'function';
                    if(isComponent){ mountNode(node.type(node.props||{}), parentTag, index, parentType); return; }
                    var type = normalizeType(node.type);
                    var tag = nextTag();
                    var props = Object.assign({}, node.props||{});
                    nb.createView(tag, type, props);
                    if(props && props.onClick){ nb.addEventListener(tag, 'click', props.onClick); }
                    (node.children||[]).forEach(function(child,i){ mountNode(child, tag, i, type); });
                    nb.addChild(parentTag, tag, index);
                }
                function renderComponent(comp, props){
                    var nb = globalThis.nativeBridge; if(!nb) { console.error('nativeBridge missing'); return; }
                    var root = (typeof comp === 'function') ? comp(props||{}) : comp;
                    var rootProps = root.props || {};
                    if(!rootProps.width) rootProps.width = 'match_parent';
                    if(!rootProps.height) rootProps.height = 'match_parent';
                    var rootTag = nextTag();
                    nb.createView(rootTag, normalizeType(root.type), rootProps);
                    nb.addChild(-1, rootTag, 0);
                    (root.children||[]).forEach(function(child,i){ mountNode(child, rootTag, i, root.type); });
                }
                globalThis.__renderHook = renderComponent;
                globalThis.h = h;
            })();
            globalThis.nativeBridge = {
              createView: function(tag, type, props) { globalThis.__nativeCreateView(tag, type, JSON.stringify(props||{})); },
              updateProps: function(tag, props) { globalThis.__nativeUpdateProps(tag, JSON.stringify(props||{})); },
              addChild: function(parent, child, index) { globalThis.__nativeAddChild(parent, child, index != null ? index : -1); },
              removeChild: function(parent, child) { globalThis.__nativeRemoveChild(parent, child); },
              addEventListener: function(tag, eventName, callback) {
                if (!globalThis.__eventCallbacks) { globalThis.__eventCallbacks = {}; }
                if (!globalThis.__eventCallbacks[tag]) { globalThis.__eventCallbacks[tag] = {}; }
                globalThis.__eventCallbacks[tag][eventName] = callback;
                globalThis.__nativeAddEventListener(tag, eventName);
              },
              removeEventListener: function(tag, eventName) {
                if (globalThis.__eventCallbacks && globalThis.__eventCallbacks[tag]) { delete globalThis.__eventCallbacks[tag][eventName]; }
                globalThis.__nativeRemoveEventListener(tag, eventName);
              },
              _triggerEvent: function(tag, eventName, data) {
                if (globalThis.__eventCallbacks && globalThis.__eventCallbacks[tag] && globalThis.__eventCallbacks[tag][eventName]) {
                  globalThis.__eventCallbacks[tag][eventName](data);
                }
              }
            };
            """,
            "renderer_test.js"
        )
    }

    private fun installJsxRuntime(engine: QuickJs) {
        engine.evaluate(
            """
            (function(){
                var jsxRuntime = {
                    jsx: function(type, props){ 
                        var children = (props && props.children) ? ([]).concat(props.children) : []; 
                        return { type:type, props:props||{}, children:children}; 
                    },
                    jsxs: function(type, props){ return jsxRuntime.jsx(type, props); },
                    Fragment: 'div'
                };
                globalThis.__hook_jsx_runtime = jsxRuntime;
              })();
            """,
            "jsx_runtime_test.js"
        )
    }

    private fun loadActRuntime(engine: QuickJs) {
        // Read from APK assets via instrumentation target context
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val actCode = ctx.assets.open("act.js").bufferedReader().use { it.readText() }
        engine.evaluate(actCode, "act.js")
        engine.evaluate("globalThis.__runtime = { mode: 'act' };", "act_runtime_flag.js")
    }

    private fun drainQueue(engine: QuickJs): String {
        return engine.evaluate("(function(){ var q = JSON.stringify(globalThis.__messageQueue||[]); globalThis.__messageQueue = []; return q; })()", "drain_queue") as String
    }
}
