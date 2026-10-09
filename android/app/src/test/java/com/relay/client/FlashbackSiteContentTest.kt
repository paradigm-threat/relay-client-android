package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackSiteContentTest {
    @Test
    fun drawerMatchesSiteNavPlusAndroidExtras() {
        val ids = FlashbackSiteContent.drawerItems.map { it.id }
        assertEquals(
            listOf(
                "library",
                "setup",
                "request",
                "motivation",
                "submit",
                "board",
                "legal",
                "terms",
                "android-lab",
                "updates",
            ),
            ids,
        )
        val board = FlashbackSiteContent.drawerItems.first { it.id == "board" }
        assertEquals("Report a bug", board.title)
        assertTrue(board.action is FlashbackSiteContent.NavAction.Board)
    }

    @Test
    fun bundledMarkdownCoversDrawerDocs() {
        val mdPaths = FlashbackSiteContent.drawerItems
            .mapNotNull { (it.action as? FlashbackSiteContent.NavAction.Markdown)?.path }
        for (path in mdPaths) {
            assertTrue("missing bundle for $path", FlashbackSiteContent.bundledMarkdownPaths.contains(path))
        }
        assertTrue(FlashbackSiteContent.bundledMarkdownPaths.contains("content/android-lab.md"))
    }
}
