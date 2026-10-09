package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackCatalogTest {
    @Test
    fun encodePathSegmentPercentEncodesSpaces() {
        assertEquals("The%20Mummy%20Debugged.mkv", FlashbackCatalog.encodePathSegment("The Mummy Debugged.mkv"))
        assertFalse(FlashbackCatalog.encodePathSegment("a b").contains("+"))
    }

    @Test
    fun joinOriginPathBuildsMummyMasterUrl() {
        val url = FlashbackCatalog.joinOriginPath(
            "https://flashbackrepository.org/",
            "FanMixes/The Mummy (1999) - Debugged",
            "The Mummy Debugged.mkv",
        )
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/The%20Mummy%20Debugged.mkv",
            url,
        )
    }

    @Test
    fun masterStreamUrlUsesEncodedSegments() {
        val p = FlashbackProject(
            slug = "the-mummy-1999-debugged",
            category = "FanMixes",
            path = "FanMixes/The Mummy (1999) - Debugged",
            title = "The Mummy (1999) — Debugged",
            originalTitle = "The Mummy (1999)",
            status = "showing",
            media = "The Mummy Debugged.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "FanMixes/The Mummy (1999) - Debugged/poster.jpg",
            blurb = "",
        )
        assertTrue(p.isShowing)
        val stream = p.masterStreamUrl("https://flashbackrepository.org")
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/The%20Mummy%20Debugged.mkv",
            stream,
        )
        assertNull(
            p.copy(media = "").masterStreamUrl("https://flashbackrepository.org"),
        )
    }

    @Test
    fun posterUrlEncodesRelativePath() {
        val p = FlashbackProject(
            slug = "x",
            category = "FanMixes",
            path = "FanMixes/X",
            title = "X",
            originalTitle = "X",
            status = "showing",
            media = "x.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "FanMixes/The Mummy (1999) - Debugged/poster.jpg",
            blurb = "",
        )
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/poster.jpg",
            p.posterUrl("https://flashbackrepository.org/"),
        )
        assertEquals(
            "https://cdn.example/p.jpg",
            p.copy(image = "https://cdn.example/p.jpg").posterUrl("https://flashbackrepository.org"),
        )
    }
}
