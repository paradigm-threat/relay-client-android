package com.relay.client

import android.content.Context

/**
 * Theater atmosphere theme (web-parity with `flashback.theaterTheme.v1`).
 * Default: [THEME_VELVET] (illustrated curtains). Alternate: [THEME_CLASSIC] (photo aisle).
 */
object FlashbackTheaterPrefs {
    const val THEME_VELVET = "velvet"
    const val THEME_CLASSIC = "classic"
    const val DEFAULT_THEME = THEME_VELVET

    private const val PREFS = "flashback-theater"
    private const val KEY_THEME = "theme"

    fun theme(context: Context): String =
        normalize(prefs(context).getString(KEY_THEME, DEFAULT_THEME))

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, normalize(theme)).apply()
    }

    fun cycle(context: Context): String {
        val next =
            if (theme(context) == THEME_CLASSIC) THEME_VELVET else THEME_CLASSIC
        setTheme(context, next)
        return next
    }

    fun label(theme: String): String =
        when (normalize(theme)) {
            THEME_CLASSIC -> "Classic"
            else -> "Velvet"
        }

    fun normalize(raw: String?): String {
        val t = raw?.trim()?.lowercase().orEmpty()
        return if (t == THEME_CLASSIC) THEME_CLASSIC else THEME_VELVET
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
