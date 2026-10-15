package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpfsNetworkStateTest {
    @Test
    fun downloadCountersClampAtZero() {
        // Drain any leftover from other tests in the same JVM.
        while (IpfsNetworkState.activeDownloadCount() > 0) {
            IpfsNetworkState.endDownload()
        }
        while (IpfsNetworkState.activePinJobCount() > 0) {
            IpfsNetworkState.endPinJob()
        }
        assertFalse(IpfsNetworkState.isDownloading())
        IpfsNetworkState.beginDownload()
        IpfsNetworkState.beginPinJob()
        assertTrue(IpfsNetworkState.isDownloading())
        assertEquals(1, IpfsNetworkState.activeDownloadCount())
        assertEquals(1, IpfsNetworkState.activePinJobCount())
        IpfsNetworkState.endDownload()
        IpfsNetworkState.endDownload()
        IpfsNetworkState.endPinJob()
        IpfsNetworkState.endPinJob()
        assertEquals(0, IpfsNetworkState.activeDownloadCount())
        assertEquals(0, IpfsNetworkState.activePinJobCount())
        assertFalse(IpfsNetworkState.isDownloading())
    }

    @Test
    fun playbackHoldToggles() {
        IpfsNetworkState.setPlaybackHold(true)
        assertTrue(IpfsNetworkState.playbackHold())
        IpfsNetworkState.setPlaybackHold(false)
        assertFalse(IpfsNetworkState.playbackHold())
    }
}
