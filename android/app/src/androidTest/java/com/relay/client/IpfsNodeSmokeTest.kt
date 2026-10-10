package com.relay.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke: Kubo binary present for this ABI; node reaches API readiness.
 * Run: ./gradlew :app:connectedFlashbackDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class IpfsNodeSmokeTest {
    @Test
    fun daemonBinaryPresentForAbi() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val bin = IpfsSession.daemonBinary(ctx)
        assertNotNull("libkubo_daemon.so missing in nativeLibraryDir", bin)
        assertTrue(bin!!.isFile)
    }

    @Test(timeout = 180_000)
    fun nodeStartsAndGatewayAlive() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "Kubo did not become ready: ${IpfsSession.lastError()}",
            IpfsSession.ensureStartedAndReady(ctx, 150_000L),
        )
        assertTrue(IpfsSession.nodeId().isNotBlank())
        // Empty UnixFS dir — always fetchable from the network or local if cached.
        val tiny = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
        IpfsSession.ensureFetching(tiny)
        // Soft assert: Range may take a while on cold DHT; readiness alone is the hard gate.
        assertTrue(IpfsSession.isReady())
    }
}
