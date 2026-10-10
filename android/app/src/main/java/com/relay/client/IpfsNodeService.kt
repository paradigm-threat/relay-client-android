package com.relay.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
            else -> {
                ensureForeground()
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

    private fun ensureForeground() {
        val channelId = "flashback-ipfs"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Flashback IPFS", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val note =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }.setContentTitle("Flashback IPFS")
                .setContentText("Seeding / fetching on the network")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
        startForeground(72, note)
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
                // Ensure ports stay on our localhost bindings after upgrades.
                configureRepo(bin, env)
            }

            stopDaemonQuiet()
            // Periodic GC reclaims unpinned ephemeral play blocks (TV storage).
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
                // Process exited — clear ready so the next startService reboots.
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
                // Watchdog: if the process dies while the service is alive, reboot once.
                Thread({
                    try {
                        proc.waitFor()
                        if (daemon === proc) {
                            Log.w(TAG, "watchdog: daemon died — restarting")
                            IpfsSession.markStopped()
                            daemon = null
                            Thread.sleep(1_500)
                            if (!starting.get()) boot()
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
        }
    }

    private fun configureRepo(bin: File, env: Map<String, String>) {
        // Bind API + gateway to localhost only; tighten connmgr for TV/32-bit.
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
        // Prefer HTTP routing when DHT is slow on TV Wi‑Fi (still Bitswap for bytes).
        runIpfs(
            bin,
            env,
            listOf("config", "--json", "Routing.AcceleratedDHTClient", "true"),
            timeoutMs = 30_000,
        )
        // Soft cap: ephemeral play must not fill the TV. Kept pins still count toward this;
        // GC only drops unpinned blocks.
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
                    // Some encodings nest differently
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
        const val ACTION_STOP = "com.relay.client.ipfs.STOP"

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
    }
}
