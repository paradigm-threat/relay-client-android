package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpfsOfflineStoreLogicTest {
    @Test
    fun ladderResolvesEmptyCidToCatalogUrl() {
        val opt = FlashbackStreamLadder.Option(
            FlashbackStreamLadder.Kind.WEB,
            "1080p",
            "https://example.com/a.mp4",
            cid = "",
        )
        assertEquals("https://example.com/a.mp4", FlashbackStreamLadder.resolveLocalPlayUrl(opt))
    }

    @Test
    fun ladderRequiresReadySessionForCid() {
        // Without a running Android IpfsSession, ready is false → null (no Relay fallback).
        val opt = FlashbackStreamLadder.Option(
            FlashbackStreamLadder.Kind.MASTER,
            "Full",
            "https://example.com/ipfs/bafytest",
            cid = "bafytest",
        )
        assertEquals(null, FlashbackStreamLadder.resolveLocalPlayUrl(opt))
    }

    @Test
    fun optionsCarryCids() {
        val p = FlashbackProject(
            slug = "t",
            category = "FanMixes",
            path = "FanMixes/T",
            title = "T",
            originalTitle = "T",
            status = "showing",
            media = "T.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "",
            blurb = "",
            ipfsCid = "bafymaster",
        )
        val opts = FlashbackStreamLadder.options(p, "https://example.com")
        assertEquals(1, opts.size)
        assertEquals("bafymaster", opts[0].cid)
        assertTrue(opts[0].url.contains("/ipfs/bafymaster"))
    }
}
