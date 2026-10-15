package com.relay.client

import android.content.Context

/**
 * Persisted chat nick + notification channel picks.
 * Default: notify only `#flashback`; background IRC listen off.
 */
object FlashbackChatPrefs {
    private const val PREFS = "flashback-chat"
    private const val KEY_NICK = "nick"
    private const val KEY_CHANNELS = "notifyChannels"
    private const val KEY_BG = "listenWhileBackground"
    private const val KEY_QUIET = "quietMinutes"
    private const val KEY_LAST_ALERT = "lastAlertMs"
    private const val KEY_DIGEST_JSON = "digestJson"
    private const val SEP = "\n"

    const val DEFAULT_QUIET_MINUTES = 10

    fun nick(context: Context): String =
        prefs(context).getString(KEY_NICK, "")?.trim().orEmpty()

    fun setNick(context: Context, nick: String) {
        prefs(context).edit().putString(KEY_NICK, nick.trim().take(24)).apply()
    }

    fun notifyChannels(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_CHANNELS, null)
        if (raw == null) {
            return setOf(FlashbackChatApi.DEFAULT_CHANNEL)
        }
        if (raw.isEmpty()) return emptySet()
        return raw.split(SEP)
            .map { FlashbackChatApi.normalizeChannel(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun setNotifyChannels(context: Context, channels: Collection<String>) {
        val normalized = channels
            .map { FlashbackChatApi.normalizeChannel(it) }
            .filter { it.isNotBlank() }
            .toSortedSet()
        prefs(context).edit().putString(KEY_CHANNELS, normalized.joinToString(SEP)).apply()
    }

    fun selectAll(context: Context, all: Collection<String>) {
        setNotifyChannels(context, all)
    }

    fun selectNone(context: Context) {
        setNotifyChannels(context, emptyList())
    }

    fun listenWhileBackground(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BG, false)

    fun setListenWhileBackground(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BG, enabled).apply()
    }

    fun quietMinutes(context: Context): Int {
        val v = prefs(context).getInt(KEY_QUIET, DEFAULT_QUIET_MINUTES)
        return v.coerceIn(1, 120)
    }

    fun setQuietMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_QUIET, minutes.coerceIn(1, 120)).apply()
    }

    fun lastAlertMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_ALERT, 0L)

    fun setLastAlertMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LAST_ALERT, ms).apply()
    }

    fun digestJson(context: Context): String =
        prefs(context).getString(KEY_DIGEST_JSON, "") ?: ""

    fun setDigestJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_DIGEST_JSON, json).apply()
    }

    fun clearDigest(context: Context) {
        prefs(context).edit().remove(KEY_DIGEST_JSON).apply()
    }

    /** Encode channel→count map for digest persistence (unit-tested). */
    fun encodeDigestCounts(counts: Map<String, Int>): String {
        if (counts.isEmpty()) return ""
        return counts.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
    }

    fun decodeDigestCounts(raw: String): MutableMap<String, Int> {
        val out = linkedMapOf<String, Int>()
        if (raw.isBlank()) return out
        for (part in raw.split("|")) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val ch = FlashbackChatApi.normalizeChannel(part.substring(0, eq))
            val n = part.substring(eq + 1).toIntOrNull() ?: continue
            if (ch.isNotBlank() && n > 0) out[ch] = n
        }
        return out
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
