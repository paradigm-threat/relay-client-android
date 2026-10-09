package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackSubtitlesTest {
    @Test
    fun detectsEnhancedLabels() {
        assertTrue(FlashbackSubtitles.isEnhancedLabel("Enhanced / Citations"))
        assertTrue(FlashbackSubtitles.isEnhancedLabel("english citations"))
        assertFalse(FlashbackSubtitles.isEnhancedLabel("English (default)"))
    }

    @Test
    fun prefersFirstOrDefaultWhenNotEnhanced() {
        val labels = listOf("English (default)", "Enhanced / Citations")
        assertEquals(0, FlashbackSubtitles.preferredIndex(labels, preferEnhanced = false))
        assertEquals(
            0,
            FlashbackSubtitles.preferredIndex(listOf("English", "French"), preferEnhanced = false),
        )
    }

    @Test
    fun prefersEnhancedWhenRequested() {
        val labels = listOf("English (default)", "Enhanced / Citations")
        assertEquals(1, FlashbackSubtitles.preferredIndex(labels, preferEnhanced = true))
        assertEquals(
            0,
            FlashbackSubtitles.preferredIndex(
                listOf("English (default)"),
                preferEnhanced = true,
            ),
        )
    }
}
