package com.relay.client

import android.content.Context

/**
 * Lobby music mute preference (web-parity with localStorage `fb-lobby-music-enabled`).
 * Default **on** for APK (plan: APK lobby on by default).
 */
object FlashbackLobbyPrefs {
    private const val PREFS = "flashback-lobby"
    const val KEY_ENABLED = "fb-lobby-music-enabled"
    const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        setEnabled(context, next)
        return next
    }

    fun label(enabled: Boolean): String =
        if (enabled) "Music: on" else "Music: off"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
