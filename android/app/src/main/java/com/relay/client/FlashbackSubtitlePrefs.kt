package com.relay.client

import android.content.Context

/**
 * Global subtitle auto-select policy for Flashback theater / Lab.
 *
 * - [autoEnable]: turn captions on when any text track exists (default on).
 * - [preferEnhanced]: if a track label looks like Enhanced / Citations, pick that
 *   instead of the first / “default” English track.
 */
class FlashbackSubtitlePrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoEnable: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO, value).apply()
        }

    var preferEnhanced: Boolean
        get() = prefs.getBoolean(KEY_ENHANCED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENHANCED, value).apply()
        }

    companion object {
        private const val PREFS = "flashback-subtitles"
        private const val KEY_AUTO = "autoEnable"
        private const val KEY_ENHANCED = "preferEnhanced"
    }
}

/** Pure helpers for picking among catalog / Media3 text-track labels (unit-tested). */
object FlashbackSubtitles {
    fun isEnhancedLabel(label: String): Boolean {
        val n = label.lowercase()
        return n.contains("enhanced") || n.contains("citation")
    }

    /**
     * Index into [labels] to select, or -1 if empty.
     * Prefer Enhanced when requested and present; else a “default” label; else first.
     */
    fun preferredIndex(labels: List<String>, preferEnhanced: Boolean): Int {
        if (labels.isEmpty()) return -1
        if (preferEnhanced) {
            val enh = labels.indexOfFirst { isEnhancedLabel(it) }
            if (enh >= 0) return enh
        }
        val def = labels.indexOfFirst { it.contains("default", ignoreCase = true) }
        if (def >= 0) return def
        return 0
    }
}
