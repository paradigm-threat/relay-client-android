package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Test

class NowShowingMarqueeTest {
    @Test
    fun wrapScrollX_resetsAtHalf() {
        assertEquals(0, NowShowingMarquee.wrapScrollX(1000, 1000))
        assertEquals(1, NowShowingMarquee.wrapScrollX(1001, 1000))
        assertEquals(50, NowShowingMarquee.wrapScrollX(50, 1000))
        assertEquals(0, NowShowingMarquee.wrapScrollX(2000, 1000))
    }

    @Test
    fun wrapScrollX_noopWhenHalfUnknown() {
        assertEquals(40, NowShowingMarquee.wrapScrollX(40, 0))
        assertEquals(0, NowShowingMarquee.wrapScrollX(-5, 0))
    }
}
