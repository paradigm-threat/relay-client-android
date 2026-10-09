package com.clevertree.relayruntime.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-safe mirrors of [ExternalMediaIntents] URL heuristics (no Android Context).
 */
object ExternalMediaUrlHeuristics {
    fun looksLikeVideo(url: String): Boolean {
        val s = url.lowercase()
        return s.contains(".mkv") || s.contains(".mp4") || s.contains(".webm") ||
            s.contains(".m4v") || s.contains(".mov") || s.contains("video/")
    }

    fun isHttpMedia(url: String): Boolean {
        val t = url.trim()
        return t.startsWith("http://") || t.startsWith("https://")
    }
}

class ExternalMediaUrlHeuristicsTest {
    @Test
    fun mkvAndMp4CountAsVideo() {
        assertTrue(ExternalMediaUrlHeuristics.looksLikeVideo("https://x/a.mkv"))
        assertTrue(ExternalMediaUrlHeuristics.looksLikeVideo("https://x/a.MP4"))
        assertFalse(ExternalMediaUrlHeuristics.looksLikeVideo("https://x/readme.md"))
    }

    @Test
    fun httpDetection() {
        assertTrue(ExternalMediaUrlHeuristics.isHttpMedia(" https://flashbackrepository.org/x.mkv "))
        assertFalse(ExternalMediaUrlHeuristics.isHttpMedia("intent://…"))
    }
}
