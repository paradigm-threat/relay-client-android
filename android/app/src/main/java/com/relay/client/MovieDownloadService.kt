package com.relay.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saves a full movie into app-specific storage. No broad storage permission.
 */
class MovieDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                cancelAllRequested.set(true)
                for ((_, conn) in activeConnections) {
                    try {
                        conn.disconnect()
                    } catch (_: Exception) {
                    }
                }
                activeConnections.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                IpfsNetworkState.reconcile(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        cancelAllRequested.set(false)
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Saving movie…"))
        IpfsNetworkState.beginDownload()
        IpfsNetworkState.reconcile(this)

        Thread {
            try {
                val dest = fileFor(this, url)
                val tmp = File(dest.path + ".part")
                val conn = URL(url).openConnection() as HttpURLConnection
                activeConnections[url] = conn
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                if (cancelAllRequested.get()) {
                    conn.disconnect()
                    return@Thread
                }
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelAllRequested.get()) break
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                        }
                    }
                }
                conn.disconnect()
                activeConnections.remove(url)
                if (cancelAllRequested.get()) {
                    tmp.delete()
                } else {
                    if (dest.exists()) dest.delete()
                    tmp.renameTo(dest)
                    File(dest.path + ".done").writeText("1")
                }
            } catch (_: Exception) {
            } finally {
                activeConnections.remove(url)
                IpfsNetworkState.endDownload()
                IpfsNetworkState.reconcile(this)
                if (IpfsNetworkState.activeDownloadCount() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val mgr = getSystemService(NotificationManager::class.java)
                    mgr.notify(NOTIF_ID, buildNotification("Saving movies…"))
                }
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Movie downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(text: String): Notification {
        val open =
            Intent(this, MainActivity::class.java).apply {
                action = IpfsNodeService.ACTION_OPEN_SEEDING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IpfsNodeService.EXTRA_OPEN_SEEDING, true)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 71, open, flags)
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        return builder
            .setContentTitle("Flashback download")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val ACTION_CANCEL_ALL = "com.relay.client.download.CANCEL_ALL"
        private const val CHANNEL_ID = "relay-movie-download"
        private const val NOTIF_ID = 71

        private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()
        private val cancelAllRequested = AtomicBoolean(false)

        fun cancelAll(context: Context) {
            cancelAllRequested.set(true)
            val i =
                Intent(context, MovieDownloadService::class.java)
                    .setAction(ACTION_CANCEL_ALL)
            context.startService(i)
        }

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
            return dir.listFiles()?.filter {
                it.isFile && it.name.startsWith("relay-") && !it.name.endsWith(".part") && !it.name.endsWith(".done")
            } ?: emptyList()
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
