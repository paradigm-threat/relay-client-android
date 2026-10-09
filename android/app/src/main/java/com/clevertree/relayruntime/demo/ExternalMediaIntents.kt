package com.clevertree.relayruntime.demo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Opens HTTPS (or other) media URLs in an external player via ACTION_VIEW.
 * Also parses Android intent: URLs used by ExternalPlay.android.js.
 *
 * Prefers VLC when installed (reliable for progressive HTTPS MKV). Falls back to a chooser.
 */
object ExternalMediaIntents {
    private const val TAG = "ExternalMediaIntents"
    private const val VLC_PACKAGE = "org.videolan.vlc"
    private const val VLC_PLAY_STORE =
        "https://play.google.com/store/apps/details?id=org.videolan.vlc"

    /**
     * @return true if an activity was started (chooser, VLC, or browser for non-video links).
     */
    fun open(context: Context, href: String, mimeType: String = "video/*"): Boolean {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return false
        val intent = parseIntentHref(trimmed, mimeType) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addCategory(Intent.CATEGORY_DEFAULT)

        // Prefer VLC for video streams — avoids OEM resolvers that claim video/* but can't stream HTTPS MKV.
        if (looksLikeVideo(trimmed) && isPackageInstalled(context, VLC_PACKAGE)) {
            val vlc = Intent(intent).setPackage(VLC_PACKAGE)
            if (startSafely(context, vlc)) return true
        }

        // Chooser with an explicit fallback list helps when package visibility hides handlers.
        if (startChooser(context, intent)) return true

        // Last resort: data-only VIEW (some players only match scheme, not mime).
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val loose = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_DEFAULT)
            if (startChooser(context, loose)) return true
            if (startSafely(context, loose)) return true
        }

        Log.w(TAG, "no handler for $trimmed")
        return false
    }

    /** Open the Play Store listing for VLC (browser / Play Store app). */
    fun openVlcPlayStore(context: Context): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$VLC_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startSafely(context, market)) return true
        return open(context, VLC_PLAY_STORE, "*/*")
    }

    fun parseIntentHref(href: String, defaultMime: String = "video/*"): Intent? {
        val trimmed = href.trim()
        if (trimmed.startsWith("intent:", ignoreCase = true)) {
            return try {
                val parsed = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
                if (parsed.type.isNullOrBlank()) {
                    parsed.type = defaultMime
                }
                if (parsed.action.isNullOrBlank()) {
                    parsed.action = Intent.ACTION_VIEW
                }
                parsed
            } catch (e: Exception) {
                Log.w(TAG, "bad intent: URL", e)
                val https = Regex("""https?://[^\s#]+""").find(trimmed)?.value ?: return null
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(https), defaultMime)
            }
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val mime = if (looksLikeVideo(trimmed)) defaultMime else "*/*"
            return Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(trimmed), mime)
        }
        if (trimmed.startsWith("vlc://", ignoreCase = true)) {
            return Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
        }
        return null
    }

    private fun startChooser(context: Context, intent: Intent): Boolean {
        return try {
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "chooser: no handler", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "chooser failed", e)
            false
        }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "startActivity: no handler for ${intent.data}", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed for ${intent.data}", e)
            false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLikeVideo(url: String): Boolean {
        val s = url.lowercase()
        return s.contains(".mkv") || s.contains(".mp4") || s.contains(".webm") ||
            s.contains(".m4v") || s.contains(".mov") || s.contains("video/")
    }
}
