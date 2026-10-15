package com.relay.client

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * flashback-logical-playlist/v1 evaluator (Kotlin mirror of LobbyPlaylist.js).
 */
object FlashbackLobbyPlaylist {
    const val FORMAT = "flashback-logical-playlist/v1"

    data class Decision(
        val action: String, // fadeOut | play | none
        val song: String? = null,
        val tracks: List<String> = emptyList(),
        val loop: Boolean = true,
        val shuffle: Boolean = false,
        val volume: Float = 0.28f,
        val fadeInMs: Int = 1200,
        val fadeOutMs: Int = 800,
        val season: String = "default",
    )

    data class HostState(
        val enabled: Boolean,
        val theater: Boolean,
        val nowMs: Long = System.currentTimeMillis(),
    )

    fun calendarSeason(
        nowMs: Long = System.currentTimeMillis(),
        start: String = "11-15",
        end: String = "01-02",
    ): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        val md = (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        val sp = start.split("-")
        val ep = end.split("-")
        val s = sp[0].toInt() * 100 + sp[1].toInt()
        val e = ep[0].toInt() * 100 + ep[1].toInt()
        return if (s > e) {
            if (md >= s || md <= e) "christmas" else "default"
        } else {
            if (md in s..e) "christmas" else "default"
        }
    }

    fun evaluate(playlistJson: String, host: HostState): Decision {
        val pl = JSONObject(playlistJson)
        val seasonCfg = pl.optJSONObject("state")
            ?.optJSONObject("season")
            ?.optJSONObject("christmas")
        val start = seasonCfg?.optString("start", "11-15") ?: "11-15"
        val end = seasonCfg?.optString("end", "01-02") ?: "01-02"
        val season = if (calendarSeason(host.nowMs, start, end) == "christmas") {
            "christmas"
        } else {
            "default"
        }
        val volume = pl.optDouble("volume", 0.28).toFloat()
        val fadeInMs = pl.optInt("fadeInMs", 1200)
        val fadeOutMs = pl.optInt("fadeOutMs", 800)
        val whenArr = pl.optJSONArray("when") ?: return Decision(
            action = "none",
            volume = volume,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            season = season,
        )
        for (i in 0 until whenArr.length()) {
            val rule = whenArr.optJSONObject(i) ?: continue
            if (!evalIf(rule.optString("if"), host.enabled, host.theater, season)) continue
            when (rule.optString("do")) {
                "fadeOut" -> {
                    return Decision(
                        action = "fadeOut",
                        volume = volume,
                        fadeInMs = fadeInMs,
                        fadeOutMs = rule.optInt("ms", fadeOutMs),
                        season = season,
                    )
                }
                "play" -> {
                    val songId = rule.optString(
                        "song",
                        pl.optString("defaultSong", "publicDomain"),
                    )
                    val song = pl.optJSONObject("songs")?.optJSONObject(songId)
                    val tracks = mutableListOf<String>()
                    val arr = song?.optJSONArray("tracks")
                    if (arr != null) {
                        for (t in 0 until arr.length()) {
                            val u = arr.optString(t).trim()
                            if (u.startsWith("http")) tracks.add(u)
                        }
                    }
                    return Decision(
                        action = "play",
                        song = songId,
                        tracks = tracks,
                        loop = song?.optBoolean("loop", true) ?: true,
                        shuffle = song?.optBoolean("shuffle", false) ?: false,
                        volume = volume,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        season = season,
                    )
                }
            }
        }
        return Decision(
            action = "none",
            volume = volume,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            season = season,
        )
    }

    private fun evalIf(
        expr: String,
        enabled: Boolean,
        theater: Boolean,
        season: String,
    ): Boolean {
        if (expr.isBlank()) return false
        val orParts = expr.split(Regex("\\s*\\|\\|\\s*"))
        for (orPart in orParts) {
            var andOk = true
            val andParts = orPart.split(Regex("\\s*&&\\s*"))
            for (raw in andParts) {
                var t = raw.trim()
                if (t.isEmpty()) continue
                var neg = false
                if (t.startsWith("!")) {
                    neg = true
                    t = t.removePrefix("!").trim()
                }
                var ok = when {
                    t == "enabled" -> enabled
                    t == "theater" -> theater
                    t.startsWith("season") && t.contains("==") -> {
                        val want = t.substringAfter("==").trim()
                        season == want
                    }
                    else -> false
                }
                if (neg) ok = !ok
                if (!ok) {
                    andOk = false
                    break
                }
            }
            if (andOk) return true
        }
        return false
    }

    fun shuffle(tracks: List<String>, seed: Long = System.currentTimeMillis()): List<String> {
        val a = tracks.toMutableList()
        var s = seed
        for (i in a.lastIndex downTo 1) {
            s = (s * 1664525L + 1013904223L) and 0xffffffffL
            val j = (s % (i + 1)).toInt()
            val tmp = a[i]
            a[i] = a[j]
            a[j] = tmp
        }
        return a
    }

    fun fetchPlaylist(origin: String): String? {
        val base = origin.trimEnd('/')
        return try {
            val url = URL("$base/hooks/client/lobby/lobby.playlist.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
