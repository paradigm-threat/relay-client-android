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
                "lobby-music",
                "seeding",
                "android-lab",
                "updates",
            ),
            ids,
        )
        val seeding = FlashbackSiteContent.drawerItems().first { it.id == "seeding" }
        assertEquals("Downloads", seeding.title)
        assertTrue(seeding.action is FlashbackSiteContent.NavAction.SeedingManage)
        val theme = FlashbackSiteContent.drawerItems().first { it.id == "theme" }
        assertTrue(theme.title.startsWith("Theme:"))
        assertTrue(theme.action is FlashbackSiteContent.NavAction.CycleTheaterTheme)
        val lobby = FlashbackSiteContent.drawerItems().first { it.id == "lobby-music" }
        assertEquals("Music: on", lobby.title)
        assertTrue(lobby.action is FlashbackSiteContent.NavAction.ToggleLobbyMusic)
        val board = FlashbackSiteContent.drawerItems().first { it.id == "board" }
        assertEquals("Bugs", board.title)
        assertTrue(board.action is FlashbackSiteContent.NavAction.Board)
        val updates = FlashbackSiteContent.drawerItems().first { it.id == "updates" }
        assertEquals("Updates", updates.title)
        val lab = FlashbackSiteContent.drawerItems().first { it.id == "android-lab" }
        assertEquals("Lab", lab.title)
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
