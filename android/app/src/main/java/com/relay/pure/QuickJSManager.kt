package com.relay.pure

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

enum class RuntimeMode(val mode: String) {
    REACT("react"),
    ACT("act");

    companion object {
        fun fromString(value: String?): RuntimeMode? {
            return values().firstOrNull { it.mode == value?.lowercase() }
        }
    }
}

class QuickJSManager(private val context: Context) {
    companion object {
        private const val TAG = "QuickJSManager"
        var activeManager: QuickJSManager? = null
    }

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("runtime_pref", Context.MODE_PRIVATE)
    private var quickJs: QuickJs? = null
    private var runtimeMode: RuntimeMode = loadRuntimePreference()
    private var lastAsset: String? = "home.jsx"
    var userMessageHandler: ((String, Boolean) -> Unit)? = null
    var runtimeChangedHandler: ((RuntimeMode) -> Unit)? = null

    fun initialize() {
        resetEngine()
    }

    fun setRuntimeMode(mode: RuntimeMode) {
        if (mode == runtimeMode) {
            emitUserMessage("Already using the ${mode.mode} runtime")
            return
        }
        runtimeMode = mode
        runtimeChangedHandler?.invoke(runtimeMode)
        prefs.edit().putString("mode", mode.mode).apply()
        emitUserMessage("Switching to ${mode.mode} runtime...")
        resetEngine()
        val assetToRender = lastAsset ?: "home.jsx"
        renderHook(assetToRender)
    }

    fun currentRuntime(): RuntimeMode = runtimeMode

    private fun resetEngine() {
        quickJs?.close()
        quickJs = null
        activeManager = this

        quickJs = QuickJs.create().also { engine ->
            Log.i(TAG, "Starting QuickJS engine (runtime=${runtimeMode.mode})")
            installNativeFunctions(engine)
            installConsole(engine)
            installNativeBridge(engine)
            installRenderer(engine)
            NativeRenderer.setQuickJsEngine(engine)
            loadRuntime(engine)
            injectVersions(engine)
            injectRepos(engine)
            runtimeChangedHandler?.invoke(runtimeMode)
            emitUserMessage("QuickJS ready (${runtimeMode.mode})")
            Log.i(TAG, "QuickJS engine initialized")
        }
    }

    private fun loadRuntimePreference(): RuntimeMode {
        val stored = prefs.getString("mode", RuntimeMode.REACT.mode)
        return RuntimeMode.fromString(stored) ?: RuntimeMode.REACT
    }

    private fun emitUserMessage(message: String, isError: Boolean = false) {
        userMessageHandler!!.invoke(message, isError)
    }

    private fun loadRuntime(engine: QuickJs) {
        when (runtimeMode) {
            RuntimeMode.REACT -> {
                loadAsset(engine, "react.production.min.js")
                engine.evaluate(
                    "globalThis.React = (typeof React !== 'undefined') ? React : {}; globalThis.__runtime = { mode: 'react' };",
                    "react_runtime.js"
                )
            }
            RuntimeMode.ACT -> {
                loadAsset(engine, "act.js")
                engine.evaluate(
                    "globalThis.__runtime = { mode: 'act' };",
                    "act_runtime.js"
                )
            }
        }
    }

    private fun injectVersions(engine: QuickJs) {
        try {
            val transpilerVersion = RustTranspilerModule.getVersion()
            val stylerVersion = ThemedStylerModule.getVersion()
            val versionsJs = "globalThis.__versions = { transpiler: '${transpilerVersion}', styler: '${stylerVersion}' };"
            engine.evaluate(versionsJs, "versions.js")
            Log.i(TAG, "Injected versions: transpiler=$transpilerVersion, styler=$stylerVersion")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inject versions", e)
        }
    }

    private fun injectRepos(engine: QuickJs) {
        try {
            val devPortReachable = NetworkModule.checkPort("127.0.0.1", 5174)
            val repos = mutableListOf<Map<String, String>>()
            if (devPortReachable) {
                repos.add(mapOf("name" to "Local Web", "url" to "http://127.0.0.1:5174"))
            }
            val reposJson = gson.toJson(repos)
            engine.evaluate("globalThis.__repos = ${reposJson};", "repos.js")
            Log.i(TAG, "Injected repos: ${reposJson}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inject repos", e)
        }
    }

    fun renderHook(asset: String, props: Map<String, Any> = emptyMap()) {
        lastAsset = asset
        val engine = quickJs ?: run {
            Log.w(TAG, "QuickJS not initialized")
            emitUserMessage("Engine is not ready yet. Try switching tabs or restarting.", true)
            return
        }

        NativeRenderer.clearAll()
        engine.evaluate("globalThis.__last_module__ = null; delete globalThis.__hook_props;", "reset_globals")

        val effectiveProps = if (props.isEmpty() && asset == "repo.jsx") {
            mapOf("repos" to (engine.evaluate("(function(){ return globalThis.__repos || []; })();") ?: emptyList<Any>()))
        } else props
        val propsJson = gson.toJson(effectiveProps)
        engine.evaluate("globalThis.__hook_props = ${propsJson};", "props_${asset}")

        val source = readAssetFile(asset)
        if (source.isEmpty()) {
            Log.w(TAG, "Asset $asset is empty; skipping render")
            emitUserMessage("Could not load $asset from app assets.", true)
            return
        }
        Log.i(TAG, "Transpiling and rendering $asset ...")
        val transpiled = try { RustTranspilerModule.transpile(source, asset, includeRuntime = false) } catch (e: Exception) {
            Log.e(TAG, "Transpile failed for $asset", e)
            emitUserMessage("We could not prepare $asset. Try again or switch runtime.", true)
            return
        }
        Log.d(TAG, "Transpiled $asset preview: ${transpiled.take(400)}")

        try {
            engine.evaluate(transpiled, asset)
            val renderScript = when (runtimeMode) {
                RuntimeMode.ACT -> """
                    (function(){
                        try {
                            var c=(globalThis.__last_module__ && globalThis.__last_module__.default)||null;
                            if(c && globalThis.Act && Act.render){
                                Act.render(c, globalThis.__hook_props||{});
                            } else {
                                console.error('No default export or Act missing in ${asset}');
                            }
                        } catch(e) {
                            console.error('Act render error: ' + (e.message || String(e)));
                        }
                    })();
                """.trimIndent()
                RuntimeMode.REACT -> """
                    (function(){
                        var c=(globalThis.__last_module__ && globalThis.__last_module__.default)||null;
                        if(c && typeof __renderHook === 'function'){
                            __renderHook(c, globalThis.__hook_props||{});
                        } else {
                            console.error('No default export in ${asset}');
                        }
                    })();
                """.trimIndent()
            }
            engine.evaluate(renderScript, "render_${asset}")
        } catch (e: Exception) {
            Log.e(TAG, "Render failed for $asset", e)
            emitUserMessage("We hit a render problem while opening ${asset}.", true)
        }

        processMessageQueue(engine)
    }

    fun drainMessageQueue() {
        val engine = quickJs ?: return
        processMessageQueue(engine)
    }

    private fun installNativeFunctions(engine: QuickJs) {
        // Create a message queue in JS that Kotlin can poll
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
            """.trimIndent(),
            "native_stubs.js"
        )
        
        // Process pending messages after script execution
        processMessageQueue(engine)
    }
    
    private fun processMessageQueue(engine: QuickJs) {
        val queueJson = engine.evaluate("(function() { var q = JSON.stringify(__messageQueue); __messageQueue = []; return q; })()") as? String ?: return
        Log.d(TAG, "Processing queue JSON: ${queueJson.take(300)}...")
        
        if (queueJson.isBlank() || queueJson == "[]") {
            Log.d(TAG, "Queue is empty, skipping")
            return
        }
        
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val messages: List<Map<String, Any>> = try {
            gson.fromJson(queueJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message queue: $queueJson", e)
            return
        }
        
        Log.i(TAG, "Processing ${messages.size} queued messages")
        
        for ((index, msg) in messages.withIndex()) {
            val msgType = msg["type"] as? String ?: continue
            val payload = msg["payload"] as? Map<String, Any> ?: continue
            
            Log.d(TAG, "Message $index: type=$msgType, payload keys=${payload.keys}")
            
            mainHandler.post {
                when (msgType) {
                    "log" -> {
                        val level = payload["level"] as? String ?: "log"
                        val message = payload["message"] as? String ?: ""
                        Log.println(mapLogLevel(level), TAG, "[JS] $message")
                        // Show error-level logs to the user
                        if (level == "error") {
                            emitUserMessage(message, true)
                        }
                    }
                    "createView" -> {
                        val tag = (payload["tag"] as? Double)?.toInt() ?: return@post
                        val type = payload["type"] as? String ?: return@post
                        val propsJson = payload["props"] as? String ?: "{}"
                        val props = parseProps(propsJson)
                        Log.d(TAG, "createView: tag=$tag, type=$type")
                        NativeBridge.createView(tag, type, props)
                    }
                    "updateProps" -> {
                        val tag = (payload["tag"] as? Double)?.toInt() ?: return@post
                        val propsJson = payload["props"] as? String ?: "{}"
                        val props = parseProps(propsJson)
                        Log.d(TAG, "updateProps: tag=$tag, propsJson=$propsJson")
                        NativeBridge.updateProps(tag, props)
                    }
                    "removeView" -> {
                        val tag = (payload["tag"] as? Double)?.toInt() ?: return@post
                        NativeBridge.removeView(tag)
                    }
                    "addChild" -> {
                        val parent = (payload["parent"] as? Double)?.toInt() ?: return@post
                        val child = (payload["child"] as? Double)?.toInt() ?: return@post
                        val index = (payload["index"] as? Double)?.toInt() ?: -1
                        Log.d(TAG, "addChild: parent=$parent, child=$child, index=$index")
                        NativeBridge.addChild(parent, child, index)
                    }
                    "removeChild" -> {
                        val parent = (payload["parent"] as? Double)?.toInt() ?: return@post
                        val child = (payload["child"] as? Double)?.toInt() ?: return@post
                        NativeBridge.removeChild(parent, child)
                    }
                    "clearViews" -> {
                        NativeRenderer.clearAll()
                    }
                    "setRuntime" -> {
                        val mode = RuntimeMode.fromString(payload["mode"] as? String)
                        if (mode != null) {
                            setRuntimeMode(mode)
                        } else {
                            emitUserMessage("Unknown runtime request", true)
                        }
                    }
                        "addEventListener" -> {
                            val tag = (payload["tag"] as? Double)?.toInt() ?: return@post
                            val eventName = payload["eventName"] as? String ?: return@post
                            Log.d(TAG, "addEventListener: tag=$tag, eventName=$eventName")
                            NativeRenderer.addEventListener(tag, eventName)
                        }
                        "removeEventListener" -> {
                            val tag = (payload["tag"] as? Double)?.toInt() ?: return@post
                            val eventName = payload["eventName"] as? String ?: return@post
                            Log.d(TAG, "removeEventListener: tag=$tag, eventName=$eventName")
                            NativeRenderer.removeEventListener(tag, eventName)
                        }
                }
            }
        }
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
            """.trimIndent(),
            "console.js"
        )
    }

    private fun installNativeBridge(engine: QuickJs) {
        // Initialize JSX runtime once globally before any module loading
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
                console.log('[JSX Runtime] Initialized global __hook_jsx_runtime');
            })();
            """.trimIndent(),
            "jsx_runtime.js"
        )
        
        engine.evaluate(
            """
            globalThis.nativeBridge = {
              createView: function(tag, type, props) {
                globalThis.__nativeCreateView(tag, type, JSON.stringify(props || {}));
              },
              updateProps: function(tag, props) {
                globalThis.__nativeUpdateProps(tag, JSON.stringify(props || {}));
              },
              removeView: function(tag) {
                globalThis.__nativeRemoveView(tag);
              },
              addChild: function(parent, child, index) {
                globalThis.__nativeAddChild(parent, child, index != null ? index : -1);
              },
              removeChild: function(parent, child) {
                globalThis.__nativeRemoveChild(parent, child);
                            },
                            addEventListener: function(tag, eventName, callback) {
                                if (!globalThis.__eventCallbacks) {
                                    globalThis.__eventCallbacks = {};
                                }
                                if (!globalThis.__eventCallbacks[tag]) {
                                    globalThis.__eventCallbacks[tag] = {};
                                }
                                globalThis.__eventCallbacks[tag][eventName] = callback;
                                globalThis.__nativeAddEventListener(tag, eventName);
                            },
                            removeEventListener: function(tag, eventName) {
                                if (globalThis.__eventCallbacks && globalThis.__eventCallbacks[tag]) {
                                    delete globalThis.__eventCallbacks[tag][eventName];
                                }
                                globalThis.__nativeRemoveEventListener(tag, eventName);
                            },
                            _triggerEvent: function(tag, eventName, data) {
                                if (globalThis.__eventCallbacks && 
                                        globalThis.__eventCallbacks[tag] && 
                                        globalThis.__eventCallbacks[tag][eventName]) {
                                    globalThis.__eventCallbacks[tag][eventName](data);
                                }
                            }
            };
            """.trimIndent(),
            "nativeBridge.js"
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
                console.log('[Renderer] Initialized shared renderer');
            })();
            """.trimIndent(),
            "renderer.js"
        )
    }

    private fun loadAsset(engine: QuickJs, filename: String) {
        val script = readAssetFile(filename)
        if (script.isEmpty()) {
            Log.w(TAG, "Asset $filename is empty; skipping load")
            return
        }
        engine.evaluate(script, filename)
    }


    @Suppress("SameParameterValue")
    private fun readAssetFile(filename: String): String {
        return try {
            val inputStream = context.assets.open(filename)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            bufferedReader.close()
            inputStream.close()
            stringBuilder.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read asset $filename: ${e.message}")
            ""
        }
    }

    private fun parseProps(raw: Any?): Map<String, Any> {
        val json = when (raw) {
            is String -> raw
            null -> "{}"
            else -> raw.toString()
        }

        val type = object : TypeToken<Map<String, Any>>() {}.type
        return runCatching { gson.fromJson<Map<String, Any>>(json, type) }.getOrElse { emptyMap() }
    }

    private fun mapLogLevel(level: String): Int {
        return when (level.lowercase()) {
            "error" -> Log.ERROR
            "warn" -> Log.WARN
            "info" -> Log.INFO
            else -> Log.DEBUG
        }
    }

    fun destroy() {
        quickJs?.close()
        quickJs = null
        if (activeManager === this) {
            activeManager = null
        }
        Log.i(TAG, "QuickJS engine stopped")
    }
}
