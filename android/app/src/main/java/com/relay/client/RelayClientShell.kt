package com.relay.client

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.clevertree.relayruntime.demo.HookRenderer
import java.io.File

/**
 * Generic Relay client chrome: server tabs and shell settings.
 * Not constructed when [BuildConfig.RELAY_LOCK_ORIGIN] is set.
 */
class RelayClientShell(context: Context) : LinearLayout(context) {
    private val prefs = RelayClientPrefs(context)
    private val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val hook = HookRenderer(context)

    init {
        orientation = VERTICAL
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(HorizontalScrollView(context).apply {
            addView(tabRow)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(Button(context).apply {
            text = "Settings"
            setOnClickListener { showSettings() }
        })
        addView(bar)
        hook.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
        addView(hook)
        rebuildTabs()
        open(prefs.activeOrigin())
    }

    private fun rebuildTabs() {
        tabRow.removeAllViews()
        for (origin in prefs.servers()) {
            tabRow.addView(Button(context).apply {
                text = origin.removePrefix("https://").removePrefix("http://")
                isFocusable = true
                setOnClickListener {
                    prefs.setActive(origin)
                    open(origin)
                }
            })
        }
    }

    private fun open(origin: String) {
        prefs.setActive(origin)
        hook.host = origin
        hook.hookPath = "hooks/client/get.jsx"
        hook.routePath = "/"
        hook.skipAffirmations = prefs.skipAffirmations
        hook.reloadIntervalMs = prefs.reloadHours * 60L * 60L * 1000L
        hook.runRender()
    }

    private fun showSettings() {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val skip = CheckBox(context).apply {
            text = "Skip affirmation checks"
            isChecked = prefs.skipAffirmations
        }
        val hours = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.reloadHours.toString())
            hint = "Hook reload hours"
        }
        val add = EditText(context).apply {
            hint = "https://example.relaygateway.net"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(skip)
        box.addView(hours)
        box.addView(add)
        val saved = MovieDownloadService.savedMovies(context)
        if (saved.isEmpty()) {
            box.addView(android.widget.TextView(context).apply { text = "No offline movies" })
        } else {
            for (file in saved) {
                box.addView(Button(context).apply {
                    text = "Delete ${file.name} (${file.length() / (1024 * 1024)} MB)"
                    setOnClickListener {
                        file.delete()
                        File(file.path + ".done").delete()
                        text = "Deleted"
                        isEnabled = false
                    }
                })
            }
        }
        AlertDialog.Builder(context)
            .setTitle("Relay")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.skipAffirmations = skip.isChecked
                prefs.reloadHours = hours.text?.toString()?.toIntOrNull() ?: 24
                val extra = add.text?.toString()?.trim().orEmpty()
                if (extra.startsWith("http://") || extra.startsWith("https://")) {
                    prefs.addServer(extra)
                    rebuildTabs()
                    open(extra.trim().trimEnd('/'))
                } else {
                    open(prefs.activeOrigin())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
