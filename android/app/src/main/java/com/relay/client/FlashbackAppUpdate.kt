package com.relay.client

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote version metadata for Flashback APK self-update.
 * Served at `{origin}/downloads/flashback.json` (repo-hosted, not IPFS).
 *
 * [apkUrl] is the universal / default package. Optional [apkUrls] maps CPU ABI
 * (`armeabi-v7a`, `arm64-v8a`, …) to smaller split APKs for Fire TV / phones.
 */
data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkUrls: Map<String, String> = emptyMap(),
)

/**
 * Parse / compare helpers (unit-tested) plus fetch, dismiss prefs, and install Intent.
 */
object FlashbackAppUpdate {
    const val MANIFEST_PATH = "downloads/flashback.json"
    const val DEFAULT_APK_PATH = "downloads/flashback.apk"
    private const val PREFS = "flashback-app-update"
    private const val KEY_DISMISS_UNTIL = "dismissUntilMs"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun manifestUrl(origin: String): String {
        val base = origin.trim().trimEnd('/')
        return "$base/$MANIFEST_PATH"
    }

    /**
     * Parse site JSON. Requires positive [versionCode]; [apkUrl] may be absolute or site-relative.
     * Optional [apkUrls] object maps ABI → URL/path.
     * Uses Gson so JVM unit tests do not depend on Android [org.json.JSONObject] stubs.
     */
    fun parseManifest(json: String): RemoteAppVersion? {
        return try {
            val o = JsonParser.parseString(json.trim()).asJsonObject
            if (!o.has("versionCode") || o.get("versionCode").isJsonNull) return null
            val code = o.get("versionCode").asInt
            if (code < 1) return null
            val name = o.get("versionName")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                .ifBlank { code.toString() }
            val apk = o.get("apkUrl")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                .ifBlank { "/$DEFAULT_APK_PATH" }
            val apkUrls = linkedMapOf<String, String>()
            val urlsEl = o.get("apkUrls")
            if (urlsEl != null && urlsEl.isJsonObject) {
                for ((k, v) in urlsEl.asJsonObject.entrySet()) {
                    if (v == null || v.isJsonNull) continue
                    val s = v.asString.trim()
                    if (k.isNotBlank() && s.isNotEmpty()) apkUrls[k.trim()] = s
                }
            }
            RemoteAppVersion(
                versionCode = code,
                versionName = name,
                apkUrl = apk.trim(),
                apkUrls = apkUrls,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isOutdated(installedVersionCode: Int, remote: RemoteAppVersion): Boolean =
        remote.versionCode > installedVersionCode

    /**
     * Prefer a split APK matching [preferredAbis] (device [Build.SUPPORTED_ABIS]),
     * else fall back to [RemoteAppVersion.apkUrl] (universal).
     */
    fun resolveApkUrl(
        origin: String,
        remote: RemoteAppVersion,
        preferredAbis: List<String> = emptyList(),
    ): String {
        for (abi in preferredAbis) {
            val candidate = remote.apkUrls[abi]?.trim().orEmpty()
            if (candidate.isNotEmpty()) return absolutize(origin, candidate)
        }
        return absolutize(origin, remote.apkUrl)
    }

    /** Device ABI list for [resolveApkUrl]; injectable in tests via overload. */
    fun resolveApkUrlForDevice(origin: String, remote: RemoteAppVersion): String =
        resolveApkUrl(origin, remote, Build.SUPPORTED_ABIS.toList())

    private fun absolutize(origin: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val base = origin.trim().trimEnd('/')
        return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    fun fetch(origin: String): RemoteAppVersion? {
        val text = FlashbackCatalog.httpGet(manifestUrl(origin)) ?: return null
        return parseManifest(text)
    }

    fun shouldShowPrompt(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DISMISS_UNTIL, 0L)
        return nowMs >= until
    }

    /** Hide the library banner until the next calendar day (or [DAY_MS] later). */
    fun markDismissed(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISMISS_UNTIL, nowMs + DAY_MS)
            .apply()
    }

    enum class InstallResult {
        /** Package installer Intent started. */
        STARTED_INSTALLER,
        /** Opened “Install unknown apps” settings; user must return and retry. */
        NEEDS_UNKNOWN_SOURCES,
        /** Download or Intent failed — caller may open the HTTPS APK URL. */
        FAILED,
    }

    /**
     * Download [apkUrl] into app cache and launch the package installer.
     * On Android 8+, opens “Install unknown apps” for this package if needed.
     */
    fun installApk(activity: Activity, apkUrl: String): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                activity.startActivity(settings)
                InstallResult.NEEDS_UNKNOWN_SOURCES
            } catch (_: Exception) {
                InstallResult.FAILED
            }
        }

        return try {
            val file = downloadApkToCache(activity, apkUrl)
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            InstallResult.STARTED_INSTALLER
        } catch (_: Exception) {
            InstallResult.FAILED
        }
    }

    /** Open the HTTPS APK URL (browser / system download manager handoff). */
    fun openApkUrl(context: Context, apkUrl: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadApkToCache(context: Context, apkUrl: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "flashback-update.apk")
        if (out.exists()) out.delete()
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
        return out
    }
}
