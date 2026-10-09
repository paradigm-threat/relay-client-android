package com.clevertree.relayruntime.demo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Opens HTTPS (or other) media URLs in an external player via ACTION_VIEW.
 * Also parses Android intent: URLs used by ExternalPlay.android.js.
 */
object ExternalMediaIntents {
    private const val TAG = "ExternalMediaIntents"

    /**
     * @return true if an activity was started (chooser or direct).
     */
    fun open(context: Context, href: String, mimeType: String = "video/*"): Boolean {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return false
        val intent = parseIntentHref(trimmed, mimeType) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no handler for $trimmed", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "open failed for $trimmed", e)
            false
        }
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
                // Fall back: strip to embedded https URL if present
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

    private fun looksLikeVideo(url: String): Boolean {
        val s = url.lowercase()
        return s.contains(".mkv") || s.contains(".mp4") || s.contains(".webm") ||
            s.contains(".m4v") || s.contains(".mov") || s.contains("video/")
    }
}
