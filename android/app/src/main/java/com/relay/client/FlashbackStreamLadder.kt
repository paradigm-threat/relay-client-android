package com.relay.client

/**
 * Catalog stream ladder: archival master → 1080p H.264 → 720p H.264.
 * Pure JVM helpers so unit tests need no Android runtime.
 *
 * [Option.cid] is the IPFS CID when known; [Option.url] is the Relay-origin URL (catalog/debug).
 * In-app play resolves via [resolveLocalPlayUrl] to the embedded Kubo gateway when ready.
 */
object FlashbackStreamLadder {
    enum class Kind {
        MASTER,
        WEB,
        MOBILE,
    }

    data class Option(
        val kind: Kind,
        val label: String,
        val url: String,
        val cid: String = "",
    )

    /**
     * Ordered options that exist for [project] at [origin].
     * Master uses IPFS CID when set; web/mobile use tip paths (or their CIDs when set).
     */
    fun options(project: FlashbackProject, origin: String): List<Option> {
        val out = ArrayList<Option>(3)
        project.masterStreamUrl(origin)?.let {
            val masterLabel =
                if (project.height >= 2000) "Full quality (4K HEVC)"
                else if (project.availableIn.isNotBlank()) project.availableIn
                else "Full quality (MKV)"
            out.add(Option(Kind.MASTER, masterLabel, it, project.ipfsCid.trim()))
        }
        project.webStreamUrl(origin)?.let {
            out.add(Option(Kind.WEB, "1080p (H.264)", it, project.ipfsCidWeb.trim()))
        }
        project.mobileStreamUrl(origin)?.let {
            out.add(Option(Kind.MOBILE, "720p (H.264)", it, project.ipfsCidMobile.trim()))
        }
        return out
    }

    /**
     * In-app play URL: localhost Kubo gateway when CID + node ready.
     * Returns null when a CID is required but IPFS is not ready (no Relay gateway fallback).
     * Tip-path-only options (empty CID) keep the catalog [Option.url].
     */
    fun resolveLocalPlayUrl(option: Option): String? {
        val cid = option.cid.trim()
        if (cid.isEmpty()) return option.url
        if (!IpfsSession.isReady()) return null
        return IpfsSession.gatewayUrl(cid)
    }

    /**
     * Default pick: master when HEVC hardware can cover catalog height; else first lower rung.
     * [maxHevcHeight] 0 means "no usable HEVC hardware".
     * Unknown catalog height (0) still prefers master when HEVC exists.
     */
    fun preferredIndex(
        options: List<Option>,
        maxHevcHeight: Int,
        masterHeight: Int,
    ): Int {
        if (options.isEmpty()) return -1
        val masterIdx = options.indexOfFirst { it.kind == Kind.MASTER }
        val hevcOk =
            maxHevcHeight > 0 &&
                (masterHeight <= 0 || maxHevcHeight >= masterHeight)
        if (masterIdx >= 0 && hevcOk) return masterIdx
        val webIdx = options.indexOfFirst { it.kind == Kind.WEB }
        if (webIdx >= 0) return webIdx
        val mobileIdx = options.indexOfFirst { it.kind == Kind.MOBILE }
        if (mobileIdx >= 0) return mobileIdx
        return 0
    }

    /** Next rung after [currentIndex], or -1 if none. */
    fun nextIndex(options: List<Option>, currentIndex: Int): Int {
        if (currentIndex < 0) return preferredIndex(options, maxHevcHeight = 0, masterHeight = 0)
        val next = currentIndex + 1
        return if (next in options.indices) next else -1
    }
}
