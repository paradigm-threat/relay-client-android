package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackSiteContentTest {
    @Test
    fun drawerMatchesSiteNavPlusAndroidExtras() {
        val ids = FlashbackSiteContent.drawerItems().map { it.id }
        assertEquals(
            listOf(
                "library",
                "app",
                "setup",
                "request",
                "motivation",
                "submit",
                "board",
                "chat",
                "legal",
                "terms",
                "theme",
                "android-lab",
                "updates",
            ),
            ids,
        )
        val theme = FlashbackSiteContent.drawerItems().first { it.id == "theme" }
        assertTrue(theme.title.startsWith("Theme:"))
        assertTrue(theme.action is FlashbackSiteContent.NavAction.CycleTheaterTheme)
        val board = FlashbackSiteContent.drawerItems().first { it.id == "board" }
        assertEquals("Report a bug", board.title)
        assertTrue(board.action is FlashbackSiteContent.NavAction.Board)
        val chat = FlashbackSiteContent.drawerItems().first { it.id == "chat" }
        assertEquals("Chat", chat.title)
        assertTrue(chat.action is FlashbackSiteContent.NavAction.Chat)
    }

    @Test
    fun bundledMarkdownCoversDrawerDocs() {
        val mdPaths = FlashbackSiteContent.drawerItems()
            .mapNotNull { (it.action as? FlashbackSiteContent.NavAction.Markdown)?.path }
        for (path in mdPaths) {
            assertTrue("missing bundle for $path", FlashbackSiteContent.bundledMarkdownPaths.contains(path))
        }
        assertTrue(FlashbackSiteContent.bundledMarkdownPaths.contains("content/android-lab.md"))
    }
}
