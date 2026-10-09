package com.clevertree.relayruntime.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract smoke for gallery open/nav (soft-retry lives in load helpers). */
class RelayGalleryStoreTest {
    @Test
    fun registerOpenNextClose() {
        RelayGalleryStore.close()
        RelayGalleryStore.register("/a.png", "A")
        RelayGalleryStore.register("/b.png", "B")
        RelayGalleryStore.openAt("/a.png")
        assertTrue(RelayGalleryStore.isOpen())
        assertEquals("/a.png", RelayGalleryStore.current()?.src)
        RelayGalleryStore.next()
        assertEquals("/b.png", RelayGalleryStore.current()?.src)
        RelayGalleryStore.prev()
        assertEquals("/a.png", RelayGalleryStore.current()?.src)
        RelayGalleryStore.close()
        assertFalse(RelayGalleryStore.isOpen())
    }
}
