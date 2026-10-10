package com.relay.client

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Local Kubo control plane: gateway URLs, pin/fetch, readiness.
 * Daemon lifecycle lives in [IpfsNodeService].
 */
object IpfsSession {
    private const val TAG = "IpfsSession"
    const val API_PORT = 15001
    const val GATEWAY_PORT = 18080
    const val ABI_MODE_KUBO = "kubo"
    const val ABI_MODE_UNAVAILABLE = "unavailable"

    private val ready = AtomicBoolean(false)
    private val lastError = AtomicReference("")
    private val peerCount = AtomicReference(0)
    private val nodeId = AtomicReference("")
    private val abiMode = AtomicReference(ABI_MODE_UNAVAILABLE)

    fun isReady(): Boolean = ready.get()

    fun lastError(): String = lastError.get().orEmpty()

    fun peerCount(): Int = peerCount.get()

    fun nodeId(): String = nodeId.get().orEmpty()

    fun abiMode(): String = abiMode.get()

    fun abiLabel(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return "$abi / ${abiMode()}"
    }

    fun gatewayBaseUrl(): String = "http://127.0.0.1:$GATEWAY_PORT"

    fun gatewayUrl(cid: String): String {
        val c = cid.trim().trimStart('/')
        return "${gatewayBaseUrl()}/ipfs/$c"
    }

    fun apiBaseUrl(): String = "http://127.0.0.1:$API_PORT"

    fun markStarting() {
        ready.set(false)
        lastError.set("")
    }

    fun markReady(id: String) {
        nodeId.set(id)
        abiMode.set(ABI_MODE_KUBO)
        ready.set(true)
        lastError.set("")
        refreshPeersAsync()
    }

    fun markFailed(msg: String) {
        ready.set(false)
        abiMode.set(ABI_MODE_UNAVAILABLE)
        lastError.set(msg)
    }

    fun markStopped() {
        ready.set(false)
        nodeId.set("")
        peerCount.set(0)
    }

    /** Kick Bitswap / block fetch so ExoPlayer Ranges hit warm-ish blocks. */
    fun ensureFetching(cid: String) {
        val c = cid.trim()
        if (c.isEmpty() || !isReady()) return
        Thread({
            try {
                // Recursive refs walks the DAG and pulls blocks without waiting for full materialization.
                apiPost("refs?arg=${enc(c)}&recursive=true&unique=true", readBody = false, timeoutMs = 120_000)
            } catch (e: Exception) {
                Log.w(TAG, "ensureFetching $c: ${e.message}")
            }
        }, "ipfs-fetch-$c").start()
    }

    fun pinAdd(cid: String): Boolean {
        val c = cid.trim()
        if (c.isEmpty() || !isReady()) return false
        return try {
            apiPost("pin/add?arg=${enc(c)}&recursive=true", readBody = true, timeoutMs = 600_000)
            true
        } catch (e: Exception) {
            Log.w(TAG, "pinAdd: ${e.message}")
            false
        }
    }

    fun pinRm(cid: String): Boolean {
        val c = cid.trim()
        if (c.isEmpty() || !isReady()) return false
        return try {
            apiPost("pin/rm?arg=${enc(c)}&recursive=true", readBody = true, timeoutMs = 120_000)
            true
        } catch (e: Exception) {
            Log.w(TAG, "pinRm: ${e.message}")
            false
        }
    }

    fun repoGc(): Boolean {
        if (!isReady()) return false
        return try {
            apiPost("repo/gc", readBody = false, timeoutMs = 300_000)
            true
        } catch (e: Exception) {
            Log.w(TAG, "repoGc: ${e.message}")
            false
        }
    }

    /** Best-effort size of the on-disk IPFS repo (bytes). */
    fun repoBytes(context: Context): Long {
        return try {
            dirSize(repoPath(context))
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * If the repo exceeds [highWaterBytes], run GC to drop unpinned ephemeral blocks.
     * Default high water: 6 GiB on 32-bit/TV-ish, 16 GiB otherwise.
     */
    fun maybeGcIfOverBudget(context: Context, highWaterBytes: Long = -1L): Boolean {
        val cap =
            if (highWaterBytes > 0) {
                highWaterBytes
            } else {
                val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                val tv =
                    try {
                        context.packageManager.hasSystemFeature("android.software.leanback")
                    } catch (_: Exception) {
                        false
                    }
                if (abi == "armeabi-v7a" || abi == "armeabi" || tv) {
                    6L * 1024 * 1024 * 1024
                } else {
                    16L * 1024 * 1024 * 1024
                }
            }
        val used = repoBytes(context)
        if (used < cap) return false
        Log.i(TAG, "repo over budget used=$used cap=$cap — GC")
        return repoGc()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isFile) {
                total += f.length()
            } else {
                val kids = f.listFiles() ?: continue
                for (k in kids) stack.add(k)
            }
        }
        return total
    }

    fun refreshPeersAsync() {
        if (!isReady()) return
        Thread({
            try {
                val body = apiPost("swarm/peers", readBody = true, timeoutMs = 15_000) ?: return@Thread
                // Response is newline-delimited multiaddrs or JSON depending on encoding; count lines/peers loosely.
                val n =
                    when {
                        body.trimStart().startsWith("{") -> {
                            val o = JSONObject(body)
                            o.optJSONArray("Peers")?.length()
                                ?: o.optJSONArray("peers")?.length()
                                ?: 0
                        }
                        else -> body.lineSequence().count { it.isNotBlank() }
                    }
                peerCount.set(n)
            } catch (_: Exception) {
            }
        }, "ipfs-peers").start()
    }

    /** HEAD Range probe against local gateway (spike / Lab). */
    fun gatewayRangeOk(cid: String, endExclusive: Long = 1_048_576L): Boolean {
        if (!isReady()) return false
        return try {
            val url = URL(gatewayUrl(cid))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Range", "bytes=0-${endExclusive - 1}")
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 206 || code == 200
        } catch (e: Exception) {
            Log.w(TAG, "gatewayRangeOk: ${e.message}")
            false
        }
    }

    fun awaitReady(timeoutMs: Long): Boolean {
        if (isReady()) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) return true
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return isReady()
            }
        }
        return isReady()
    }

    fun startService(context: Context) {
        IpfsNodeService.start(context.applicationContext)
    }

    fun ensureStartedAndReady(context: Context, timeoutMs: Long = 90_000L): Boolean {
        if (isReady()) return true
        startService(context)
        return awaitReady(timeoutMs)
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    private fun apiPost(pathAndQuery: String, readBody: Boolean, timeoutMs: Int): String? {
        val url = URL("${apiBaseUrl()}/api/v0/$pathAndQuery")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = timeoutMs
        conn.doInput = true
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body =
            if (readBody && stream != null) {
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            } else {
                stream?.close()
                null
            }
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("ipfs API $pathAndQuery → HTTP $code ${body?.take(200)}")
        }
        return body
    }

    fun repoPath(context: Context): File =
        File(context.applicationContext.filesDir, "ipfs_repo")

    /**
     * Locate Kubo daemon. Prefer [ApplicationInfo.nativeLibraryDir] when the .so was extracted
     * as a real file; otherwise copy from the APK zip (`lib/<abi>/libkubo_daemon.so`) into
     * filesDir — required when `extractNativeLibs=false` (common on modern AGP).
     */
    fun daemonBinary(context: Context): File? {
        val app = context.applicationContext
        val native = app.applicationInfo.nativeLibraryDir?.let { File(it, "libkubo_daemon.so") }
        if (native != null && native.isFile) {
            if (!native.canExecute()) native.setExecutable(true)
            if (native.canExecute()) return native
        }
        return extractDaemonFromApk(app)
    }

    private fun extractDaemonFromApk(context: Context): File? {
        val abis = Build.SUPPORTED_ABIS
        val outDir = File(context.filesDir, "ipfs_bin")
        outDir.mkdirs()
        val out = File(outDir, "kubo")
        val marker = File(outDir, "kubo.ver")
        val ver = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()
        } catch (_: Exception) {
            "0"
        }
        if (out.isFile && out.canExecute() && marker.isFile && marker.readText() == ver) {
            return out
        }
        val apk = context.applicationInfo.sourceDir ?: return null
        try {
            java.util.zip.ZipFile(apk).use { zip ->
                for (abi in abis) {
                    val entry = zip.getEntry("lib/$abi/libkubo_daemon.so") ?: continue
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out.setExecutable(true, false)
                    marker.writeText(ver)
                    Log.i(TAG, "Extracted Kubo daemon for $abi → ${out.absolutePath} (${out.length()} bytes)")
                    return out
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractDaemonFromApk failed", e)
        }
        return null
    }

    /** Latch used by [IpfsNodeService] tests / Lab waiters. */
    fun newReadyLatch(): CountDownLatch = CountDownLatch(1)

    fun awaitLatch(latch: CountDownLatch, timeoutMs: Long): Boolean =
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
}
