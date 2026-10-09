package com.clevertree.relayruntime.demo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.clevertree.relayruntime.RelayRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Core relay-runtime component: renders local or remote hooks.
 * Local: hookPath (e.g. "hooks/demo.jsx") — loads from assets.
 * Remote: host + hookPath — fetches from host (Phase F, stub for now).
 * Used as main root by MainActivity; can be nested for remote peer content.
 */
class HookRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "RelayDemo"
        private const val HOOKS_ASSET_DIR = "hooks"
        fun prelude(skipAffirmations: Boolean): String {
            val skip = if (skipAffirmations) "true" else "false"
            return """
                function __relayArmTimeout(fn, ms) {
                  try { fn() } catch (e) {}
                  return 0
                }
                try { globalThis.setTimeout = __relayArmTimeout } catch (e) {}
                try { globalThis.__relaySkipAffirmations = $skip } catch (e) {}
                try { globalThis.__relayAndroidClient = true } catch (e) {}
                try { globalThis.location = { href: "/", pathname: "/", search: "", __relayLocation: true } } catch (e) {}
            """.trimIndent() + "\n"
        }

        fun normalizeRoute(path: String): String {
            var t = path.trim()
            val hash = t.indexOf('#')
            if (hash >= 0) t = t.substring(0, hash)
            val q = t.indexOf('?')
            if (q >= 0) t = t.substring(0, q)
            if (t.startsWith("http://") || t.startsWith("https://")) {
                val slash = t.indexOf('/', t.indexOf("://") + 3)
                t = if (slash >= 0) t.substring(slash) else "/"
            }
            if (!t.startsWith("/")) t = "/$t"
            if (t.length > 1 && t.endsWith("/")) t = t.dropLast(1)
            return t
        }
    }

    private fun readLocalStorage(): String {
        return context.getSharedPreferences("relay-local-storage", Context.MODE_PRIVATE)
            .getString("json", "{}") ?: "{}"
    }

    private fun persistLocalStorage() {
        val json = RelayRuntime.exportLocalStorage()
        if (json.isBlank() || json == "{}") return
        context.getSharedPreferences("relay-local-storage", Context.MODE_PRIVATE)
            .edit().putString("json", json).apply()
    }

    private fun scheduleReloadCheck() {
        if (reloadScheduled) return
        reloadScheduled = true
        mainHandler.postDelayed({
            reloadScheduled = false
            val age = System.currentTimeMillis() - loadedAt
            val playing = RelayVideoPlayerView.anyPlaying
            if (loadedAt > 0 && age >= reloadIntervalMs && !playing) {
                forceReload = true
                memory = null
                runRender()
            } else {
                scheduleReloadCheck()
            }
        }, 60_000L)
    }

    private fun consumePendingNavigation() {
        val href = RelayRuntime.takeLocationHref()
        if (href.isNotEmpty()) routePath = normalizeRoute(href)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val scrollView: ScrollView
    private val container: FrameLayout
    private val renderer: VNodeRenderer

    private fun isDarkTheme(): Boolean {
        val themeName = RelayRuntime.getCurrentTheme()
        val prefersDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return themeName == "dark" || (themeName == "default" && prefersDark)
    }

    /** Page color from the root style, so chrome padding does not show a second background. */
    private fun applyPageBackground(vnodeJson: String) {
        val hex = try {
            val root = org.json.JSONObject(vnodeJson).optJSONObject("Element") ?: return
            val props = root.optJSONObject("props") ?: return
            val styleObj = props.optJSONObject("style") ?: return
            val style = styleObj.optJSONObject("value") ?: styleObj
            val raw = style.opt("backgroundColor")
            when (raw) {
                is String -> raw
                else -> return
            }
        } catch (_: Exception) {
            return
        }
        if (!hex.startsWith("#")) return
        val color = try {
            android.graphics.Color.parseColor(hex)
        } catch (_: Exception) {
            return
        }
        scrollView.setBackgroundColor(color)
        container.setBackgroundColor(color)
    }

    private fun applyThemeToChrome() {
        val dark = isDarkTheme()
        val bgColor = if (dark) android.graphics.Color.parseColor("#111827") else android.graphics.Color.WHITE
        val containerColor = if (dark) android.graphics.Color.parseColor("#111827") else android.graphics.Color.parseColor("#f5f5f5")
        scrollView.setBackgroundColor(bgColor)
        container.setBackgroundColor(containerColor)
    }

    /** Local: path in module map. Default: hooks/app.jsx (single entry). */
    var hookPath: String = "hooks/app.jsx"
        set(value) {
            field = value
        }

    /** Remote: base URL (no trailing slash required). When non-null, [runRender] loads via HTTP. */
    var host: String? = null
        set(value) {
            field = value
        }

    /** In-app path passed to the hook as `relayInitialPath`. */
    var routePath: String = "/"

    /** Generic client sets this from shell settings. Dedicated builds leave it false. */
    var skipAffirmations: Boolean = false

    /** Full hook-source reload while the process stays in the foreground. */
    var reloadIntervalMs: Long = 24L * 60L * 60L * 1000L

    var onError: ((String) -> Unit)? = null
    var onReady: (() -> Unit)? = null

    private val session by lazy { RemoteHookSession(context) }
    private var memory: RemoteHookSession.Loaded? = null
    private var memoryOrigin: String? = null
    private var loadedAt: Long = 0L
    private var reloadScheduled = false
    private var forceReload = false
    private var storageImported = false

    init {
        scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }
        container = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 0)
        }
        scrollView.addView(container)
        addView(scrollView)
        renderer = VNodeRenderer(context)
        renderer.siteNavigator = { path -> navigateTo(path) }
        applyThemeToChrome()
    }

    fun navigateTo(path: String) {
        val next = normalizeRoute(path)
        if (next.isEmpty()) return
        routePath = next
        runRender()
    }

    /** Call after view is attached to load and render the hook. */
    fun runRender() {
        consumePendingNavigation()
        if (host != null) {
            runRenderRemote()
            return
        }
        runRenderLocal()
    }

    private fun runRenderLocal() {
        Log.d(TAG, "HookRenderer.runRender: hookPath=$hookPath")
        try {
            val loaded = loadHooksFromAssets()
            if (loaded == null) {
                showError("No hooks found in assets/$HOOKS_ASSET_DIR")
                return
            }
            val (moduleMap, hooksManifest) = loaded
            val entrySource = moduleMap.optString(hookPath)
            if (entrySource.isEmpty()) {
                showError("Entry hook $hookPath not found in assets")
                return
            }
            val themeYaml = ""
            val themeName = RelayRuntime.getCurrentTheme()
            val prefersDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val dm = context.resources.displayMetrics
            val vnodeJson = runHookStable(
                entrySource = entrySource,
                moduleMapJson = moduleMap.toString(),
                themeYaml = themeYaml,
                hooksManifestJson = hooksManifest.toString(),
                file = hookPath,
                themeName = themeName,
                prefersDark = prefersDark,
                displayDensity = dm.density,
                scaledDensity = dm.scaledDensity,
                viewportCssPx = if (dm.density > 0f) dm.widthPixels / dm.density else 0f,
            )
            Log.i(TAG, "HookRenderer: hook OK")
            applyThemeToChrome()
            val ok = renderer.render(vnodeJson, container) { runRender() }
            applyPageBackground(vnodeJson)
            if (!ok) {
                showError("Render returned false")
            } else {
                onReady?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HookRenderer: parse/runtime error", e)
            showError(e.message ?: e.toString(), e.stackTraceToString())
        }
    }

    private fun runRenderRemote() {
        val baseRaw = host?.trim().orEmpty()
        if (baseRaw.isEmpty()) {
            showError("Remote host URL is empty")
            return
        }
        val base = baseRaw.trimEnd('/')

        Thread {
            try {
                val themeName = RelayRuntime.getCurrentTheme()
                val prefersDark = (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val dm = context.resources.displayMetrics

                if (!storageImported) {
                    RelayRuntime.importLocalStorage(readLocalStorage())
                    storageImported = true
                }
                val reuse = memory != null && memoryOrigin == base && !forceReload
                if (!reuse) {
                    val discovered = session.discoverEntry(base, hookPath)
                    if (discovered.isNotEmpty()) hookPath = discovered
                }
                val loaded = if (reuse) memory!! else session.load(base, hookPath)
                if (!reuse) {
                    memory = loaded
                    memoryOrigin = base
                    loadedAt = if (loaded.fromCache) loaded.fetchedAt else System.currentTimeMillis()
                    forceReload = false
                }
                val moduleMap = loaded.modules
                var themeYaml = ""
                themeYaml = loaded.themeYaml
                val effectivePath = UrlResolve.normalizeModuleKey(hookPath)
                val entrySource = prelude(skipAffirmations) + moduleMap.optString(effectivePath)
                if (moduleMap.optString(effectivePath).isEmpty()) {
                    mainHandler.post {
                        showError("Remote entry $effectivePath not loaded")
                    }
                    return@Thread
                }
                val hooksManifest = JSONObject()
                    .put("hooks", JSONArray().put(JSONObject().put("path", effectivePath).put("name", "Remote")))
                    .put("relayClientBaseUrl", base)
                    .put("relayPathSlash", "/")
                    .put("relayInitialPath", routePath)
                    .put("relaySkipAffirmations", skipAffirmations)
                RelayMediaUrls.fetchBaseUrl = base

                val vnodeJson = runHookStable(
                    entrySource = entrySource,
                    moduleMapJson = moduleMap.toString(),
                    themeYaml = themeYaml,
                    hooksManifestJson = hooksManifest.toString(),
                    file = effectivePath,
                    themeName = themeName,
                    prefersDark = prefersDark,
                    displayDensity = dm.density,
                    scaledDensity = dm.scaledDensity,
                    viewportCssPx = if (dm.density > 0f) dm.widthPixels / dm.density else 0f,
                )
                persistLocalStorage()

                mainHandler.post {
                    try {
                        Log.i(TAG, "HookRenderer: remote hook OK base=$base")
                        applyThemeToChrome()
                        val ok = renderer.render(vnodeJson, container) { runRender() }
                        applyPageBackground(vnodeJson)
                        if (!ok) {
                            showError("Render returned false")
                        } else {
                            onReady?.invoke()
                            scheduleReloadCheck()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HookRenderer: remote render error", e)
                        showError(e.message ?: e.toString(), e.stackTraceToString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HookRenderer: remote fetch error", e)
                mainHandler.post {
                    showError(e.message ?: e.toString(), e.stackTraceToString())
                }
            }
        }.start()
    }

    private fun runHookStable(
        entrySource: String,
        moduleMapJson: String,
        themeYaml: String,
        hooksManifestJson: String,
        file: String,
        themeName: String,
        prefersDark: Boolean,
        displayDensity: Float,
        scaledDensity: Float,
        viewportCssPx: Float,
    ): String {
        var vnodeJson = RelayRuntime.runHookWithHost(
            source = entrySource,
            moduleMapJson = moduleMapJson,
            themeYaml = themeYaml,
            hooksManifestJson = hooksManifestJson,
            file = file,
            themeName = themeName,
            prefersDark = prefersDark,
            displayDensity = displayDensity,
            scaledDensity = scaledDensity,
            viewportCssPx = viewportCssPx,
        )
        repeat(15) {
            if (!RelayRuntime.consumeRenderRequested()) return vnodeJson
            vnodeJson = RelayRuntime.runHookWithHost(
                source = entrySource,
                moduleMapJson = moduleMapJson,
                themeYaml = themeYaml,
                hooksManifestJson = hooksManifestJson,
                file = file,
                themeName = themeName,
                prefersDark = prefersDark,
                displayDensity = displayDensity,
                scaledDensity = scaledDensity,
                viewportCssPx = viewportCssPx,
            )
        }
        return vnodeJson
    }

    private fun fetchUrlString(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code for $urlString")
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun loadHooksFromAssets(): Pair<JSONObject, JSONObject>? {
        return try {
            val moduleMap = JSONObject()
            val hooksArray = JSONArray()
            context.assets.list(HOOKS_ASSET_DIR)?.filter { it.endsWith(".jsx") }?.sorted()?.forEach { filename ->
                val path = "$HOOKS_ASSET_DIR/$filename"
                val source = context.assets.open(path).use { stream ->
                    InputStreamReader(stream).readText()
                }
                moduleMap.put(path, source)
                val name = filename.removeSuffix(".jsx").replaceFirstChar { it.uppercase() }
                hooksArray.put(JSONObject().apply {
                    put("path", path)
                    put("name", name)
                })
            }
            if (moduleMap.length() == 0) {
                Log.e(TAG, "loadHooksFromAssets: no .jsx files in $HOOKS_ASSET_DIR")
                return null
            }
            val hooksManifest = JSONObject().put("hooks", hooksArray)
            Pair(moduleMap, hooksManifest)
        } catch (e: Exception) {
            Log.e(TAG, "loadHooksFromAssets failed", e)
            null
        }
    }

    private fun showError(msg: String?, stackTrace: String? = null) {
        container.removeAllViews()
        val (title, detail) = parseRelayError(msg)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"))
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(context).apply {
            text = "⚠ $title"
            setTextColor(android.graphics.Color.parseColor("#B91C1C"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        layout.addView(TextView(context).apply {
            text = detail
            setTextColor(android.graphics.Color.parseColor("#7F1D1D"))
            textSize = 14f
            setPadding(0, 0, 0, 16)
        })
        if (!stackTrace.isNullOrBlank()) {
            layout.addView(TextView(context).apply {
                text = "Details:\n$stackTrace"
                setTextColor(android.graphics.Color.GRAY)
                textSize = 11f
                setPadding(0, 8, 0, 0)
            })
        }
        layout.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        container.addView(layout)
        onError?.invoke(msg ?: "")
    }

    private fun parseRelayError(msg: String?): Pair<String, String> {
        if (msg == null) return "Error" to ""
        val jsonPrefix = "RELAY_ERROR_JSON:"
        if (msg.startsWith(jsonPrefix)) {
            return try {
                val json = org.json.JSONObject(msg.removePrefix(jsonPrefix))
                val type = json.optString("type", "error")
                val message = json.optString("message", "")
                val file = json.optString("file", "").takeIf { it.isNotEmpty() }
                val line = json.optInt("line", 0).takeIf { it > 0 }
                val col = json.optInt("column", 0).takeIf { it > 0 }
                val sourceLine = json.optString("source_line", "").takeIf { it.isNotEmpty() }
                val loc = when {
                    file != null && line != null -> "$file:$line" + (col?.let { ":$it" } ?: "")
                    line != null -> "line $line" + (col?.let { ":$it" } ?: "")
                    else -> ""
                }
                val title = when (type) {
                    "parse" -> "Parse Error"
                    "runtime" -> "Runtime Error"
                    else -> "Error"
                }
                val detail = buildString {
                    append(message)
                    if (loc.isNotEmpty()) append("\n  at $loc")
                    if (sourceLine != null) append("\n  $sourceLine")
                }
                title to detail
            } catch (_: Exception) {
                "Error" to msg
            }
        }
        return "Error" to msg
    }
}
