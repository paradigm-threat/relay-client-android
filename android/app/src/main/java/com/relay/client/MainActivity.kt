package com.relay.client

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.app.UiModeManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.clevertree.hooktranspiler.render.HookRenderer
import com.clevertree.hooktranspiler.model.RendererMode
import com.google.gson.Gson
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var hookRenderer: HookRenderer
    private lateinit var tabContainer: LinearLayout
    private lateinit var nativeLabel: TextView
    private lateinit var versionLabel: TextView
    private lateinit var nativeJniBtn: Button
    private lateinit var nativeJnaBtn: Button
    
    private var currentHookPath: String = "home.jsx"
    private var availableThemes: List<String> = emptyList()
    private var currentTheme: String = "default"

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
        addTab("Settings", "settings.jsx")
        addTab("Repos", "repo.jsx")

        // Right-aligned runtime selector group
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(34, 34, 34))
        }

        // Version info row
        versionLabel = TextView(this).apply {
            text = "Loading versions..."
            textSize = 10f
            setTextColor(Color.rgb(150, 150, 150))
            setPadding(16, 8, 16, 8)
        }

        // Native mode selector row
        val nativeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        nativeLabel = TextView(this).apply {
            text = "Native: --"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        }
        nativeJniBtn = Button(this).apply {
            text = "ANDROID"
            textSize = 12f
            setOnClickListener { hookRenderer.setRendererMode(RendererMode.ANDROID) }
        }
        nativeJnaBtn = Button(this).apply {
            text = "ACT"
            textSize = 12f
            setOnClickListener { hookRenderer.setRendererMode(RendererMode.ACT) }
        }
        nativeRow.addView(nativeLabel)
        nativeRow.addView(nativeJniBtn)
        nativeRow.addView(nativeJnaBtn)

        bottomBar.addView(versionLabel)
        bottomBar.addView(nativeRow)

        hookRenderer = HookRenderer(this).apply {
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
        rootLayout.addView(hookRenderer)
        rootLayout.addView(bottomBar)

        setContentView(rootLayout)

        loadTheme()

        try {
            hookRenderer.onReady = { viewCount ->
                addStatus("Rendered $viewCount views")
            }
            hookRenderer.onError = { error ->
                showError("Error: ${error.message}")
            }
            hookRenderer.onThemeChanged = { themeName ->
                val prefs = getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("selected_theme", themeName).apply()
                if (currentTheme != themeName) {
                    currentTheme = themeName
                    Log.i("MainActivity", "Theme changed and persisted: $themeName")
                    // Re-render to update props in JS
                    renderHook(currentHookPath)
                }
            }
            
            // Update versions once ready
            val actVersion = "1.0.1"
            versionLabel.text = "Act: $actVersion | HookRenderer Ready"
            
            updateNativeLabel(RendererMode.ANDROID)
            
            // Default to home on startup
            renderHook("home.jsx")
        } catch (t: Throwable) {
            Log.e("MainActivity", "HookRenderer init failed", t)
            showError("Failed to initialize HookRenderer: ${t.message ?: "unknown error"}")
        }
    }

    private fun loadTheme() {
        try {
            val assetManager = assets
            val inputStream = assetManager.open("theme.yaml")
            val reader = InputStreamReader(inputStream)
            val yaml = Yaml()
            val themeMap = yaml.load<Map<String, Any>>(reader) as MutableMap<String, Any>
            
            val themes = themeMap["themes"] as? Map<String, Any> ?: emptyMap()
            availableThemes = themes.keys.toList()
            
            val prefs = getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)
            val savedTheme = prefs.getString("selected_theme", null)
            
            var themeToUse = savedTheme
            
            // Special case: if no theme saved or "default" is saved, follow system dark mode
            // This automatic switch to dark mode is NOT persisted to local storage here.
            if (themeToUse == null || themeToUse == "default") {
                val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                val isSystemDark = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
                
                if (isSystemDark) {
                    // If system is dark, try to find a dark theme
                    themeToUse = availableThemes.find { it.startsWith("dark") } ?: "dark"
                } else if (themeToUse == null) {
                    // First run fallback
                    themeToUse = availableThemes.find { it == "default" || it == "light" } ?: availableThemes.firstOrNull()
                }
            }
            
            currentTheme = themeToUse ?: "default"
            if (themes.containsKey(currentTheme)) {
                themeMap["current_theme"] = currentTheme
            }

            val gson = Gson()
            val themeJson = gson.toJson(themeMap)
            hookRenderer.setTheme(themeJson)
            Log.i("MainActivity", "Theme loaded: $currentTheme")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load theme.yaml", e)
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
        Log.i("MainActivity", "Status: $message")
    }

    private fun showError(message: String) {
        Log.e("MainActivity", "Error: $message")
    }

    private fun updateNativeLabel(mode: RendererMode) {
        val label = mode.name
        nativeLabel.text = "Mode: $label"
        val isAndroid = mode == RendererMode.ANDROID
        nativeJniBtn.isEnabled = !isAndroid
        nativeJnaBtn.isEnabled = isAndroid
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun renderHook(asset: String, props: Map<String, Any> = emptyMap()) {
        currentHookPath = asset
        val fullProps = props.toMutableMap()
        fullProps["availableThemes"] = availableThemes
        fullProps["currentTheme"] = currentTheme
        hookRenderer.loadHook(asset, fullProps)
    }
}
