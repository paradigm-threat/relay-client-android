package com.relay.client

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class FlashbackProject(
    val slug: String,
    val category: String,
    val path: String,
    val title: String,
    val originalTitle: String,
    val status: String,
    val media: String,
    val mediaWeb: String,
    val mediaMobile: String,
    val image: String,
    val blurb: String,
) {
    /** Progressive HTTPS master URL with percent-encoded path segments (spaces, etc.). */
    fun masterStreamUrl(origin: String): String? {
        if (media.isBlank()) return null
        return FlashbackCatalog.joinOriginPath(origin, path, media)
    }

    fun posterUrl(origin: String): String? {
        if (image.isBlank()) return null
        val img = image.trim()
        if (img.startsWith("http://") || img.startsWith("https://")) return img
        return FlashbackCatalog.joinOriginPath(origin, img)
    }

    val isShowing: Boolean get() = status.equals("showing", ignoreCase = true)

    val hasStreamMedia: Boolean
        get() = media.isNotBlank() || mediaWeb.isNotBlank() || mediaMobile.isNotBlank()
}

/** Parsed `index.json` — projects plus optional `nowShowing` slug order. */
data class FlashbackLibrary(
    val projects: List<FlashbackProject>,
    val nowShowingSlugs: List<String> = emptyList(),
)

object FlashbackCatalog {
    fun fetch(origin: String): FlashbackLibrary {
        val base = origin.trim().trimEnd('/')
        val text = httpGet("$base/index.json") ?: throw IllegalStateException("Could not load library")
        return parseIndexJson(text)
    }

    /** Parse library JSON (unit-testable without network). */
    fun parseIndexJson(text: String): FlashbackLibrary {
        val root = JSONObject(text)
        val projects = root.optJSONArray("projects") ?: JSONArray()
        val out = ArrayList<FlashbackProject>(projects.length())
        for (i in 0 until projects.length()) {
            val o = projects.optJSONObject(i) ?: continue
            out.add(
                FlashbackProject(
                    slug = o.optString("slug"),
                    category = o.optString("category"),
                    path = o.optString("path"),
                    title = o.optString("title").ifBlank { o.optString("slug") },
                    originalTitle = o.optString("originalTitle").ifBlank { o.optString("title") },
                    status = o.optString("status"),
                    media = o.optString("media"),
                    mediaWeb = o.optString("mediaWeb"),
                    mediaMobile = o.optString("mediaMobile"),
                    image = o.optString("image"),
                    blurb = o.optString("blurb"),
                ),
            )
        }
        val nowShowing = root.optJSONArray("nowShowing")
        val slugs = ArrayList<String>()
        if (nowShowing != null) {
            for (i in 0 until nowShowing.length()) {
                val s = nowShowing.optString(i).trim()
                if (s.isNotEmpty()) slugs.add(s)
            }
        }
        return FlashbackLibrary(out, slugs)
    }

    /**
     * Catalog items for Now Showing — same rules as web `nowShowingItemsJson`:
     * prefer `index.nowShowing` slug order; fall back to `status=showing`.
     */
    fun resolveNowShowing(
        projects: List<FlashbackProject>,
        nowShowingSlugs: List<String>,
    ): List<FlashbackProject> {
        if (nowShowingSlugs.isNotEmpty()) {
            val bySlug = HashMap<String, FlashbackProject>(projects.size)
            for (p in projects) {
                if (p.slug.isNotBlank()) bySlug[p.slug] = p
            }
            val out = ArrayList<FlashbackProject>(nowShowingSlugs.size)
            for (slug in nowShowingSlugs) {
                bySlug[slug]?.let { out.add(it) }
            }
            return out
        }
        return projects.filter { it.isShowing }
    }

    /** Site markdown (`content/setup.md`, README under a project path, …). */
    fun fetchText(origin: String, path: String): String? {
        val base = origin.trim().trimEnd('/')
        val rel = path.trim().trimStart('/')
        return httpGet("$base/$rel")
    }

    /**
     * Join origin + relative path segments with percent-encoding.
     * Spaces become `%20` (not `+`). Catalog paths are plain UTF-8 filenames.
     */
    fun joinOriginPath(origin: String, vararg parts: String): String {
        val base = origin.trim().trimEnd('/')
        val segs = ArrayList<String>()
        for (part in parts) {
            for (raw in part.trim().trim('/').split('/')) {
                if (raw.isNotEmpty()) segs.add(encodePathSegment(raw))
            }
        }
        return if (segs.isEmpty()) base else "$base/${segs.joinToString("/")}"
    }

    /**
     * Encode one path segment for HTTPS media URLs.
     * Spaces → `%20`; leaves RFC3986 unreserved + common sub-delims (e.g. `()`) intact.
     */
    fun encodePathSegment(segment: String): String {
        val bytes = segment.toByteArray(StandardCharsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (isSafePathByte(c)) {
                out.append(ch)
            } else if (c == ' '.code) {
                out.append("%20")
            } else {
                out.append('%')
                out.append("0123456789ABCDEF"[c ushr 4])
                out.append("0123456789ABCDEF"[c and 0x0f])
            }
        }
        return out.toString()
    }

    /** ASCII unreserved + path-friendly sub-delims / colon / at (not slash). */
    private fun isSafePathByte(c: Int): Boolean {
        if (c !in 0x21..0x7e) return false
        val ch = c.toChar()
        return ch.isLetterOrDigit() ||
            ch == '-' || ch == '.' || ch == '_' || ch == '~' ||
            ch == '!' || ch == '$' || ch == '&' || ch == '\'' ||
            ch == '(' || ch == ')' || ch == '*' || ch == '+' ||
            ch == ',' || ch == ';' || ch == '=' || ch == ':' || ch == '@'
    }

    fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
