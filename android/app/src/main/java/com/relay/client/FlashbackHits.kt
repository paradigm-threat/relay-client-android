package com.relay.client

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Affirmation-gated hit beacons — same API as web Affirmation.host.js `reportHit`.
 *
 * Semantics (match web): local ack is written first; POST is fire-and-forget.
 * If the network fails after the write, the hit is undercounted on purpose (no retry).
 */
object FlashbackHits {
    const val KIND_SITE = "site_view"
    const val KIND_MOVIE = "movie_view"

    data class Hit(val kind: String, val path: String? = null)

    /**
     * Which beacons to fire after a successful local ack write.
     * Mirrors Affirmation.host.js `persist()`.
     */
    fun hitsToReport(
        storageKey: String,
        already: Boolean,
        hadWelcome: Boolean,
        slugHint: String? = null,
    ): List<Hit> {
        val isTitle = storageKey.startsWith(FlashbackOwnership.MOVIE_PREFIX)
        val isWelcome = storageKey == FlashbackOwnership.WELCOME_KEY
        val out = ArrayList<Hit>(2)
        if (isTitle && !hadWelcome) {
            out.add(Hit(KIND_SITE))
        }
        if (!already) {
            if (isTitle) {
                val slug = (slugHint ?: slugFromKey(storageKey)).trim().trimStart('/')
                if (slug.isNotEmpty()) out.add(Hit(KIND_MOVIE, "/$slug"))
            } else if (isWelcome) {
                out.add(Hit(KIND_SITE))
            }
        }
        return out
    }

    fun slugFromKey(storageKey: String): String {
        if (!storageKey.startsWith(FlashbackOwnership.MOVIE_PREFIX)) return ""
        return storageKey.removePrefix(FlashbackOwnership.MOVIE_PREFIX)
    }

    fun moviePath(slug: String): String {
        val s = slug.trim().trimStart('/')
        return if (s.isEmpty()) "" else "/$s"
    }

    fun payloadJson(hit: Hit): String {
        val kind = jsonEscape(hit.kind)
        val path = hit.path
        return if (path.isNullOrBlank()) {
            """{"kind":"$kind"}"""
        } else {
            """{"kind":"$kind","path":"${jsonEscape(path)}"}"""
        }
    }

    /** Minimal JSON string escape for hit payloads (kind/path are ASCII slugs). */
    fun jsonEscape(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) {
                    out.append("\\u").append("%04x".format(c.code))
                } else {
                    out.append(c)
                }
            }
        }
        return out.toString()
    }

    /**
     * Persist ownership ack (same SharedPreferences as web localStorage keys),
     * then POST unique hit(s) at most once per site / per movie.
     */
    fun acknowledge(context: Context, origin: String, storageKey: String, slugHint: String? = null) {
        val already = FlashbackOwnership.hasAck(context, storageKey)
        val hadWelcome = FlashbackOwnership.hasAck(context, FlashbackOwnership.WELCOME_KEY)
        FlashbackOwnership.setAck(context, storageKey)
        val hits = hitsToReport(storageKey, already, hadWelcome, slugHint)
        if (hits.isEmpty()) return
        postAsync(origin, hits)
    }

    fun acknowledgeTitle(context: Context, origin: String, slug: String) {
        acknowledge(context, origin, FlashbackOwnership.movieKey(slug), slug)
    }

    fun acknowledgeWelcome(context: Context, origin: String) {
        acknowledge(context, origin, FlashbackOwnership.WELCOME_KEY, null)
    }

    internal fun hitsUrl(origin: String): String {
        val base = origin.trim().trimEnd('/')
        return "$base/api/hits"
    }

    private fun postAsync(origin: String, hits: List<Hit>) {
        val url = hitsUrl(origin)
        Thread {
            for (hit in hits) {
                try {
                    postOne(url, hit)
                } catch (_: Exception) {
                    /* optional — match web .catch(() => {}) */
                }
            }
        }.start()
    }

    private fun postOne(url: String, hit: Hit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.instanceFollowRedirects = true
        try {
            conn.outputStream.use { os ->
                os.write(payloadJson(hit).toByteArray(Charsets.UTF_8))
            }
            // Drain response; ignore status — web does not retry on failure.
            try {
                conn.inputStream?.close()
            } catch (_: Exception) {
                try {
                    conn.errorStream?.close()
                } catch (_: Exception) {
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
