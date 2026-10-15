package com.relay.client

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class FlashbackChatChannel(
    val name: String,
    val topic: String = "",
    val messages: Int = 0,
)

data class FlashbackChatMessage(
    val id: String,
    val ts: String,
    val channel: String,
    val nick: String,
    val kind: String,
    val text: String,
    val local: Boolean = false,
)

/**
 * REST helpers for Flashback chat-edge (same paths as web ChatCommon).
 */
object FlashbackChatApi {
    const val DEFAULT_CHANNEL = "flashback"

    fun normalizeChannel(raw: String?): String {
        var c = (raw ?: "").trim().lowercase().removePrefix("#")
        c = c.replace(Regex("[^a-z0-9_\\-./]"), "").take(64)
        return c.ifBlank { DEFAULT_CHANNEL }
    }

    fun wsUrl(origin: String): String {
        val base = origin.trim().trimEnd('/')
        val uri = URL(if (base.startsWith("http")) base else "https://$base")
        val proto = if (uri.protocol.equals("https", ignoreCase = true)) "wss" else "ws"
        val host = if (uri.port > 0 && uri.port != uri.defaultPort) {
            "${uri.host}:${uri.port}"
        } else {
            uri.host
        }
        return "$proto://$host/chat/ws"
    }

    fun fetchHealth(origin: String): Boolean {
        val j = fetchJson(origin, "/chat/api/health") ?: return false
        return j.optBoolean("ok", false)
    }

    fun fetchChannels(origin: String, q: String = ""): List<FlashbackChatChannel> {
        val path =
            if (q.isBlank()) "/chat/api/channels"
            else "/chat/api/channels?q=${encodeQuery(q)}"
        val j = fetchJson(origin, path) ?: return emptyList()
        val arr = j.optJSONArray("channels") ?: return emptyList()
        return parseChannels(arr)
    }

    fun fetchHistory(origin: String, channel: String, limit: Int = 50): List<FlashbackChatMessage> {
        val ch = normalizeChannel(channel)
        val path = "/chat/api/history?channel=${encodeQuery(ch)}&limit=$limit"
        val j = fetchJson(origin, path) ?: return emptyList()
        val arr = j.optJSONArray("messages") ?: return emptyList()
        return parseMessages(arr)
    }

    fun fetchName(origin: String, mode: String = "theater"): String {
        val path = "/chat/api/name?mode=${encodeQuery(mode)}"
        val j = fetchJson(origin, path) ?: return "Lobby-A1"
        return j.optString("nick").ifBlank { "Lobby-A1" }
    }

    fun parseChannels(arr: JSONArray): List<FlashbackChatChannel> {
        val out = ArrayList<FlashbackChatChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = normalizeChannel(o.optString("name").ifBlank { o.optString("key") })
            out.add(
                FlashbackChatChannel(
                    name = name,
                    topic = o.optString("topic"),
                    messages = o.optInt("messages", 0),
                ),
            )
        }
        return out
    }

    fun parseMessages(arr: JSONArray): List<FlashbackChatMessage> {
        val out = ArrayList<FlashbackChatMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseMessageObject(o)?.let { out.add(it) }
        }
        return out
    }

    fun parseMessageObject(o: JSONObject): FlashbackChatMessage? {
        val text = o.optString("text")
        val nick = o.optString("nick")
        val channel = normalizeChannel(o.optString("channel"))
        if (text.isBlank() && nick.isBlank()) return null
        return FlashbackChatMessage(
            id = o.optString("id").ifBlank { "${System.currentTimeMillis()}" },
            ts = o.optString("ts"),
            channel = channel,
            nick = nick,
            kind = o.optString("kind").ifBlank { "privmsg" },
            text = text,
            local = o.optBoolean("local", false),
        )
    }

    /** Parse a WS JSON frame; returns null for non-message control events. */
    fun parseWsEvent(raw: String): Pair<String, JSONObject>? {
        return try {
            val o = JSONObject(raw)
            val type = o.optString("type").ifBlank { return null }
            type to o
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchJson(origin: String, path: String): JSONObject? {
        val base = origin.trim().trimEnd('/')
        val url = if (path.startsWith("http")) path else "$base$path"
        val text = httpGet(url) ?: return null
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeQuery(s: String): String {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch)
            } else if (c == ' '.code) {
                out.append('+')
            } else {
                out.append('%')
                out.append("0123456789ABCDEF"[c ushr 4])
                out.append("0123456789ABCDEF"[c and 0x0f])
            }
        }
        return out.toString()
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
