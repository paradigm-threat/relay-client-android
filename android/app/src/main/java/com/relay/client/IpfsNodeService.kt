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
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service hosting embedded Kubo (`libkubo_daemon.so`).
 * Localhost API :[IpfsSession.API_PORT], gateway :[IpfsSession.GATEWAY_PORT].
 *
 * Tray notification is shown only while seeding kept titles or actively downloading.
 * Tap opens [MainActivity] Downloads & seeding panel.
 */
class IpfsNodeService : Service() {
    private var daemon: Process? = null
    private val starting = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDaemon()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                applyNotificationPolicy()
                return START_STICKY
            }
            else -> {
                if (!IpfsNetworkState.shouldKeepAlive(this) && !starting.get()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Must call startForeground promptly when started as FGS; demote if idle notify.
                ensureChannel()
                applyNotificationPolicy(forceForegroundBriefly = true)
                if (IpfsSession.isReady() && daemon?.isAlive == true) {
                    return START_STICKY
                }
                Thread({ boot() }, "ipfs-boot").start()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopDaemon()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Flashback IPFS", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun managePendingIntent(): PendingIntent {
        val open =
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SEEDING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_SEEDING, true)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 72, open, flags)
    }

    private fun buildNotification(): Notification {
        val text = IpfsNetworkState.notificationText(this)
        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        return builder
            .setContentTitle("Flashback IPFS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(managePendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Show FGS notification only when seeding or downloading; otherwise demote to silent
     * background service (daemon may still run for theater playback hold).
     */
    private fun applyNotificationPolicy(forceForegroundBriefly: Boolean = false) {
        ensureChannel()
        val notify = IpfsNetworkState.shouldNotify(this)
        if (notify || forceForegroundBriefly) {
            startForeground(NOTIF_ID, buildNotification())
            if (!notify) {
                // Boot path: satisfy FGS contract, then hide tray if nothing to seed/download.
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        if (!IpfsNetworkState.shouldKeepAlive(this) && !starting.get()) {
            stopDaemon()
            stopSelf()
        }
    }

    private fun boot() {
        if (!starting.compareAndSet(false, true)) return
        IpfsSession.markStarting()
        try {
            val bin = IpfsSession.daemonBinary(this)
            if (bin == null) {
                IpfsSession.markFailed("libkubo_daemon.so missing for this ABI")
                return
            }
            val repo = IpfsSession.repoPath(this)
            repo.mkdirs()
            val env = HashMap<String, String>(System.getenv())
            env["IPFS_PATH"] = repo.absolutePath

            if (!File(repo, "config").isFile) {
                runIpfs(bin, env, listOf("init", "--profile=lowpower"), timeoutMs = 120_000)
                configureRepo(bin, env)
            } else {
                configureRepo(bin, env)
            }

            stopDaemonQuiet()
            val pb =
                ProcessBuilder(bin.absolutePath, "daemon", "--migrate=true", "--enable-gc=true")
                    .directory(repo)
                    .redirectErrorStream(true)
            pb.environment().clear()
            pb.environment().putAll(env)
            val proc = pb.start()
            daemon = proc
            Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8)).use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            Log.i(TAG, line!!)
                        }
                    }
                } catch (_: Exception) {
                }
                if (daemon === proc) {
                    Log.w(TAG, "Kubo daemon exited")
                    IpfsSession.markStopped()
                    daemon = null
                }
            }, "ipfs-daemon-log").start()

            val id = waitForApi(90_000L)
            if (id != null) {
                IpfsSession.markReady(id)
                Log.i(TAG, "Kubo ready id=$id abi=${Build.SUPPORTED_ABIS.firstOrNull()}")
                applyNotificationPolicy()
                Thread({
                    try {
                        proc.waitFor()
                        if (daemon === proc) {
                            Log.w(TAG, "watchdog: daemon died — restarting")
                            IpfsSession.markStopped()
                            daemon = null
                            Thread.sleep(1_500)
                            if (IpfsNetworkState.shouldKeepAlive(this) && !starting.get()) boot()
                        }
                    } catch (_: Exception) {
                    }
                }, "ipfs-watchdog").start()
            } else {
                IpfsSession.markFailed("Kubo API did not become ready")
                stopDaemonQuiet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "boot failed", e)
            IpfsSession.markFailed(e.message ?: "IPFS boot failed")
            stopDaemonQuiet()
        } finally {
            starting.set(false)
            applyNotificationPolicy()
        }
    }

    private fun configureRepo(bin: File, env: Map<String, String>) {
        runIpfs(
            bin,
            env,
            listOf("config", "Addresses.API", "/ip4/127.0.0.1/tcp/${IpfsSession.API_PORT}"),
            timeoutMs = 30_000,
        )
        runIpfs(
            bin,
            env,
            listOf("config", "Addresses.Gateway", "/ip4/127.0.0.1/tcp/${IpfsSession.GATEWAY_PORT}"),
            timeoutMs = 30_000,
        )
        val lowWater = if (isLikely32BitOrTv()) 20 else 40
        val highWater = if (isLikely32BitOrTv()) 40 else 80
        runIpfs(
            bin,
            env,
            listOf(
                "config",
                "--json",
                "Swarm.ConnMgr",
                """{"Type":"basic","LowWater":$lowWater,"HighWater":$highWater,"GracePeriod":"20s"}""",
            ),
            timeoutMs = 30_000,
        )
        runIpfs(
            bin,
            env,
            listOf("config", "--json", "Routing.AcceleratedDHTClient", "true"),
            timeoutMs = 30_000,
        )
        val storageMax = if (isLikely32BitOrTv()) "8GB" else "20GB"
        runIpfs(
            bin,
            env,
            listOf("config", "Datastore.StorageMax", storageMax),
            timeoutMs = 30_000,
        )
        runIpfs(
            bin,
            env,
            listOf("config", "--json", "Datastore.StorageGCWatermark", "90"),
            timeoutMs = 30_000,
        )
    }

    private fun isLikely32BitOrTv(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        if (abi == "armeabi-v7a" || abi == "armeabi") return true
        return packageManager.hasSystemFeature("android.software.leanback")
    }

    private fun waitForApi(timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (daemon?.isAlive != true) return null
            try {
                val url = URL("${IpfsSession.apiBaseUrl()}/api/v0/id")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2_000
                conn.readTimeout = 5_000
                val code = conn.responseCode
                val body =
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText() }
                conn.disconnect()
                if (code in 200..299 && !body.isNullOrBlank()) {
                    val id = JSONObject(body).optString("ID").ifBlank {
                        JSONObject(body).optString("ID")
                    }
                    if (id.isNotBlank()) return id
                    return JSONObject(body).optString("ID", "ready")
                }
            } catch (_: Exception) {
            }
            try {
                Thread.sleep(400)
            } catch (_: InterruptedException) {
                return null
            }
        }
        return null
    }

    private fun runIpfs(
        bin: File,
        env: Map<String, String>,
        args: List<String>,
        timeoutMs: Long,
    ) {
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(env)
        val p = pb.start()
        val out = StringBuilder()
        Thread({
            try {
                BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8)).use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        out.append(line).append('\n')
                    }
                }
            } catch (_: Exception) {
            }
        }).start()
        val ok = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!ok) {
            p.destroyForcibly()
            throw IllegalStateException("ipfs ${args.firstOrNull()} timed out")
        }
        if (p.exitValue() != 0) {
            throw IllegalStateException("ipfs ${args.joinToString(" ")} → ${p.exitValue()}: ${out.take(400)}")
        }
    }

    private fun stopDaemon() {
        stopDaemonQuiet()
        IpfsSession.markStopped()
    }

    private fun stopDaemonQuiet() {
        try {
            daemon?.destroy()
            daemon?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            daemon?.destroyForcibly()
        } catch (_: Exception) {
        }
        daemon = null
    }

    companion object {
        private const val TAG = "IpfsNodeService"
        private const val CHANNEL_ID = "flashback-ipfs"
        private const val NOTIF_ID = 72
        const val ACTION_STOP = "com.relay.client.ipfs.STOP"
        const val ACTION_REFRESH_NOTIFICATION = "com.relay.client.ipfs.REFRESH_NOTIFICATION"
        const val ACTION_OPEN_SEEDING = "com.relay.client.ipfs.OPEN_SEEDING"
        const val EXTRA_OPEN_SEEDING = "open_seeding"

        fun start(context: Context) {
            val i = Intent(context, IpfsNodeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, IpfsNodeService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun refreshNotification(context: Context) {
            val i =
                Intent(context, IpfsNodeService::class.java)
                    .setAction(ACTION_REFRESH_NOTIFICATION)
            context.startService(i)
        }
    }
}
