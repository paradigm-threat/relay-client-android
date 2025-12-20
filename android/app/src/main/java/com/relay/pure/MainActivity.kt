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
    private lateinit var tabHome: Button
    private lateinit var tabRepos: Button
    private lateinit var tabSettings: Button
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

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(34, 34, 34))
        }

        tabHome = Button(this).apply {
            text = "Home"
            setOnClickListener { renderHook("home.jsx") }
        }
        tabRepos = Button(this).apply {
            text = "Repos"
            setOnClickListener { renderHook("repo.jsx") }
        }
        tabSettings = Button(this).apply {
            text = "Settings"
            setOnClickListener { renderHook("settings.jsx") }
        }
        // Left tabs
        tabBar.addView(tabHome)
        tabBar.addView(tabRepos)
        tabBar.addView(tabSettings)

        // Spacer to push following views to the right
        val spacer = TextView(this).apply { text = "" }
        spacer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tabBar.addView(spacer)

        // Right-aligned runtime selector group
        val rightGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        runtimeLabel = TextView(this).apply {
            text = "Runtime: --"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
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
        rightGroup.addView(runtimeLabel)
        rightGroup.addView(runtimeReactBtn)
        rightGroup.addView(runtimeActBtn)
        tabBar.addView(rightGroup)

        jsContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rootLayout.addView(tabBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        rootLayout.addView(jsContainer)

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

    private fun renderHook(asset: String) {
        quickJSManager?.renderHook(asset)
    }
}
