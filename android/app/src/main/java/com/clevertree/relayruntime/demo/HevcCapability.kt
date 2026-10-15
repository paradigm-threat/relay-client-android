package com.clevertree.relayruntime.demo

import android.media.MediaCodecList
import android.os.Build

/**
 * Reports whether the device advertises a hardware HEVC (H.265) video decoder.
 * Soft decode is not shipped — Flashback relies on this for in-app play.
 */
object HevcCapability {
    data class Report(
        val hevcHardwareDecoder: Boolean,
        val decoderNames: List<String>,
        val apiLevel: Int = Build.VERSION.SDK_INT,
        /** Best-effort max decode height (px). 0 = no usable HEVC hardware. */
        val maxHeight: Int = 0,
    ) {
        fun summaryLine(): String = when {
            hevcHardwareDecoder ->
                "HEVC hardware decode: yes (${decoderNames.take(2).joinToString()})"
            else ->
                "HEVC hardware decode: no — use Open in external player (VLC)"
        }

        /** True when hardware HEVC can cover [height] (unknown height 0 → yes if any HEVC). */
        fun canDecodeHeight(height: Int): Boolean {
            if (maxHeight <= 0) return false
            if (height <= 0) return true
            return maxHeight >= height
        }
    }

    fun probe(): Report {
        val names = mutableListOf<String>()
        var maxH = 0
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            if (!info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }) continue
            if (!looksHardware(info.name)) continue
            names.add(info.name)
            try {
                val caps = info.getCapabilitiesForType("video/hevc")
                val vh = caps?.videoCapabilities?.supportedHeights
                if (vh != null) {
                    maxH = maxOf(maxH, vh.upper)
                }
            } catch (_: Exception) {
            }
        }
        if (names.isNotEmpty() && maxH <= 0) {
            // Capability query unavailable — assume 4K-class hardware decode.
            maxH = 2160
        }
        return Report(
            hevcHardwareDecoder = names.isNotEmpty(),
            decoderNames = names,
            maxHeight = maxH,
        )
    }

    /** Pure helper for unit tests — name heuristics only. */
    fun looksHardware(codecName: String): Boolean {
        val n = codecName.lowercase()
        if (n.contains("android.media.sw") || n.contains("google") && n.contains("sw")) return false
        if (n.startsWith("omx.google.") || n.startsWith("c2.android.")) {
            // Google software codecs
            if (n.contains("hevc") || n.contains("h265")) return false
        }
        if (n.contains(".sw.") || n.contains("soft")) return false
        return n.contains("hevc") || n.contains("h265")
    }

    fun filterHardwareHevcNames(allNames: List<String>): List<String> =
        allNames.filter { looksHardware(it) }
}
