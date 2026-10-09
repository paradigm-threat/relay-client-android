package com.relay.client

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Public board bug posts — same contract as web [Board.host.js]:
 * `PUT` markdown on git branch `comments` under `.relay/comments/by-path/…`.
 *
 * No dedicated `/api/bugs` route; the publish node accepts branch-scoped PUT.
 */
object FlashbackBugReport {
    const val BRANCH = "comments"
    const val PATH_ROOT = ".relay/comments/by-path/"
    const val MAX_BODY = 30_000
    const val KIND_BUG = "bug"

    private const val PREFS = "flashback-bug-report"
    private const val KEY_FOOTER_DISMISS_UNTIL = "footerDismissUntilMs"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    data class DebugSnapshot(
        val versionName: String,
        val versionCode: Int,
        val packageName: String,
        val deviceModel: String,
        val sdkInt: Int,
        val origin: String,
        val screen: String,
        val slug: String?,
        val lastPlaybackError: String?,
        val logcatSnippet: String? = null,
    )

    data class SubmitRequest(
        val title: String,
        val body: String,
        val name: String = "",
        val pagePath: String = "/android",
        val snapshot: DebugSnapshot,
    )

    sealed class SubmitResult {
        data class Ok(val filePath: String) : SubmitResult()
        data class HttpError(val status: Int, val detail: String) : SubmitResult()
        data class NetworkError(val message: String) : SubmitResult()
        data class ValidationError(val message: String) : SubmitResult()
    }

    fun shouldShowFooter(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_FOOTER_DISMISS_UNTIL, 0L)
        return nowMs >= until
    }

    /** Hide the floating footer for ~24h (or until prefs cleared). */
    fun markFooterDismissed(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_FOOTER_DISMISS_UNTIL, nowMs + DAY_MS)
            .apply()
    }

    /** Match Rust/JS percent_encoding::NON_ALPHANUMERIC (A-Za-z0-9 only unescaped). */
    fun encodeCommentTargetPath(repoRelativePath: String): String {
        val trimmed = repoRelativePath.trimStart('/')
        val out = StringBuilder(trimmed.length * 3)
        for (ch in trimmed) {
            if (ch.code < 128 && ch.isLetterOrDigit()) {
                out.append(ch)
            } else {
                val bytes = ch.toString().toByteArray(StandardCharsets.UTF_8)
                for (b in bytes) {
                    out.append('%')
                    out.append("%02X".format(b.toInt() and 0xff))
                }
            }
        }
        return out.toString()
    }

    /** JS encodeURIComponent (UTF-8; leaves A-Za-z0-9 -_.!~*'() unescaped). */
    fun encodeURIComponent(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val out = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '!' ||
                ch == '~' || ch == '*' || ch == '\'' || ch == '(' || ch == ')'
            ) {
                out.append(ch)
            } else {
                out.append('%')
                out.append("%02X".format(c))
            }
        }
        return out.toString()
    }

    /**
     * HTTP path for a repo-relative path. Axum Path decodes once; write handlers
     * decode again — so each segment is encodeURIComponent'd twice.
     */
    fun httpUrlForRepoPath(repoRelativePath: String): String {
        val rel = repoRelativePath.trimStart('/')
        if (rel.isEmpty()) return "/"
        return "/" + rel.split('/').joinToString("/") { part ->
            encodeURIComponent(encodeURIComponent(part))
        }
    }

    fun utcStamp(nowMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs
        return String.format(
            Locale.US,
            "%04d%02d%02dT%02d%02d%02dZ",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
        )
    }

    fun yamlQuoted(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }

    fun buildMarkdown(
        kind: String,
        pagePath: String,
        created: String,
        name: String,
        body: String,
    ): String {
        val lines = ArrayList<String>(12)
        lines.add("---")
        lines.add("kind: $kind")
        lines.add("path: ${yamlQuoted(pagePath)}")
        lines.add("created: $created")
        if (name.isNotBlank()) lines.add("name: ${yamlQuoted(name.trim())}")
        lines.add("---")
        lines.add("")
        lines.add(body)
        lines.add("")
        return lines.joinToString("\n")
    }

    fun formatUserBody(title: String, body: String): String {
        val t = title.trim()
        val b = body.trim()
        return when {
            t.isEmpty() -> b
            b.isEmpty() -> t
            else -> "$t\n\n$b"
        }
    }

    fun formatDebugSnapshot(snap: DebugSnapshot): String {
        val lines = ArrayList<String>(16)
        lines.add("### Android debug snapshot")
        lines.add("- app: ${snap.versionName} (${snap.versionCode})")
        lines.add("- package: ${snap.packageName}")
        lines.add("- device: ${snap.deviceModel} / SDK ${snap.sdkInt}")
        lines.add("- origin: ${snap.origin}")
        lines.add("- screen: ${snap.screen}")
        val slug = snap.slug?.trim().orEmpty()
        if (slug.isNotEmpty()) lines.add("- slug: $slug")
        val err = snap.lastPlaybackError?.trim().orEmpty()
        if (err.isNotEmpty()) lines.add("- lastPlaybackError: $err")
        val log = snap.logcatSnippet?.trim().orEmpty()
        if (log.isNotEmpty()) {
            lines.add("- logcat:")
            lines.add("```")
            lines.add(log.take(2000))
            lines.add("```")
        }
        return lines.joinToString("\n")
    }

    fun composePostBody(title: String, body: String, snapshot: DebugSnapshot): String {
        val user = formatUserBody(title, body)
        val debug = formatDebugSnapshot(snapshot)
        return if (user.isEmpty()) debug else "$user\n\n---\n\n$debug"
    }

    fun filePathFor(pagePath: String, kind: String, created: String): String {
        var path = pagePath.trim()
        if (path.isEmpty()) path = "/"
        if (!path.startsWith("/")) path = "/$path"
        val key = path.trimStart('/').ifEmpty { "home" }
        return PATH_ROOT + encodeCommentTargetPath(key) + "/" + created + "-" + kind + ".md"
    }

    fun putUrl(origin: String, repoRelativePath: String): String {
        val base = origin.trim().trimEnd('/')
        return base + httpUrlForRepoPath(repoRelativePath)
    }

    fun deviceModelLabel(): String {
        val manuf = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return when {
            manuf.isEmpty() -> model.ifEmpty { "unknown" }
            model.startsWith(manuf, ignoreCase = true) -> model
            else -> "$manuf $model".trim()
        }
    }

    /** Best-effort recent log lines from this process; often empty without READ_LOGS. */
    fun readLogcatSnippet(maxLines: Int = 40): String? {
        return try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", maxLines.toString(), "*:W"),
            )
            val text = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            proc.waitFor()
            val trimmed = text.trim()
            if (trimmed.isEmpty()) null else trimmed.take(2000)
        } catch (_: Exception) {
            null
        }
    }

    fun submit(origin: String, request: SubmitRequest): SubmitResult {
        val title = request.title.trim()
        val body = request.body.trim()
        if (title.isEmpty() && body.isEmpty()) {
            return SubmitResult.ValidationError("Please write a short title or message.")
        }
        val composed = composePostBody(title, body, request.snapshot)
        if (composed.length > MAX_BODY) {
            return SubmitResult.ValidationError(
                "Message is too long (keep under $MAX_BODY characters).",
            )
        }
        var pagePath = request.pagePath.trim().ifEmpty { "/android" }
        if (!pagePath.startsWith("/")) pagePath = "/$pagePath"
        val created = utcStamp()
        val filePath = filePathFor(pagePath, KIND_BUG, created)
        val markdown = buildMarkdown(
            kind = KIND_BUG,
            pagePath = pagePath,
            created = created,
            name = request.name,
            body = composed,
        )
        val url = putUrl(origin, filePath)
        return try {
            putMarkdown(url, markdown)
                .fold(
                    onSuccess = { SubmitResult.Ok(filePath) },
                    onFailure = { err ->
                        val http = err as? HttpStatusException
                        if (http != null) {
                            SubmitResult.HttpError(http.status, http.detail)
                        } else {
                            SubmitResult.NetworkError(err.message ?: "network error")
                        }
                    },
                )
        } catch (e: Exception) {
            SubmitResult.NetworkError(e.message ?: "network error")
        }
    }

    private class HttpStatusException(val status: Int, val detail: String) :
        Exception(detail.ifBlank { "PUT HTTP $status" })

    private fun putMarkdown(url: String, markdown: String): Result<Unit> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Content-Type", "text/markdown; charset=utf-8")
        conn.setRequestProperty("X-Relay-Branch", BRANCH)
        return try {
            val bytes = markdown.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val detail = try {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(
                    HttpStatusException(
                        code,
                        detail.trim().ifEmpty { "PUT HTTP $code" },
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }
}
