package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackAssetStatusTest {
    @Test
    fun parseCachedReady() {
        val r =
            FlashbackAssetStatus.parse(
                """{"error":"ok","seed":{"state":"cached","providers":2}}""",
            )
        assertTrue(r.ready)
        assertEquals("cached", r.seedState)
        assertEquals(2, r.providers)
    }

    @Test
    fun parseFetchingNotReady() {
        val r =
            FlashbackAssetStatus.parse(
                """{"error":"ipfs-pending","seed":{"state":"fetching","providers":0}}""",
            )
        assertFalse(r.ready)
        assertEquals("fetching", r.seedState)
    }

    @Test
    fun parseMissingNotReady() {
        val r =
            FlashbackAssetStatus.parse(
                """{"error":"not-found","message":"needs to be seeded","seed":{"state":"missing"}}""",
            )
        assertFalse(r.ready)
        assertTrue(r.message.contains("seeded"))
    }
}
