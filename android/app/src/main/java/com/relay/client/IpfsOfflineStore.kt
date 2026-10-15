package com.relay.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-level record of CIDs the user asked to Keep offline (pins / seeds).
 * Kubo pin state is authoritative; this drives UI lists / toggles.
 *
 * [seedingEnabled] is a master switch: when false, kept titles stay listed but
 * the node should not advertise / run for seeding until re-enabled.
 */
class IpfsOfflineStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Entry(
        val cid: String,
        val title: String,
        val slug: String,
        val keptAtMs: Long,
        val bytesHint: Long = 0L,
    )

    fun seedingEnabled(): Boolean = prefs.getBoolean(KEY_SEEDING_ENABLED, true)

    fun setSeedingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEEDING_ENABLED, enabled).apply()
    }

    fun isKept(cid: String): Boolean {
        val c = cid.trim()
        if (c.isEmpty()) return false
        return all().any { it.cid == c }
    }

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cid = o.optString("cid").trim()
                if (cid.isEmpty()) continue
                out.add(
                    Entry(
                        cid = cid,
                        title = o.optString("title"),
                        slug = o.optString("slug"),
                        keptAtMs = o.optLong("keptAtMs"),
                        bytesHint = o.optLong("bytesHint"),
                    ),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun keep(cid: String, title: String, slug: String, bytesHint: Long = 0L) {
        val c = cid.trim()
        if (c.isEmpty()) return
        val next = all().filterNot { it.cid == c }.toMutableList()
        next.add(
            0,
            Entry(
                cid = c,
                title = title,
                slug = slug,
                keptAtMs = System.currentTimeMillis(),
                bytesHint = bytesHint,
            ),
        )
        write(next)
    }

    fun remove(cid: String) {
        val c = cid.trim()
        if (c.isEmpty()) return
        write(all().filterNot { it.cid == c })
    }

    fun clearAll() {
        write(emptyList())
    }

    private fun write(entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("cid", e.cid)
                    .put("title", e.title)
                    .put("slug", e.slug)
                    .put("keptAtMs", e.keptAtMs)
                    .put("bytesHint", e.bytesHint),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "flashback_ipfs_offline"
        private const val KEY = "kept"
        private const val KEY_SEEDING_ENABLED = "seeding_enabled"
    }
}
