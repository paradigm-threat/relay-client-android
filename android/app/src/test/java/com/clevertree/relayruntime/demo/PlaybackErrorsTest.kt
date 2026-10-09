package com.clevertree.relayruntime.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorsTest {
    @Test
    fun decoderCodesMapToHevcCopy() {
        val codes = listOf(
            PlaybackErrors.CODE_DECODER_INIT_FAILED,
            PlaybackErrors.CODE_DECODING_FAILED,
            PlaybackErrors.CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackErrors.CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
        for (code in codes) {
            val m = PlaybackErrors.classify(code)
            assertEquals(PlaybackErrors.Kind.DECODER, m.kind)
            assertTrue(m.body.contains("HEVC", ignoreCase = true))
            assertEquals("Open in external player", m.primaryAction)
            assertEquals("Get VLC", m.secondaryAction)
        }
    }

    @Test
    fun networkCodes() {
        val m = PlaybackErrors.classify(PlaybackErrors.CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals(PlaybackErrors.Kind.NETWORK, m.kind)
        assertTrue(m.title.contains("Stream", ignoreCase = true))
    }

    @Test
    fun formatCodes() {
        val m = PlaybackErrors.classify(PlaybackErrors.CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertEquals(PlaybackErrors.Kind.FORMAT, m.kind)
        assertTrue(m.body.contains("Matroska", ignoreCase = true) || m.body.contains("external"))
    }

    @Test
    fun causeHintPromotesDecoder() {
        val m = PlaybackErrors.classify(1000, "MediaCodecVideoDecoderException: hevc")
        assertEquals(PlaybackErrors.Kind.DECODER, m.kind)
    }

    @Test
    fun bannerLineJoinsTitleAndBody() {
        val m = PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER)
        assertTrue(m.bannerLine().startsWith(m.title))
        assertTrue(m.bannerLine().contains("VLC"))
    }

    @Test
    fun shortToastIsShort() {
        assertTrue(PlaybackErrors.shortNoHandlerToast().length < 80)
        assertFalse(PlaybackErrors.shortNoHandlerToast().contains("HEVC"))
    }
}
