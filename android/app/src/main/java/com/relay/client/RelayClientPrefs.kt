package com.relay.client

import android.content.Context

/** Generic-client shell preferences. The Flashback flavor does not open this UI. */
class RelayClientPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("relay-client-shell", Context.MODE_PRIVATE)

    fun servers(): List<String> {
        val raw = prefs.getString(KEY_SERVERS, null)
        if (raw.isNullOrBlank()) return listOf(DEFAULT_ORIGIN)
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(DEFAULT_ORIGIN) }
    }

    fun addServer(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) return
        val next = (servers() + clean).distinct()
        prefs.edit().putString(KEY_SERVERS, next.joinToString("\n")).putString(KEY_ACTIVE, clean).apply()
    }

    fun activeOrigin(): String {
        return prefs.getString(KEY_ACTIVE, null)?.trim()?.ifEmpty { null } ?: servers().first()
    }

    fun setActive(url: String) {
        prefs.edit().putString(KEY_ACTIVE, url.trim().trimEnd('/')).apply()
    }

    var skipAffirmations: Boolean
        get() = prefs.getBoolean(KEY_SKIP, false)
        set(value) { prefs.edit().putBoolean(KEY_SKIP, value).apply() }

    var reloadHours: Int
        get() = prefs.getInt(KEY_HOURS, 24).coerceIn(1, 24 * 14)
        set(value) { prefs.edit().putInt(KEY_HOURS, value.coerceIn(1, 24 * 14)).apply() }

    companion object {
        const val DEFAULT_ORIGIN = "https://flashbackrepository.org"
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active"
        private const val KEY_SKIP = "skipAffirmations"
        private const val KEY_HOURS = "reloadHours"
    }
}
