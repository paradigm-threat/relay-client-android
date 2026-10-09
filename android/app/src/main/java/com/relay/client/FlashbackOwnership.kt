package com.relay.client

import android.content.Context

/**
 * Same keys as the web / CleverScript Affirmation hosts so ack state is shared
 * if the user later opens the hook-based shell.
 */
object FlashbackOwnership {
    private const val PREFS = "relay-local-storage"
    private const val KEY_JSON = "json"
    const val WELCOME_KEY = "flashback.ownershipAck.v1"
    const val MOVIE_PREFIX = "flashback.movieOwn.v1."

    fun movieKey(slug: String): String = "$MOVIE_PREFIX$slug"

    fun hasAck(context: Context, key: String): Boolean {
        return readMap(context)[key] == "1"
    }

    fun setAck(context: Context, key: String) {
        val map = readMap(context).toMutableMap()
        map[key] = "1"
        if (key.startsWith("flashback.movieOwn.v1.")) {
            map[WELCOME_KEY] = "1"
        }
        writeMap(context, map)
    }

    private fun readMap(context: Context): Map<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, "{}") ?: "{}"
        return try {
            val obj = org.json.JSONObject(raw)
            val out = HashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optString(k)
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeMap(context: Context, map: Map<String, String>) {
        val obj = org.json.JSONObject()
        for ((k, v) in map) obj.put(k, v)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, obj.toString())
            .apply()
    }
}
