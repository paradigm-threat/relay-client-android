package com.relay.pure

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var jsContainer: FrameLayout
    private lateinit var tabContainer: LinearLayout
    private lateinit var runtimeLabel: TextView
    private lateinit var runtimeReactBtn: Button
    private lateinit var runtimeActBtn: Button
    private var quickJSManager: QuickJSManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native layout: vertical with tab bar and JS render container
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val scrollTabBar = android.widget.HorizontalScrollView(this).apply {
            setBackgroundColor(Color.rgb(34, 34, 34))
        }

        tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        scrollTabBar.addView(tabContainer)

        // Add default tabs
        addTab("Home", "home.jsx")
        addTab("Repos", "repo.jsx")
        addTab("Settings", "settings.jsx")

        // Right-aligned runtime selector group
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(34, 34, 34))
        }

        runtimeLabel = TextView(this).apply {
            text = "Runtime: --"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        }
        runtimeReactBtn = Button(this).apply {
            text = "React"
            textSize = 12f
            setOnClickListener { quickJSManager?.setRuntimeMode(RuntimeMode.REACT) }
        }
        runtimeActBtn = Button(this).apply {
            text = "Act"
            textSize = 12f
            setOnClickListener { quickJSManager?.setRuntimeMode(RuntimeMode.ACT) }
        }
        bottomBar.addView(runtimeLabel)
        bottomBar.addView(runtimeReactBtn)
        bottomBar.addView(runtimeActBtn)

        jsContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rootLayout.addView(scrollTabBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        rootLayout.addView(jsContainer)
        rootLayout.addView(bottomBar)

        setContentView(rootLayout)

        NativeRenderer.initialize(this, jsContainer)

        try {
            quickJSManager = QuickJSManager(this).also { manager ->
                manager.userMessageHandler = { message, isError ->
                    if (isError) showError(message) else addStatus(message)
                }
                manager.runtimeChangedHandler = { mode -> updateRuntimeLabel(mode) }
                manager.initialize()
            }
            addStatus("QuickJS runtime initialized")
            renderHook("home.jsx")
        } catch (e: Exception) {
            Log.e("MainActivity", "QuickJS init failed", e)
            showError("Failed to initialize QuickJS: ${e.message}")
        }
    }

    fun addTab(label: String, asset: String, props: Map<String, Any> = emptyMap()) {
        val tabButton = Button(this).apply {
            text = label
            setOnClickListener { renderHook(asset, props) }
        }
        tabContainer.addView(tabButton)
    }

    private fun addStatus(message: String) {
        val textView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        jsContainer.addView(textView)
    }

    private fun showError(message: String) {
        val textView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.RED)
        }
        jsContainer.addView(textView)
    }

    private fun updateRuntimeLabel(mode: RuntimeMode) {
        val label = mode.mode.replaceFirstChar { it.uppercase() }
        runtimeLabel.text = "Runtime: ${label}"
        // Simple visual indication of active runtime
        val isReact = mode == RuntimeMode.REACT
        runtimeReactBtn.isEnabled = !isReact
        runtimeActBtn.isEnabled = isReact
    }

    override fun onDestroy() {
        quickJSManager?.destroy()
        super.onDestroy()
    }

    private fun renderHook(asset: String, props: Map<String, Any> = emptyMap()) {
        quickJSManager?.renderHook(asset, props)
    }
}
