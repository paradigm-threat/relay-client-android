package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackBugReportTest {
    @Test
    fun encodeCommentTargetPathMatchesNonAlphanumeric() {
        assertEquals("home", FlashbackBugReport.encodeCommentTargetPath("home"))
        assertEquals(
            "android",
            FlashbackBugReport.encodeCommentTargetPath("/android"),
        )
        assertEquals(
            "FanMixes%2FThe%20Mummy",
            FlashbackBugReport.encodeCommentTargetPath("FanMixes/The Mummy"),
        )
    }

    @Test
    fun httpUrlDoubleEncodesPercentSegments() {
        val encoded = FlashbackBugReport.encodeCommentTargetPath("a/b")
        assertEquals("a%2Fb", encoded)
        // One encode: % → %25; second encode: %25 → %2525 (matches Board.host.js).
        assertEquals("a%25252Fb", FlashbackBugReport.encodeURIComponent(FlashbackBugReport.encodeURIComponent(encoded)))
        val url = FlashbackBugReport.httpUrlForRepoPath(
            FlashbackBugReport.PATH_ROOT + encoded + "/20261009T120000Z-bug.md",
        )
        assertTrue(url.contains("%25252F"))
        assertTrue(url.startsWith("/.relay/comments/by-path/"))
        assertTrue(url.contains("20261009T120000Z-bug.md"))
    }

    @Test
    fun buildMarkdownMatchesWebFrontMatter() {
        val md = FlashbackBugReport.buildMarkdown(
            kind = "bug",
            pagePath = "/android",
            created = "20261009T120000Z",
            name = "Ari",
            body = "Playback failed\n\nmore",
        )
        assertTrue(md.startsWith("---\n"))
        assertTrue(md.contains("kind: bug\n"))
        assertTrue(md.contains("path: \"/android\"\n"))
        assertTrue(md.contains("created: 20261009T120000Z\n"))
        assertTrue(md.contains("name: \"Ari\"\n"))
        assertTrue(md.contains("\n---\n\nPlayback failed\n\nmore\n"))
    }

    @Test
    fun composePostBodyAppendsDebugSnapshot() {
        val snap = FlashbackBugReport.DebugSnapshot(
            versionName = "1.0.14",
            versionCode = 14,
            packageName = "com.flashback.repository",
            deviceModel = "Pixel 7",
            sdkInt = 34,
            origin = "https://flashbackrepository.org",
            screen = "theater",
            slug = "the-mummy-debugged",
            lastPlaybackError = "Can't play in this app …",
            logcatSnippet = null,
        )
        val body = FlashbackBugReport.composePostBody("Title", "Details here", snap)
        assertTrue(body.startsWith("Title\n\nDetails here"))
        assertTrue(body.contains("### Android debug snapshot"))
        assertTrue(body.contains("app: 1.0.14 (14)"))
        assertTrue(body.contains("slug: the-mummy-debugged"))
        assertTrue(body.contains("lastPlaybackError:"))
    }

    @Test
    fun filePathUsesHomeForRoot() {
        val path = FlashbackBugReport.filePathFor("/", "bug", "20261009T120000Z")
        assertEquals(
            ".relay/comments/by-path/home/20261009T120000Z-bug.md",
            path,
        )
    }

    @Test
    fun putUrlJoinsOriginAndEncodedPath() {
        val url = FlashbackBugReport.putUrl(
            "https://flashbackrepository.org/",
            ".relay/comments/by-path/android/x-bug.md",
        )
        assertEquals(
            "https://flashbackrepository.org" +
                FlashbackBugReport.httpUrlForRepoPath(".relay/comments/by-path/android/x-bug.md"),
            url,
        )
        assertFalse(url.contains("//.relay"))
    }

    @Test
    fun utcStampIsCompactUtc() {
        // Fixed millis: 2026-10-09 15:30:45 UTC
        val stamp = FlashbackBugReport.utcStamp(1_760_021_445_000L)
        assertTrue(stamp.matches(Regex("""\d{8}T\d{6}Z""")))
    }

    @Test
    fun parsePublishOriginHostFromFleet403() {
        val detail =
            "this node (ord1.relaygateway.net) is not the catalog publish_origin " +
                "for fan-edits (publish_origin=iad1.relaygateway.net). Push git://…"
        assertEquals("iad1.relaygateway.net", FlashbackBugReport.parsePublishOriginHost(detail))
        assertEquals(null, FlashbackBugReport.parsePublishOriginHost("forbidden"))
    }
}
