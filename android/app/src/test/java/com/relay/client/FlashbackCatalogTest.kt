package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackCatalogTest {
    private fun project(
        slug: String,
        status: String = "catalog",
        category: String = "FanMixes",
        title: String = slug,
        media: String = "",
        image: String = "",
    ) = FlashbackProject(
        slug = slug,
        category = category,
        path = "$category/$slug",
        title = title,
        originalTitle = title,
        status = status,
        media = media,
        mediaWeb = "",
        mediaMobile = "",
        image = image,
        blurb = "",
    )

    @Test
    fun encodePathSegmentPercentEncodesSpaces() {
        assertEquals("The%20Mummy%20Debugged.mkv", FlashbackCatalog.encodePathSegment("The Mummy Debugged.mkv"))
        assertFalse(FlashbackCatalog.encodePathSegment("a b").contains("+"))
    }

    @Test
    fun joinOriginPathBuildsMummyMasterUrl() {
        val url = FlashbackCatalog.joinOriginPath(
            "https://flashbackrepository.org/",
            "FanMixes/The Mummy (1999) - Debugged",
            "The Mummy Debugged.mkv",
        )
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/The%20Mummy%20Debugged.mkv",
            url,
        )
    }

    @Test
    fun masterStreamUrlUsesEncodedSegments() {
        val p = FlashbackProject(
            slug = "the-mummy-1999-debugged",
            category = "FanMixes",
            path = "FanMixes/The Mummy (1999) - Debugged",
            title = "The Mummy (1999) — Debugged",
            originalTitle = "The Mummy (1999)",
            status = "showing",
            media = "The Mummy Debugged.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "FanMixes/The Mummy (1999) - Debugged/poster.jpg",
            blurb = "",
        )
        assertTrue(p.isShowing)
        val stream = p.masterStreamUrl("https://flashbackrepository.org")
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/The%20Mummy%20Debugged.mkv",
            stream,
        )
        assertNull(
            p.copy(media = "").masterStreamUrl("https://flashbackrepository.org"),
        )
    }

    @Test
    fun masterStreamUrlPrefersIpfsCid() {
        val p = FlashbackProject(
            slug = "the-mummy-1999-debugged",
            category = "FanMixes",
            path = "FanMixes/The Mummy (1999) - Debugged",
            title = "Mummy",
            originalTitle = "Mummy",
            status = "showing",
            media = "The Mummy Debugged.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "",
            blurb = "",
            ipfsCid = "bafybeicfynx4o23njy4owttcziexmm4f6ibnqceharrhoxsvsbaaax7rxq",
        )
        assertEquals(
            "https://flashbackrepository.org/ipfs/bafybeicfynx4o23njy4owttcziexmm4f6ibnqceharrhoxsvsbaaax7rxq",
            p.masterStreamUrl("https://flashbackrepository.org/"),
        )
    }

    @Test
    fun parseAssetsManifestCidsMapsPaths() {
        val json =
            """
            {"schema":"x","assets":[
              {"path":"FanMixes/The Mummy (1999) - Debugged/The Mummy Debugged.mkv",
               "ipfs":"bafybeicfynx4o23njy4owttcziexmm4f6ibnqceharrhoxsvsbaaax7rxq"}
            ]}
            """.trimIndent()
        val map = FlashbackCatalog.parseAssetsManifestCids(json)
        assertEquals(
            "bafybeicfynx4o23njy4owttcziexmm4f6ibnqceharrhoxsvsbaaax7rxq",
            map["FanMixes/The Mummy (1999) - Debugged/The Mummy Debugged.mkv"],
        )
    }

    @Test
    fun posterUrlEncodesRelativePath() {
        val p = FlashbackProject(
            slug = "x",
            category = "FanMixes",
            path = "FanMixes/X",
            title = "X",
            originalTitle = "X",
            status = "showing",
            media = "x.mkv",
            mediaWeb = "",
            mediaMobile = "",
            image = "FanMixes/The Mummy (1999) - Debugged/poster.jpg",
            blurb = "",
        )
        assertEquals(
            "https://flashbackrepository.org/FanMixes/The%20Mummy%20(1999)%20-%20Debugged/poster.jpg",
            p.posterUrl("https://flashbackrepository.org/"),
        )
        assertEquals(
            "https://cdn.example/p.jpg",
            p.copy(image = "https://cdn.example/p.jpg").posterUrl("https://flashbackrepository.org"),
        )
    }

    @Test
    fun resolveNowShowingPrefersSlugOrder() {
        val projects = listOf(
            project("a", status = "showing"),
            project("b", status = "catalog"),
            project("c", status = "showing"),
        )
        val resolved = FlashbackCatalog.resolveNowShowing(projects, listOf("c", "missing", "a"))
        assertEquals(listOf("c", "a"), resolved.map { it.slug })
    }

    @Test
    fun resolveNowShowingFallsBackToStatusShowing() {
        val projects = listOf(
            project("a", status = "catalog"),
            project("b", status = "showing"),
            project("c", status = "SHOWING"),
        )
        val resolved = FlashbackCatalog.resolveNowShowing(projects, emptyList())
        assertEquals(listOf("b", "c"), resolved.map { it.slug })
    }

    @Test
    fun hasStreamMediaChecksMediaFields() {
        assertFalse(project("x").hasStreamMedia)
        assertTrue(project("x", media = "x.mkv").hasStreamMedia)
        assertTrue(project("x").copy(mediaWeb = "x.mp4").hasStreamMedia)
    }

    @Test
    fun statsLineJoinsRuntimeFields() {
        val p = project("x").copy(
            faneditRuntime = "1:50:25",
            timeCut = "0:14:24",
            availableIn = "2160p HEVC MKV (AAC)",
        )
        assertEquals("edit 1:50:25 · cut 0:14:24 · 2160p HEVC MKV (AAC)", p.statsLine())
    }

    @Test
    fun parseIndexJsonReadsSubtitlesJson() {
        val json = """
            {
              "nowShowing": ["the-mummy-1999-debugged"],
              "projects": [{
                "slug": "the-mummy-1999-debugged",
                "category": "FanMixes",
                "path": "FanMixes/The Mummy (1999) - Debugged",
                "title": "The Mummy (1999) — Debugged",
                "originalTitle": "The Mummy (1999)",
                "status": "showing",
                "media": "The Mummy Debugged.mkv",
                "mediaWeb": "",
                "mediaMobile": "",
                "image": "",
                "blurb": "",
                "subtitlesJson": "[{\"src\":\"/FanMixes/The Mummy (1999) - Debugged/subs/The Mummy Debugged.english.vtt\",\"label\":\"English (default)\",\"lang\":\"en\",\"default\":true},{\"src\":\"/FanMixes/The Mummy (1999) - Debugged/subs/The Mummy Debugged.enhanced.vtt\",\"label\":\"Enhanced / Citations\",\"lang\":\"en\"}]"
              }]
            }
        """.trimIndent()
        val lib = FlashbackCatalog.parseIndexJson(json)
        assertEquals(1, lib.projects.size)
        val p = lib.projects[0]
        assertTrue(p.subtitlesJson.contains("english.vtt"))
        assertTrue(p.subtitlesJson.contains("enhanced.vtt"))
        assertTrue(p.subtitlesJson.contains("English (default)"))
        assertTrue(p.subtitlesJson.contains("Enhanced / Citations"))
    }

    @Test
    fun changesBriefFromReadmeTakesWhatChangedBullets() {
        val readme = """
            # Title

            Blurb with **bold**.

            ## Why this cut

            Prose.

            ## What changed

            - First removal
            - Second removal
            - Third removal
            - Fourth removal
            - Fifth ignored

            ## Subtitles

            - English
        """.trimIndent()
        val brief = FlashbackCatalog.changesBriefFromReadme(readme)
        assertEquals(
            "- First removal\n- Second removal\n- Third removal\n- Fourth removal",
            brief,
        )
    }

}
