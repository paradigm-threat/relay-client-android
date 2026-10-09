package com.clevertree.relayruntime.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevcCapabilityTest {
    @Test
    fun oemHevcLooksHardware() {
        assertTrue(HevcCapability.looksHardware("OMX.qcom.video.decoder.hevc"))
        assertTrue(HevcCapability.looksHardware("c2.qti.hevc.decoder"))
        assertTrue(HevcCapability.looksHardware("OMX.Exynos.HEVC.Decoder"))
    }

    @Test
    fun googleSoftwareRejected() {
        assertFalse(HevcCapability.looksHardware("OMX.google.hevc.decoder"))
        assertFalse(HevcCapability.looksHardware("c2.android.hevc.decoder"))
    }

    @Test
    fun filterKeepsHardwareOnly() {
        val filtered = HevcCapability.filterHardwareHevcNames(
            listOf(
                "OMX.google.hevc.decoder",
                "OMX.qcom.video.decoder.hevc",
                "c2.android.hevc.decoder",
            ),
        )
        assertTrue(filtered.single().contains("qcom"))
    }
}
