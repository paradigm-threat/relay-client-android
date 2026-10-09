package com.clevertree.relayruntime.demo

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.URL
import com.clevertree.relayruntime.RelayRuntime
import org.json.JSONObject

/**
 * Renders VNode JSON from relay-runtime into Android Views.
 * Supports: div, span, p, h1–h6, button, fragment; Text nodes; onClick→invokeHandler.
 *
 * NOTE: Style translation (className→style, units, theme vars) is done in Rust
 * (style_resolve.rs + themed-styler) before this receives the JSON. This renderer
 * only APPLIES the pre-resolved style props; it does not interpret className.
 */
class VNodeRenderer(private val context: Context) {

    /** Relative site links. The shell re-runs the hook at this path. */
    var siteNavigator: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "RelayDemo"
    }

    /**
     * @param onRenderRequested Call when invokeHandler triggers setState; host should re-run hook and call render again.
     */
    fun render(vnodeJson: String, container: ViewGroup, onRenderRequested: () -> Unit): Boolean {
        return try {
            Log.d(TAG, "VNodeRenderer.render: input len=${vnodeJson.length}, preview=${vnodeJson.take(150)}...")
            container.removeAllViews()
            val root = parseAndBuild(vnodeJson, onRenderRequested, null)
            if (root == null) {
                Log.e(TAG, "VNodeRenderer.render: parseAndBuild returned null (unknown VNode format)")
                return false
            }
            root.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Root sizes to content; no min height (was causing layout issues)
            container.addView(root)
            Log.d(TAG, "VNodeRenderer.render: OK")
            true
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "VNodeRenderer.render: JSON parse error", e)
            Log.e(TAG, "VNodeRenderer.render: input was: ${vnodeJson.take(500)}")
            showRenderError(container, "Parse error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "VNodeRenderer.render: error", e)
            showRenderError(container, "Render error: ${e.message}")
            false
        }
    }

    private fun showRenderError(container: ViewGroup, msg: String) {
        container.removeAllViews()
        container.addView(TextView(context).apply {
            text = msg
            setTextColor(Color.RED)
            setPadding(48, 48, 48, 48)
        })
    }

    private fun parseAndBuild(json: String, onRenderRequested: () -> Unit, inheritedColor: Int?): View? {
        return try {
            val raw = org.json.JSONObject(json)
            when {
                raw.has("Element") -> buildElement(raw.getJSONObject("Element"), onRenderRequested, inheritedColor)
                raw.has("Text") -> buildText(raw.getString("Text"), inheritedColor)
                raw.has("Empty") -> {
                    // CleverScript: hook normalized to empty vnode (e.g. wrong import path, null UI)
                    Log.w(TAG, "VNodeRenderer.parseAndBuild: Empty vnode — hook produced no element tree")
                    TextView(context).apply {
                        text = "Empty render (hook returned no UI). Check dynamic import paths match module map keys (e.g. hooks/home.jsx)."
                        setTextColor(Color.GRAY)
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                    }
                }
                else -> {
                    Log.w(TAG, "VNodeRenderer.parseAndBuild: unknown format keys=${raw.keys().asSequence().toList()}")
                    null
                }
            }
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "VNodeRenderer.parseAndBuild: JSON error on '$json'", e)
            throw e
        }
    }

    private fun buildElement(el: JSONObject, onRenderRequested: () -> Unit, inheritedColor: Int?): View {
        val tag = el.optString("tag", "div")
        val props = el.optJSONObject("props") ?: JSONObject()
        val children = el.optJSONArray("children")
        val resolved = styleFromProps(props)
        val position = if (resolved != null) styleValue(resolved, "position").lowercase() else ""
        if (position == "fixed" || position == "absolute") {
            return View(context).apply { visibility = View.GONE }
        }
        val color = parseCssColor(if (resolved != null) styleValue(resolved, "color") else "") ?: inheritedColor
        val textColor = color ?: Color.BLACK

        when (tag.lowercase()) {
            "relay-hook-renderer" -> return buildNestedHookRenderer(props, onRenderRequested)
            "relay-video-player" -> return buildRelayVideoPlayer(props, onRenderRequested)
            "relay-pop-image" -> return buildRelayPopImage(props, onRenderRequested)
            "relay-image-gallery" -> return buildRelayImageGallery()
            "style", "script", "link", "meta", "noscript" -> return View(context).apply {
                visibility = View.GONE
            }
            else -> { /* fall through */ }
        }
        val parkTag = propString(props, "data-relay-park-native")
        if (!parkTag.isNullOrEmpty()) {
            // Prefer MKV master, then H.264 fallbacks from TheaterPlayer stub.
            val parkSrc = firstMediaSrc(
                propString(props, "data-src-master"),
                propString(props, "data-src"),
                propString(props, "data-src-web"),
                propString(props, "data-src-mobile"),
                propString(props, "src"),
            )
            if (!parkSrc.isNullOrEmpty() && looksLikeMedia(parkSrc)) {
                val videoProps = JSONObject().put("src", parkSrc)
                propString(props, "data-subtitles-json")?.takeIf { it.isNotEmpty() && it != "[]" }?.let {
                    videoProps.put("subtitlesJson", it)
                }
                propString(props, "subtitlesJson")?.takeIf { it.isNotEmpty() && it != "[]" }?.let {
                    videoProps.put("subtitlesJson", it)
                }
                return buildRelayVideoPlayer(videoProps, onRenderRequested)
            }
        }
        if (wantsHorizontalScroll(props)) {
            return buildHorizontalRow(props, children, onRenderRequested)
        }
        val view = when (tag.lowercase()) {
            "img" -> {
                val src = propString(props, "src").orEmpty()
                val mark = if (src.substringBefore('?').endsWith(".svg")) {
                    context.resources.getIdentifier("ic_brand_mark", "drawable", context.packageName)
                } else {
                    0
                }
                if (mark != 0) {
                    // SVG does not decode. The flavor mark stands in for the graphic; alt stays the label.
                    val logoPx = styleInt(resolved, "height")?.takeIf { it > 0 }
                        ?: styleInt(resolved, "width")?.takeIf { it > 0 }
                        ?: dp(42)
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val icon = ImageView(context).apply {
                            setImageResource(mark)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            adjustViewBounds = true
                        }
                        addView(icon, LinearLayout.LayoutParams(logoPx, logoPx))
                        val alt = propString(props, "alt").orEmpty()
                        if (alt.isNotEmpty()) {
                            val label = TextView(context).apply {
                                text = alt
                                setTextColor(textColor)
                                includeFontPadding = false
                            }
                            addView(label, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { marginStart = dp(8) })
                        }
                    }
                } else if (src.substringBefore('?').endsWith(".svg")) {
                    TextView(context).apply {
                        text = propString(props, "alt").orEmpty()
                        setTextColor(textColor)
                        includeFontPadding = false
                    }
                } else {
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                    }
                }
            }
            "a" -> if (textOnly(children)) {
                TextView(context).apply {
                    setTextColor(textColor)
                    includeFontPadding = false
                }
            } else {
                LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            }
            "input", "checkbox" -> {
                val type = propString(props, "type")?.lowercase()
                if (tag.equals("checkbox", true) || type == "checkbox") CheckBox(context) else Button(context)
            }
            "button" -> TextView(context).apply {
                // Framework Button paints its own theme fill over the style map.
                setTextColor(textColor)
                includeFontPadding = false
                gravity = Gravity.CENTER
                isClickable = true
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> TextView(context).apply {
                textSize = headingSize(tag)
                setTextColor(textColor)
                includeFontPadding = false
            }
            "p", "span", "text", "strong", "em", "b", "i", "code", "small", "label" ->
                TextView(context).apply {
                    setTextColor(textColor)
                    includeFontPadding = false
                }
            "div", "view", "section", "header", "footer", "main", "fragment" -> {
                LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            }
            "scroll", "scrollview" -> ScrollView(context).apply {
                addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL })
            }
            else -> LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        }

        applyProps(view, tag, props, onRenderRequested)
        if (view is ImageView) loadImage(view, propString(props, "src"))
        markFocusable(view, tag, props)

        if (children != null) {
            if (view is ViewGroup) {
                val content = when (view) {
                    is ScrollView -> (view.getChildAt(0) as? LinearLayout) ?: view
                    else -> view
                }
                val parentStyle = styleFromProps(props)
                val isRow = (view is LinearLayout) && (view.orientation == LinearLayout.HORIZONTAL)
                val spacingPx = when {
                    isRow -> styleInt(parentStyle, "spaceX") ?: styleInt(parentStyle, "gap") ?: 0
                    else -> styleInt(parentStyle, "spaceY") ?: styleInt(parentStyle, "gap") ?: 0
                }
                addChildren(content, children, onRenderRequested, color, isRow, spacingPx, parentStyle)
            } else if (view is TextView) {
                val text = textContent(children)
                if (text.isNotEmpty()) {
                    view.text = text
                    if (view is Button) view.setAllCaps(false)
                }
            }
        }
        return view
    }

    private fun layoutParamsFor(parent: ViewGroup, child: View, childProps: JSONObject?): ViewGroup.LayoutParams {
        val style = styleFromProps(childProps)
        val row = parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL
        // Flex main size is content. A row child defaults to wrap width so a
        // match_parent sibling cannot crush the rest of the line to 0px.
        val widthDefault = if (row) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        var width = resolveDimension(style, "width", widthDefault)
        var height = resolveDimension(style, "height", ViewGroup.LayoutParams.WRAP_CONTENT)
        if (style == null || !style.has("width")) attrPx(childProps, "width")?.let { width = it }
        if (style == null || !style.has("height")) attrPx(childProps, "height")?.let { height = it }
        val params = when (parent) {
            is LinearLayout -> LinearLayout.LayoutParams(width, height)
            is ScrollView -> ViewGroup.LayoutParams(width, height)
            else -> ViewGroup.LayoutParams(width, height)
        }
        if (params is ViewGroup.MarginLayoutParams && style != null) {
            val m = styleInt(style, "margin")
            params.topMargin = styleInt(style, "marginTop") ?: m ?: 0
            params.bottomMargin = styleInt(style, "marginBottom") ?: m ?: 0
            params.leftMargin = styleInt(style, "marginLeft") ?: m ?: 0
            params.rightMargin = styleInt(style, "marginRight") ?: m ?: 0
        }
        if (params is LinearLayout.LayoutParams && style != null) {
            val weight = styleDouble(style, "flex") ?: styleDouble(style, "flexGrow") ?: 0.0
            params.weight = weight.toFloat()
            // flex-grow adds extra space. The base size stays wrap_content
            // (CSS min-height: auto). Zeroing height here clips a scrolling column.
            val layoutGravity = styleValue(style, "androidLayoutGravity")
            if (layoutGravity.isNotEmpty()) params.gravity = parseGravity(layoutGravity)
        }
        return params
    }

    private fun resolveDimension(style: JSONObject?, key: String, default: Int): Int {
        if (style == null) return default
        val v = style.opt(key) ?: return default
        val s = when (v) {
            is String -> v
            is JSONObject -> v.optString("value", v.optString("String", ""))
            else -> v.toString()
        }
        return when (s.lowercase()) {
            "match_parent", "100%" -> ViewGroup.LayoutParams.MATCH_PARENT
            "wrap_content", "fit-content", "auto" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> styleInt(style, key) ?: default
        }
    }

    private fun parseGravity(s: String): Int {
        var g = Gravity.NO_GRAVITY
        for (part in s.split("|").map { it.trim().lowercase() }) {
            g = g or when (part) {
                "center" -> Gravity.CENTER
                "center_horizontal" -> Gravity.CENTER_HORIZONTAL
                "center_vertical" -> Gravity.CENTER_VERTICAL
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "start" -> Gravity.START
                "end" -> Gravity.END
                else -> Gravity.NO_GRAVITY
            }
        }
        return g
    }

    private fun headingSize(tag: String): Float = when (tag) {
        "h1" -> 28f; "h2" -> 24f; "h3" -> 20f; "h4" -> 18f; "h5" -> 16f; "h6" -> 14f
        else -> 16f
    }

    private fun textContent(children: org.json.JSONArray?): String {
        if (children == null) return ""
        val sb = StringBuilder()
        for (i in 0 until children.length()) {
            val child = try {
                org.json.JSONObject(children.get(i).toString())
            } catch (_: Exception) {
                null
            } ?: continue
            when {
                child.has("Text") -> sb.append(child.optString("Text"))
                child.has("Element") -> {
                    val el = child.optJSONObject("Element")
                    val display = styleValue(
                        styleFromProps(el?.optJSONObject("props")) ?: JSONObject(),
                        "display",
                    ).lowercase()
                    if (display != "none" && display != "hidden") {
                        sb.append(textContent(el?.optJSONArray("children")))
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Fragments are not boxes. Hoist their children so a horizontal row is not
     * one vertical column.
     */
    private fun addChildren(
        content: ViewGroup,
        children: org.json.JSONArray,
        onRenderRequested: () -> Unit,
        inheritedColor: Int?,
        isRow: Boolean,
        spacingPx: Int,
        parentStyle: JSONObject?,
    ) {
        val justify = if (parentStyle != null) styleValue(parentStyle, "justifyContent") else ""
        val spaceBetween = isRow && (justify == "space-between" || justify == "between")
        for (i in 0 until children.length()) {
            val childJson = children.get(i).toString()
            val childEl = try { org.json.JSONObject(childJson) } catch (_: Exception) { null }
            val element = childEl?.optJSONObject("Element")
            val tag = element?.optString("tag", "").orEmpty()
            if (tag.equals("fragment", true)) {
                val nested = element?.optJSONArray("children")
                if (nested != null) {
                    addChildren(content, nested, onRenderRequested, inheritedColor, isRow, spacingPx, parentStyle)
                }
                continue
            }
            val childView = try {
                parseAndBuild(childJson, onRenderRequested, inheritedColor)
            } catch (e: Exception) {
                Log.e(TAG, "VNodeRenderer.addChildren: child[$i] parse failed", e)
                null
            } ?: continue
            if (childView.visibility == View.GONE) {
                content.addView(childView)
                continue
            }
            if (spaceBetween && content.childCount > 0) {
                content.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            val lp = layoutParamsFor(content, childView, element?.optJSONObject("props"))
            if (lp is ViewGroup.MarginLayoutParams && content.childCount > 0 && spacingPx > 0) {
                if (isRow) {
                    if (lp.leftMargin == 0) lp.leftMargin = spacingPx
                } else if (lp.topMargin == 0) {
                    lp.topMargin = spacingPx
                }
            }
            content.addView(childView, lp)
        }
    }

    private fun firstCssColor(raw: String): String {
        val hex = Regex("#[0-9a-fA-F]{3,8}").find(raw)
        if (hex != null) return hex.value
        val rgba = Regex("rgba?\\([^)]*\\)").find(raw)
        if (rgba != null) return rgba.value
        return raw.trim()
    }

    private fun parseCssColor(raw: String): Int? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        if (v == "transparent") return Color.TRANSPARENT
        if (v.startsWith("#")) {
            return try {
                Color.parseColor(v)
            } catch (_: Exception) {
                null
            }
        }
        val rgba = Regex("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)").find(v)
            ?: return null
        val r = rgba.groupValues[1].toIntOrNull() ?: return null
        val g = rgba.groupValues[2].toIntOrNull() ?: return null
        val b = rgba.groupValues[3].toIntOrNull() ?: return null
        val a = rgba.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1f
        return Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, b)
    }

    private fun buildText(text: String, inheritedColor: Int?): TextView = TextView(context).apply {
        this.text = text
        setTextColor(inheritedColor ?: Color.BLACK)
        includeFontPadding = false
    }

    /** Anchors that only wrap text stay text, so a row sizes to the label. */
    private fun textOnly(children: org.json.JSONArray?): Boolean {
        if (children == null || children.length() == 0) return true
        for (i in 0 until children.length()) {
            val child = try {
                org.json.JSONObject(children.get(i).toString())
            } catch (_: Exception) {
                return false
            }
            if (child.has("Element")) return false
        }
        return true
    }

    /** Presentational width/height attributes are CSS px. Style map values already win. */
    private fun attrPx(props: JSONObject?, key: String): Int? {
        if (props == null) return null
        val raw = propString(props, key) ?: return null
        val n = raw.toFloatOrNull() ?: return null
        return dp(n.toInt())
    }

    private var liveVideo: RelayVideoPlayerView? = null

    private fun buildRelayVideoPlayer(props: JSONObject, onRenderRequested: () -> Unit): View {
        val existing = liveVideo
        if (existing != null && !existing.released) {
            existing.bindProps(props, onRenderRequested)
            return existing
        }
        return RelayVideoPlayerView(context).apply {
            liveVideo = this
            setSaveChromeVisible(true)
            val dm = context.resources.displayMetrics
            val theaterH = (dm.widthPixels * 9 / 16).coerceIn(dp(220), (dm.heightPixels * 0.55f).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                theaterH,
            )
            bindProps(props, onRenderRequested)
        }    }

    private fun buildNestedHookRenderer(props: JSONObject, onRenderRequested: () -> Unit): View {
        val host = propString(props, "host") ?: return TextView(context).apply {
            text = "relay-hook-renderer: host prop required"
            setTextColor(Color.RED)
        }
        val hookPath = propString(props, "hookPath") ?: "/hooks/client/get.jsx"
        return HookRenderer(context).apply {
            this.host = host
            this.hookPath = hookPath
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            runRender()
        }
    }

    private fun applyProps(view: View, tag: String, props: JSONObject, onRenderRequested: () -> Unit) {
        // Apply pre-resolved style prop only. No className parsing (handled in relay-runtime core).
        val styleObj = props.optJSONObject("style") ?: (props.opt("style") as? JSONObject)
        // Style values from relay-runtime are in px (themed-styler converts dp→px by density)
        val style = styleObj?.optJSONObject("value") ?: styleObj
        style?.let { s ->
            val padAll = styleInt(s, "padding")
            var pl = padAll ?: 0
            var pt = padAll ?: 0
            var pr = padAll ?: 0
            var pb = padAll ?: 0
            styleInt(s, "paddingLeft")?.let { pl = it }
            styleInt(s, "paddingTop")?.let { pt = it }
            styleInt(s, "paddingRight")?.let { pr = it }
            styleInt(s, "paddingBottom")?.let { pb = it }
            if (padAll != null || s.has("paddingLeft") || s.has("paddingTop") || s.has("paddingRight") || s.has("paddingBottom")) {
                view.setPadding(pl, pt, pr, pb)
            }
            styleInt(s, "minWidth")?.let { view.minimumWidth = it }
            styleInt(s, "minHeight")?.let { view.minimumHeight = it }
            var borderRadius = 0f
            var borderWidth = 0
            var borderColor: Int? = null
            var backgroundColor: Int? = null
            var backgroundRaw = ""
            for (key in s.keys()) {
                when (key) {
                    "color" -> {
                        val v = styleValue(s, key)
                        if (v.isNotEmpty() && v.startsWith("#") && view is TextView) {
                            try { view.setTextColor(Color.parseColor(v)) } catch (_: Exception) {}
                        }
                    }
                    "backgroundColor" -> {
                        parseCssColor(styleValue(s, key))?.let { backgroundColor = it }
                    }
                    "background" -> backgroundRaw = styleValue(s, key)
                    "fontSize" -> {
                        val size = (styleDouble(s, key) ?: 16.0).toFloat()
                        if (view is TextView) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                    }
                    "fontWeight", "fontFamily", "androidTypefaceStyle" -> { /* applyRelayTypeface below */ }
                    "androidTextGravity" -> {
                        if (view is TextView) view.gravity = parseGravity(styleValue(s, key))
                    }
                    "androidGravity" -> {
                        if (view is LinearLayout) view.gravity = parseGravity(styleValue(s, key))
                    }
                    "flexDirection" -> {
                        if (view is LinearLayout) {
                            val fd = styleValue(s, key).lowercase()
                            view.orientation = if (fd == "row") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                        }
                    }
                    "display" -> {
                        val d = styleValue(s, key).lowercase()
                        view.visibility = if (d == "none" || d == "hidden") View.GONE else View.VISIBLE
                    }
                    "elevation" -> view.elevation = (styleDouble(s, key) ?: 0.0).toFloat()
                    "borderRadius" -> borderRadius = (styleDouble(s, key) ?: 0.0).toFloat()
                    "borderWidth" -> borderWidth = styleInt(s, key) ?: 0
                    "borderColor" -> {
                        val v = styleValue(s, key)
                        if (v.startsWith("#")) try { borderColor = Color.parseColor(v) } catch (_: Exception) {}
                    }
                    "padding", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight" -> { /* handled above */ }
                    else -> { /* skip */ }
                }
            }
            if (backgroundRaw.isNotEmpty()) {
                parseCssColor(firstCssColor(backgroundRaw))?.let { backgroundColor = it }
            }
            val fill = backgroundColor
            if (borderRadius > 0 || borderWidth > 0 || fill != null) {
                val shape = GradientDrawable()
                if (borderRadius > 0) {
                    shape.cornerRadius = borderRadius
                    view.clipToOutline = true
                }
                if (fill != null) shape.setColor(fill)
                if (borderWidth > 0) shape.setStroke(borderWidth, borderColor ?: Color.TRANSPARENT)
                view.backgroundTintList = null
                view.background = shape
            }
            if (view is TextView) {
                applyRelayTypeface(view, s)
            }
        }

        // children text for container (div with single text child)
        propString(props, "text")?.let { text ->
            if (view is TextView) view.text = text
            else if (view is ViewGroup && view.childCount == 0) {
                view.addView(TextView(context).apply { this.text = text })
            }
        }

        // onClick -> HandlerId (serialized as {"type":"HandlerId","value":id})
        (props.optJSONObject("onClick") ?: (props.opt("onClick") as? JSONObject))?.let { handler ->
            val id = handler.optInt("value", 0)
            if (id > 0) {
                view.isClickable = true
                view.setOnClickListener {
                    val href = propString(props, "href")
                    if (href != null && openHref(href)) return@setOnClickListener
                    try {
                        Log.d(TAG, "VNodeRenderer: onClick handlerId=$id")
                        RelayRuntime.invokeHandler(id)
                        val pending = RelayRuntime.takeLocationHref()
                        if (pending.isNotEmpty()) {
                            siteNavigator?.invoke(pending)
                            return@setOnClickListener
                        }
                        val needRender = RelayRuntime.consumeRenderRequested()
                        Log.d(TAG, "VNodeRenderer: consumeRenderRequested=$needRender")
                        if (needRender) onRenderRequested()
                    } catch (e: Exception) {
                        Log.e(TAG, "VNodeRenderer: invokeHandler failed id=$id", e)
                        val msg = e.message ?: ""
                        if (!msg.contains("navigate not implemented")) {
                            android.widget.Toast.makeText(context, "Error: $msg", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val href = propString(props, "href")
        if (href != null && !view.hasOnClickListeners()) {
            view.isClickable = true
            view.setOnClickListener { openHref(href) }
        }
    }

    private fun openHref(href: String): Boolean {
        val trimmed = href.trim()
        // External player CTAs (intent: / https MKV / vlc://) — never treat as in-app route.
        if (ExternalMediaIntents.parseIntentHref(trimmed) != null) {
            return ExternalMediaIntents.open(context, trimmed)
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(trimmed))
                )
                true
            } catch (_: Exception) {
                false
            }
        }
        siteNavigator?.invoke(trimmed)
        return siteNavigator != null
    }

    /** First non-empty media URL (Android park ladder: master → src → web → mobile). */
    private fun firstMediaSrc(vararg candidates: String?): String? {
        for (c in candidates) {
            val s = c?.trim().orEmpty()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun looksLikeMedia(src: String): Boolean {
        val s = src.lowercase()
        return s.endsWith(".mkv") || s.endsWith(".mp4") || s.endsWith(".webm") || s.endsWith(".m4v")
            || s.contains(".mkv?") || s.contains(".mp4?") || s.contains(".webm?")
    }

    private fun wantsHorizontalScroll(props: JSONObject): Boolean {
        val flag = propString(props, "data-relay-scroll") ?: propString(props, "dataRelayScroll")
        if (flag == "x") return true
        val style = styleFromProps(props) ?: return false
        val dir = styleValue(style, "flexDirection").lowercase()
        val overflow = (styleValue(style, "overflowX") + styleValue(style, "overflow")).lowercase()
        return dir == "row" && (overflow.contains("auto") || overflow.contains("scroll"))
    }

    private fun buildHorizontalRow(
        props: JSONObject,
        children: org.json.JSONArray?,
        onRenderRequested: () -> Unit,
    ): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(row)
        if (children != null) {
            addChildren(row, children, onRenderRequested, null, true, dp(12), null)
        }
        applyProps(scroller, "div", props, onRenderRequested)
        markFocusable(scroller, "div", props)
        return scroller
    }

    private fun markFocusable(view: View, tag: String, props: JSONObject) {
        val interactive = tag.lowercase() in setOf("a", "button", "input", "checkbox")
            || view.isClickable
            || propString(props, "href") != null
        if (!interactive) return
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnFocusChangeListener { v, has -> v.elevation = if (has) 8f else 0f }
    }

    /**
     * Soft-retry image loads (transient 404 while path-cache/IPFS catch up).
     * Backoff: 250 → 500 → 1000 → 2000 → 4000ms, max 6 attempts; cache-bust `_rg=`.
     */
    private fun loadImage(view: ImageView, src: String?, maxAttempts: Int = 6) {
        val base = RelayMediaUrls.resolve(src) ?: return
        fun cacheBust(url: String, attempt: Int): String {
            if (attempt <= 0) return url
            val hashIdx = url.indexOf('#')
            val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
            val noHash = if (hashIdx >= 0) url.substring(0, hashIdx) else url
            val qIdx = noHash.indexOf('?')
            val path = if (qIdx >= 0) noHash.substring(0, qIdx) else noHash
            val qs = if (qIdx >= 0) noHash.substring(qIdx + 1) else ""
            val parts = qs.split('&').filter { it.isNotEmpty() && !it.startsWith("_rg=") }.toMutableList()
            parts.add("_rg=$attempt")
            return path + "?" + parts.joinToString("&") + hash
        }
        fun backoffMs(attemptIndex: Int): Long = when {
            attemptIndex <= 0 -> 250L
            attemptIndex == 1 -> 500L
            attemptIndex == 2 -> 1000L
            attemptIndex == 3 -> 2000L
            else -> 4000L
        }
        Thread {
            var attempt = 0
            while (attempt < maxAttempts) {
                val url = cacheBust(base, attempt)
                try {
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        view.post { view.setImageBitmap(bmp) }
                        return@Thread
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "img load failed attempt=$attempt $url: ${e.message}")
                }
                attempt += 1
                if (attempt >= maxAttempts) break
                try {
                    Thread.sleep(backoffMs(attempt - 1))
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            Log.w(TAG, "img load exhausted retries for $base")
        }.start()
    }

    private fun buildRelayPopImage(props: JSONObject, onRenderRequested: () -> Unit): View {
        val src = propString(props, "src").orEmpty()
        val alt = propString(props, "alt").orEmpty()
        val highRes = propString(props, "highResSrc").orEmpty()
        val retry = propString(props, "retry")?.toIntOrNull() ?: 6
        if (src.isNotEmpty()) {
            RelayGalleryStore.register(src, alt, highRes)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val img = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            contentDescription = alt.ifEmpty { "Figure" }
        }
        column.addView(
            img,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        if (alt.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = alt
                setTextColor(Color.GRAY)
                textSize = 12f
            })
        }
        loadImage(img, src, retry)
        column.isClickable = true
        column.setOnClickListener {
            if (src.isNotEmpty()) {
                RelayGalleryStore.openAt(src)
                RelayGalleryStore.presentIfOpen(context)
                onRenderRequested()
            }
        }
        return column
    }

    private fun buildRelayImageGallery(): View {
        // Slot for ImageGalleryRoot; overlay shown via RelayGalleryStore.presentIfOpen.
        return View(context).apply {
            visibility = View.GONE
            tag = "relay-image-gallery"
        }
    }

    private fun applyRelayTypeface(view: TextView, style: JSONObject) {
        val family = styleValue(style, "fontFamily").lowercase()
        val weightStr = styleValue(style, "fontWeight").lowercase()
        val androidStyle = styleValue(style, "androidTypefaceStyle").lowercase()
        val mono = family.contains("mono")
        val serif = family.contains("serif") || family.contains("georgia") || family.contains("times")
        val boldFromWeight = weightStr == "bold"
            || (weightStr.toIntOrNull()?.let { it >= 600 } == true)
        val boldFromAndroid = androidStyle == "bold" || androidStyle == "bold_italic"
        val italic = androidStyle == "italic" || androidStyle == "bold_italic"
        val bold = boldFromWeight || boldFromAndroid
        val base = when {
            mono -> Typeface.MONOSPACE
            serif -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }
        val styleBits = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        view.typeface = Typeface.create(base, styleBits)
    }

    private fun styleFromProps(props: JSONObject?): JSONObject? {
        if (props == null) return null
        val styleObj = props.optJSONObject("style") ?: (props.opt("style") as? JSONObject)
        return styleObj?.optJSONObject("value") ?: styleObj
    }

    private fun styleInt(style: JSONObject?, key: String): Int? {
        if (style == null) return null
        val v = style.opt(key) ?: return null
        return when (v) {
            is Number -> v.toInt()
            is JSONObject -> v.optInt("value", -1).let { if (it == -1) null else it }
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    private fun styleValue(style: JSONObject, key: String): String {
        val v = style.opt(key) ?: return ""
        return when (v) {
            is String -> v
            is JSONObject -> v.optString("value", v.optString("String", ""))
            else -> v.toString()
        }
    }

    private fun styleDouble(style: JSONObject, key: String): Double? {
        val v = style.opt(key) ?: return null
        return when (v) {
            is Number -> v.toDouble()
            is JSONObject -> v.optDouble("value", Double.NaN).let { if (it.isNaN()) null else it }
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun propString(props: JSONObject, key: String): String? {
        val v = props.opt(key) ?: return null
        return when (v) {
            is String -> v
            is JSONObject -> v.optString("value").ifEmpty { v.optString("String") }.ifEmpty { null }
            else -> v.toString()
        }
    }

    private fun dp(px: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, px.toFloat(), context.resources.displayMetrics
    ).toInt()
}
