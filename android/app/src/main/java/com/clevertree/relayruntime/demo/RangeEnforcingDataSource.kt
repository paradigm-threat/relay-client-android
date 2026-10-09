package com.clevertree.relayruntime.demo

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * HTTP [DataSource] that only uses **bounded** Range requests.
 *
 * Relay/IPFS edges serving Flashback masters often hang on full GET and on open-ended
 * `Range: bytes=0-` (0 bytes until timeout), but answer quickly for closed ranges like
 * `bytes=0-1048575`. Progressive ExoPlayer opens at position 0 with [C.LENGTH_UNSET];
 * this wrapper fetches fixed-size windows and advances transparently so extractors see
 * one continuous stream.
 */
class RangeEnforcingDataSource(
    private val upstreamFactory: DefaultHttpDataSource.Factory,
) : DataSource {

    companion object {
        private const val TAG = "RangeHttpDataSource"
        /**
         * Bounded Range window size.
         * 256 KiB caused stall-rebuffers: each open costs ~2–4s gateway/TLS latency, so
         * effective throughput collapsed (~0.5–1 Mb/s) while 4K HEVC needs several Mb/s.
         * Phone curl shows 4 MiB ranges complete in ~2.6s (~13 Mb/s) — use that size.
         */
        private const val CHUNK_BYTES = 4L * 1024L * 1024L
    }

    private var listener: TransferListener? = null
    private var upstream: DefaultHttpDataSource? = null
    private var baseSpec: DataSpec? = null
    private var absolutePosition: Long = 0L
    private var contentLength: Long = C.LENGTH_UNSET.toLong()
    private var bytesRemainingInChunk: Long = 0L
    private var opened: Boolean = false

    fun setTransferListener(transferListener: TransferListener?) {
        listener = transferListener
    }

    override fun addTransferListener(transferListener: TransferListener) {
        listener = transferListener
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeQuietly()
        baseSpec = dataSpec
        absolutePosition = dataSpec.position
        contentLength = C.LENGTH_UNSET.toLong()
        opened = true
        openChunk()
        return if (contentLength != C.LENGTH_UNSET.toLong()) {
            (contentLength - dataSpec.position).coerceAtLeast(0L)
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) return C.RESULT_END_OF_INPUT
        if (length == 0) return 0
        if (contentLength != C.LENGTH_UNSET.toLong() && absolutePosition >= contentLength) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemainingInChunk <= 0L) {
            if (!advanceChunk()) return C.RESULT_END_OF_INPUT
        }
        val toRead = minOf(length.toLong(), bytesRemainingInChunk).toInt()
        val src = upstream ?: return C.RESULT_END_OF_INPUT
        val read = src.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            // Window finished early or exactly — try the next bounded range.
            bytesRemainingInChunk = 0L
            if (!advanceChunk()) return C.RESULT_END_OF_INPUT
            return read(buffer, offset, length)
        }
        absolutePosition += read
        bytesRemainingInChunk -= read
        return read
    }

    override fun getUri(): Uri? = upstream?.uri ?: baseSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        upstream?.responseHeaders ?: emptyMap()

    override fun close() {
        closeQuietly()
        opened = false
        baseSpec = null
    }

    private fun advanceChunk(): Boolean {
        if (contentLength != C.LENGTH_UNSET.toLong() && absolutePosition >= contentLength) {
            bytesRemainingInChunk = 0L
            return false
        }
        openChunk()
        return bytesRemainingInChunk > 0L
    }

    private fun openChunk() {
        val spec = baseSpec ?: return
        val scheme = spec.uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            val http = newUpstream()
            upstream = http
            val openedLen = http.open(spec)
            bytesRemainingInChunk =
                if (openedLen == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE / 4 else openedLen
            if (openedLen != C.LENGTH_UNSET.toLong()) {
                contentLength = spec.position + openedLen
            }
            return
        }

        if (contentLength != C.LENGTH_UNSET.toLong() && absolutePosition >= contentLength) {
            bytesRemainingInChunk = 0L
            return
        }

        closeUpstreamOnly()
        val endExclusive = if (contentLength != C.LENGTH_UNSET.toLong()) {
            minOf(absolutePosition + CHUNK_BYTES, contentLength)
        } else {
            absolutePosition + CHUNK_BYTES
        }
        if (endExclusive <= absolutePosition) {
            bytesRemainingInChunk = 0L
            return
        }
        val length = endExclusive - absolutePosition
        // Closed DataSpec length ⇒ DefaultHttpDataSource sends Range: bytes=pos-(pos+len-1).
        // Open-ended / full GET hang on this gateway (proven on-device).
        val chunkSpec = spec.buildUpon()
            .setPosition(absolutePosition)
            .setLength(length)
            .build()

        val http = newUpstream()
        upstream = http
        val t0 = android.os.SystemClock.elapsedRealtime()
        val openedLen = try {
            http.open(chunkSpec)
        } catch (e: Exception) {
            Log.e(TAG, "chunk open failed pos=$absolutePosition len=$length", e)
            throw e
        }
        val openMs = android.os.SystemClock.elapsedRealtime() - t0

        parseContentRangeTotal(http.responseHeaders)?.let { total ->
            contentLength = total
        }

        bytesRemainingInChunk = when {
            openedLen == C.LENGTH_UNSET.toLong() -> length
            openedLen <= 0L -> 0L
            else -> openedLen
        }

        // Short window ⇒ EOF of resource
        if (openedLen >= 0 && openedLen < length) {
            contentLength = absolutePosition + openedLen
        }
        val kbps = if (openMs > 0 && openedLen > 0) (openedLen * 8L) / openMs else 0L
        Log.i(
            TAG,
            "opened bytes=$absolutePosition-${absolutePosition + bytesRemainingInChunk - 1} " +
                "got=$openedLen total=$contentLength openMs=$openMs ~${kbps}kbps",
        )
    }

    private fun newUpstream(): DefaultHttpDataSource {
        val http = upstreamFactory.createDataSource() as DefaultHttpDataSource
        listener?.let { http.addTransferListener(it) }
        return http
    }

    private fun closeUpstreamOnly() {
        try {
            upstream?.close()
        } catch (_: Exception) {
        }
        upstream = null
        bytesRemainingInChunk = 0L
    }

    private fun closeQuietly() {
        closeUpstreamOnly()
    }

    private fun parseContentRangeTotal(headers: Map<String, List<String>>): Long? {
        val values = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value ?: return null
        val raw = values.firstOrNull() ?: return null
        val slash = raw.lastIndexOf('/')
        if (slash < 0 || slash == raw.lastIndex) return null
        val total = raw.substring(slash + 1).trim()
        if (total == "*") return null
        return total.toLongOrNull()
    }

    class Factory(
        userAgent: String = "FlashbackRepository/1.0 (Android ExoPlayer)",
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 60_000,
    ) : DataSource.Factory {
        private val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)

        private var listener: TransferListener? = null

        fun setTransferListener(transferListener: TransferListener?): Factory {
            listener = transferListener
            return this
        }

        override fun createDataSource(): DataSource {
            return RangeEnforcingDataSource(upstreamFactory).also {
                it.setTransferListener(listener)
            }
        }
    }
}
