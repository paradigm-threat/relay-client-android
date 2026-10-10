package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackStreamLadderTest {
    private fun project(
        media: String = "Title.mkv",
        mediaWeb: String = "Title.web-1080p.mp4",
        mediaMobile: String = "Title.mobile-720p.mp4",
        height: Int = 2160,
    ) = FlashbackProject(
        slug = "title",
        category = "FanMixes",
        path = "FanMixes/Title",
        title = "Title",
        originalTitle = "Title",
        status = "showing",
        media = media,
        mediaWeb = mediaWeb,
        mediaMobile = mediaMobile,
        image = "",
        blurb = "",
        height = height,
    )

    @Test
    fun optionsIncludeAllRungs() {
        val opts = FlashbackStreamLadder.options(project(), "https://example.com")
        assertEquals(3, opts.size)
        assertEquals(FlashbackStreamLadder.Kind.MASTER, opts[0].kind)
        assertEquals(FlashbackStreamLadder.Kind.WEB, opts[1].kind)
        assertEquals(FlashbackStreamLadder.Kind.MOBILE, opts[2].kind)
        assertTrue(opts[0].url.contains("Title.mkv"))
        assertTrue(opts[1].url.contains("web-1080p"))
        assertTrue(opts[2].url.contains("mobile-720p"))
    }

    @Test
    fun prefersMasterWhenHevcCoversHeight() {
        val opts = FlashbackStreamLadder.options(project(), "https://example.com")
        assertEquals(
            0,
            FlashbackStreamLadder.preferredIndex(opts, maxHevcHeight = 2160, masterHeight = 2160),
        )
    }

    @Test
    fun prefersWebWhenHevcMaxIs1080() {
        val opts = FlashbackStreamLadder.options(project(), "https://example.com")
        assertEquals(
            1,
            FlashbackStreamLadder.preferredIndex(opts, maxHevcHeight = 1088, masterHeight = 2160),
        )
    }

    @Test
    fun prefersMobileWhenOnlyMobileAndNoHevc() {
        val opts = FlashbackStreamLadder.options(
            project(media = "", mediaWeb = "", mediaMobile = "Title.mobile-720p.mp4"),
            "https://example.com",
        )
        assertEquals(1, opts.size)
        assertEquals(
            0,
            FlashbackStreamLadder.preferredIndex(opts, maxHevcHeight = 0, masterHeight = 2160),
        )
    }

    @Test
    fun nextIndexAdvancesThenEnds() {
        val opts = FlashbackStreamLadder.options(project(), "https://example.com")
        assertEquals(1, FlashbackStreamLadder.nextIndex(opts, 0))
        assertEquals(2, FlashbackStreamLadder.nextIndex(opts, 1))
        assertEquals(-1, FlashbackStreamLadder.nextIndex(opts, 2))
    }

    @Test
    fun webStreamUrlUsesCidWhenPresent() {
        val p = project().copy(ipfsCidWeb = "bafywebcid")
        assertEquals(
            "https://example.com/ipfs/bafywebcid",
            p.webStreamUrl("https://example.com/"),
        )
    }
}
