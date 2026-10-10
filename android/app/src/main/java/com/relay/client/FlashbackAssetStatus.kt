package com.relay.client

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gateway seed probe (`?relayAssetStatus=1`) — same signal the web Ways to watch panel uses.
 */
object FlashbackAssetStatus {
    data class Result(
        val ready: Boolean,
        val seedState: String = "",
        val providers: Int? = null,
        val message: String = "",
    )

    fun probeUrl(mediaUrl: String, timeoutMs: Int = 10_000): Result {
        val base = mediaUrl.trim()
        if (base.isEmpty()) return Result(ready = false, message = "No media URL")
        val statusUrl = base + (if (base.contains('?')) "&" else "?") + "relayAssetStatus=1"
        return try {
            val conn = URL(statusUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "Accept",
                "application/vnd.relay.asset-status+json, application/json",
            )
            try {
                val code = conn.responseCode
                val body =
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()
                        ?.readText()
                        .orEmpty()
                if (body.isBlank()) {
                    Result(ready = false, message = "Empty status (HTTP $code)")
                } else {
                    parse(body)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(ready = false, message = e.message.orEmpty())
        }
    }

    fun parse(json: String): Result {
        return try {
            val root = JSONObject(json)
            val seed = root.optJSONObject("seed")
            val state = seed?.optString("state").orEmpty()
            val providers =
                if (seed != null && seed.has("providers") && !seed.isNull("providers")) {
                    seed.optInt("providers")
                } else {
                    null
                }
            val message = root.optString("message")
            val error = root.optString("error")
            val ready = error == "ok" || state.equals("cached", ignoreCase = true)
            Result(
                ready = ready,
                seedState = state,
                providers = providers,
                message = message,
            )
        } catch (_: Exception) {
            Result(ready = false, message = "Invalid status JSON")
        }
    }
}
