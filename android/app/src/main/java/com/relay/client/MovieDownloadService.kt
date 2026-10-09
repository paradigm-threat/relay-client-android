package com.relay.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Saves a full movie into app-specific storage. No broad storage permission.
 */
class MovieDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val channelId = "relay-movie-download"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Movie downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val note = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("Saving movie").setSmallIcon(android.R.drawable.stat_sys_download).build()
        startForeground(71, note)
        Thread {
            try {
                val dest = fileFor(this, url)
                val tmp = File(dest.path + ".part")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                File(dest.path + ".done").writeText("1")
            } catch (_: Exception) {
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_URL = "url"

        fun probeLength(url: String): Long? {
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                val len = conn.contentLengthLong
                conn.disconnect()
                if (len > 0) len else null
            } catch (_: Exception) {
                null
            }
        }

        fun freeBytes(context: Context): Long {
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir
            return try {
                StatFs(dir.absolutePath).availableBytes
            } catch (_: Exception) {
                0L
            }
        }

        fun savedMovies(context: Context): List<File> {
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: return emptyList()
            return dir.listFiles()?.filter { it.isFile && it.name.startsWith("relay-") && !it.name.endsWith(".part") && !it.name.endsWith(".done") }
                ?: emptyList()
        }

        fun fileFor(context: Context, url: String): File {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }.take(20)
            val ext = when {
                url.contains(".mp4", ignoreCase = true) -> ".mp4"
                url.contains(".webm", ignoreCase = true) -> ".webm"
                else -> ".mkv"
            }
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
            return File(dir, "relay-$hex$ext")
        }
    }
}
