package com.relay.pure

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Color

object NativeRenderer {
    private const val TAG = "NativeRenderer"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var rootContainer: ViewGroup? = null
    private val nodes = mutableMapOf<Int, View>()
    private val viewTypes = mutableMapOf<Int, String>()
    private val eventListeners = mutableMapOf<Int, MutableSet<String>>()
    private var quickJsEngine: app.cash.quickjs.QuickJs? = null

    fun setQuickJsEngine(engine: app.cash.quickjs.QuickJs) {
        quickJsEngine = engine
    }

    fun initialize(ctx: Context, root: ViewGroup) {
        context = ctx
        rootContainer = root
        Log.i(TAG, "NativeRenderer initialized")
    }

    fun clearAll() {
        mainHandler.post {
            val root = rootContainer!!
            nodes.values.forEach { view -> 
                if (view.parent != null) {
                    (view.parent as? ViewGroup)?.removeView(view) 
                }
            }
            nodes.clear()
            viewTypes.clear()
            eventListeners.clear()
            root.removeAllViews()
            Log.i(TAG, "NativeRenderer cleared all views")
        }
    }

    fun createView(tag: Int, type: String, props: Map<String, Any>) {
        val ctx = context ?: return
        Log.d(TAG, "createView: tag=$tag, type=$type")

        val view = when (type.lowercase()) {
            "div", "view" -> LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            "text", "span" -> TextView(ctx)
            "button" -> Button(ctx)
            "img", "image" -> ImageView(ctx)
            "scroll", "scrollview" -> ScrollView(ctx)
            else -> LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        }

        view.id = tag
        nodes[tag] = view
        viewTypes[tag] = type

        // Root view (tag=-1) should use FrameLayout.LayoutParams to fill parent
        if (tag == -1) {
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            view.layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        updateProps(tag, props)
        
        // Auto-add root view to rootContainer
        if (tag == -1 && rootContainer != null) {
            rootContainer!!.addView(view, 0)
            Log.d(TAG, "Auto-added root view (tag=-1) to rootContainer with MATCH_PARENT layout")
        }
        
        Log.d(TAG, "createView complete: tag=$tag")
    }

    fun updateProps(tag: Int, props: Map<String, Any>) {
        val view = nodes[tag] ?: run {
            Log.w(TAG, "updateProps: view not found for tag=$tag")
            return
        }

        if (view is TextView && props.containsKey("text")) {
            view.text = props["text"] as? String ?: ""
        }
        if (view is ImageView && props.containsKey("src")) {
            view.contentDescription = props["src"]?.toString() ?: ""
        }

        val className = props["className"] as? String
        val viewType = viewTypes[tag] ?: "div"
        if (!className.isNullOrBlank()) {
            applyThemedStyles(view, viewType, className)
        }

        val lp = (view.layoutParams as? ViewGroup.MarginLayoutParams)
            ?: ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        (props["width"] as? String)?.let { lp.width = parseLayoutDimension(it) }
        (props["height"] as? String)?.let { lp.height = parseLayoutDimension(it) }
        view.layoutParams = lp
    }

    fun removeView(tag: Int) {
        val view = nodes.remove(tag) ?: return
        viewTypes.remove(tag)
        eventListeners.remove(tag)
        (view.parent as? ViewGroup)?.removeView(view)
        Log.d(TAG, "removeView: tag=$tag")
    }

    fun addChild(parentTag: Int, childTag: Int, index: Int) {
        val root = rootContainer ?: run {
            Log.e(TAG, "addChild: rootContainer is null!")
            return
        }
        val parent = if (parentTag == -1) root else nodes[parentTag] as? ViewGroup
        val child = nodes[childTag] ?: run {
            Log.e(TAG, "addChild: child view not found for tag=$childTag")
            return
        }

        if (child.parent is ViewGroup) {
            (child.parent as ViewGroup).removeView(child)
        }

        if (parent is LinearLayout) {
            val lp = (child.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(child.layoutParams)
            child.layoutParams = lp
        }

        if (index >= 0 && parent is ViewGroup) {
            parent.addView(child, index)
            Log.d(TAG, "addChild: Added child=$childTag to parent=$parentTag at index=$index, parent childCount=${parent.childCount}")
        } else if (parent is ViewGroup) {
            parent.addView(child)
            Log.d(TAG, "addChild: Added child=$childTag to parent=$parentTag, parent childCount=${parent.childCount}")
        }
    }

    fun removeChild(parentTag: Int, childTag: Int) {
        val root = rootContainer ?: return
        val parent = if (parentTag == -1) root else nodes[parentTag] as? ViewGroup
        val child = nodes[childTag] ?: return
        parent?.removeView(child)
    }

    private fun applyThemedStyles(view: View, type: String, className: String) {
        val classes = className.split(" ").filter { it.isNotEmpty() }
        val classesJson = gson.toJson(classes)
        val themesJson = "{}"

        try {
            val stylesJson = ThemedStylerModule.getAndroidStyles(type, classesJson, themesJson)
            if (stylesJson.isNotBlank() && stylesJson != "{}") {
                val styleType = object : TypeToken<Map<String, Any>>() {}.type
                val styles: Map<String, Any> = gson.fromJson(stylesJson, styleType)
                applyStyleMap(view, styles)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Themed styler failed, falling back", e)
        }

        applyStyles(view, className)
    }

    private fun applyStyleMap(view: View, styles: Map<String, Any>) {
        for ((key, value) in styles) {
            Log.d(TAG, "applyStyleMap: key=$key, value=$value")
            when (key) {
                "backgroundColor" -> safeColor(value) { view.setBackgroundColor(it) }
                "padding" -> setPaddingAll(view, value)
                "paddingHorizontal" -> setPaddingHorizontal(view, value)
                "paddingVertical" -> setPaddingVertical(view, value)
                "width" -> {
                    val lp = (view.layoutParams as? ViewGroup.MarginLayoutParams)
                        ?: ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val parsed = parseLayoutDimension(value.toString())
                    Log.d(TAG, "Setting width: value=${value.toString()}, parsed=$parsed, MATCH_PARENT=${ViewGroup.LayoutParams.MATCH_PARENT}, WRAP_CONTENT=${ViewGroup.LayoutParams.WRAP_CONTENT}")
                    lp.width = parsed
                    view.layoutParams = lp
                }
                "height" -> {
                    val lp = (view.layoutParams as? ViewGroup.MarginLayoutParams)
                        ?: ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val parsed = parseLayoutDimension(value.toString())
                    Log.d(TAG, "Setting height: value=${value.toString()}, parsed=$parsed")
                    lp.height = parsed
                    view.layoutParams = lp
                }
                "color" -> if (view is TextView) safeColor(value) { view.setTextColor(it) }
                "fontSize" -> if (view is TextView) view.textSize = (value as? Number)?.toFloat() ?: 14f
                "textAlign" -> if (view is TextView) setTextAlign(view, value)
                "margin" -> setMargin(view, value)
                "marginHorizontal" -> setMarginHorizontal(view, value)
                "marginVertical" -> setMarginVertical(view, value)
                "flexDirection" -> if (view is LinearLayout) view.orientation = if (value.toString() == "row") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                "justifyContent", "alignItems" -> if (view is LinearLayout) setGravity(view, key, value)
                "flex" -> applyFlex(view, value)
            }
        }
    }

    private fun applyStyles(view: View, className: String) {
        val classes = className.split(" ").filter { it.isNotEmpty() }
        for (cls in classes) {
            when {
                cls.startsWith("bg-") -> applyBackgroundColor(view, cls)
                cls.startsWith("text-") -> applyTextStyle(view, cls)
                cls.startsWith("p-") -> applyPaddingClass(view, cls)
                cls.startsWith("m-") -> applyMarginClass(view, cls)
            }
        }
    }

    private fun safeColor(value: Any, apply: (Int) -> Unit) {
        val colorStr = value.toString()
        try { apply(Color.parseColor(colorStr)) } catch (_: Exception) { Log.w(TAG, "Invalid color: $colorStr") }
    }

    private fun setPaddingAll(view: View, value: Any) {
        val p = (value as? Number)?.toInt() ?: 0
        view.setPadding(p, p, p, p)
    }

    private fun setPaddingHorizontal(view: View, value: Any) {
        val p = (value as? Number)?.toInt() ?: 0
        view.setPadding(p, view.paddingTop, p, view.paddingBottom)
    }

    private fun setPaddingVertical(view: View, value: Any) {
        val p = (value as? Number)?.toInt() ?: 0
        view.setPadding(view.paddingLeft, p, view.paddingRight, p)
    }

    private fun setMargin(view: View, value: Any) {
        val m = (value as? Number)?.toInt() ?: 0
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(m, m, m, m)
        view.layoutParams = lp
    }

    private fun setMarginHorizontal(view: View, value: Any) {
        val m = (value as? Number)?.toInt() ?: 0
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(m, lp.topMargin, m, lp.bottomMargin)
        view.layoutParams = lp
    }

    private fun setMarginVertical(view: View, value: Any) {
        val m = (value as? Number)?.toInt() ?: 0
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(lp.leftMargin, m, lp.rightMargin, m)
        view.layoutParams = lp
    }

    private fun setTextAlign(view: TextView, value: Any) {
        when (value.toString()) {
            "center" -> view.textAlignment = View.TEXT_ALIGNMENT_CENTER
            "left" -> view.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            "right" -> view.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
    }

    private fun setGravity(layout: LinearLayout, key: String, value: Any) {
        val gravity = when (value.toString()) {
            "center" -> Gravity.CENTER
            "flex-start" -> Gravity.START or Gravity.TOP
            "flex-end" -> Gravity.END or Gravity.BOTTOM
            "space-between" -> Gravity.CENTER_VERTICAL
            else -> Gravity.START
        }
        if (key == "justifyContent") {
            layout.gravity = gravity
        } else {
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                val lp = (child.layoutParams as? LinearLayout.LayoutParams)
                    ?: LinearLayout.LayoutParams(child.layoutParams)
                lp.gravity = gravity
                child.layoutParams = lp
            }
        }
    }

    private fun applyFlex(view: View, value: Any) {
        val flex = (value as? Number)?.toFloat() ?: 0f
        val parent = view.parent
        if (parent is LinearLayout) {
            val lp = (view.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(view.layoutParams)
            lp.weight = flex
            if (parent.orientation == LinearLayout.VERTICAL) lp.height = 0 else lp.width = 0
            view.layoutParams = lp
        }
    }

    private fun applyBackgroundColor(view: View, className: String) {
        val colorName = className.removePrefix("bg-")
        val color = when (colorName) {
            "white" -> Color.WHITE
            "black" -> Color.BLACK
            "blue-500" -> Color.parseColor("#3b82f6")
            "red-500" -> Color.parseColor("#ef4444")
            "green-500" -> Color.parseColor("#10b981")
            "gray-100" -> Color.parseColor("#f3f4f6")
            "gray-200" -> Color.parseColor("#e5e7eb")
            else -> null
        }
        color?.let { view.setBackgroundColor(it) }
    }

    private fun applyTextStyle(view: View, className: String) {
        if (view !is TextView) return
        val style = className.removePrefix("text-")
        when {
            style.startsWith("white") -> view.setTextColor(Color.WHITE)
            style.startsWith("black") -> view.setTextColor(Color.BLACK)
            style.startsWith("blue") -> view.setTextColor(Color.parseColor("#3b82f6"))
            style.startsWith("gray") -> view.setTextColor(Color.parseColor("#6b7280"))
            style == "lg" -> view.textSize = 18f
            style == "sm" -> view.textSize = 14f
            style == "center" -> view.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }

    private fun applyPaddingClass(view: View, className: String) {
        val value = className.removePrefix("p-")
        val padding = when (value) {
            "0" -> 0
            "1" -> 4
            "2" -> 8
            "3" -> 12
            "4" -> 16
            "6" -> 24
            "8" -> 32
            else -> 16
        }
        view.setPadding(padding, padding, padding, padding)
    }

    private fun applyMarginClass(view: View, className: String) {
        val value = className.removePrefix("m-")
        val margin = when (value) {
            "0" -> 0
            "1" -> 4
            "2" -> 8
            "4" -> 16
            else -> 8
        }
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
        lp?.setMargins(margin, margin, margin, margin)
        if (lp != null) view.layoutParams = lp
    }

    private fun parseLayoutDimension(value: String): Int {
        return when {
            value == "match_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            value == "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            value == "full" || value == "100%" || value.endsWith("%") -> ViewGroup.LayoutParams.MATCH_PARENT
            value.endsWith("dp") || value.endsWith("px") -> {
                value.dropLast(2).toIntOrNull()?.let { it * 2 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
            }
            else -> value.toIntOrNull() ?: ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    fun addEventListener(tag: Int, eventName: String) {
        val view = nodes[tag] ?: run {
            Log.w(TAG, "addEventListener: view not found for tag=$tag")
            return
        }

        Log.d(TAG, "addEventListener: tag=$tag, eventName=$eventName")
        eventListeners.getOrPut(tag) { mutableSetOf() }.add(eventName)

        when (eventName) {
            "click" -> {
                view.setOnClickListener {
                    Log.d(TAG, "Click event triggered on tag=$tag")
                    triggerEvent(tag, "click", emptyMap())
                }
                view.isClickable = true
                view.isFocusable = true
            }
            else -> Log.w(TAG, "Unsupported event: $eventName")
        }
    }

    fun removeEventListener(tag: Int, eventName: String) {
        eventListeners[tag]?.remove(eventName)
        val view = nodes[tag] ?: return
        when (eventName) {
            "click" -> view.setOnClickListener(null)
        }
    }

    private fun triggerEvent(tag: Int, eventName: String, data: Map<String, Any>) {
        val engine = quickJsEngine ?: run {
            Log.w(TAG, "Cannot trigger event: QuickJS engine not set")
            return
        }

        mainHandler.post {
            try {
                val dataJson = gson.toJson(data)
                engine.evaluate(
                    "globalThis.nativeBridge._triggerEvent($tag, '$eventName', $dataJson);",
                    "event_trigger.js"
                )
                QuickJSManager.activeManager?.drainMessageQueue()
                Log.d(TAG, "Event triggered: tag=$tag, event=$eventName")
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering event: ${e.message}", e)
            }
        }
    }
}
