package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackHitsTest {

    @Test
    fun welcomeFirstTimeFiresSiteView() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.WELCOME_KEY,
            already = false,
            hadWelcome = false,
        )
        assertEquals(listOf(FlashbackHits.Hit(FlashbackHits.KIND_SITE)), hits)
    }

    @Test
    fun welcomeAlreadyAckedFiresNothing() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.WELCOME_KEY,
            already = true,
            hadWelcome = true,
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun titleFirstTimeFiresSiteAndMovie() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.movieKey("van-helsing-deanna-d"),
            already = false,
            hadWelcome = false,
            slugHint = "van-helsing-deanna-d",
        )
        assertEquals(
            listOf(
                FlashbackHits.Hit(FlashbackHits.KIND_SITE),
                FlashbackHits.Hit(FlashbackHits.KIND_MOVIE, "/van-helsing-deanna-d"),
            ),
            hits,
        )
    }

    @Test
    fun titleWhenWelcomeAlreadyOnlyFiresMovie() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.movieKey("the-mummy-debugged"),
            already = false,
            hadWelcome = true,
            slugHint = "the-mummy-debugged",
        )
        assertEquals(
            listOf(FlashbackHits.Hit(FlashbackHits.KIND_MOVIE, "/the-mummy-debugged")),
            hits,
        )
    }

    @Test
    fun titleAlreadyAckedFiresNothing() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.movieKey("serenity-unjossed"),
            already = true,
            hadWelcome = true,
            slugHint = "serenity-unjossed",
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun slugFromKeyAndMoviePath() {
        assertEquals(
            "van-helsing-deanna-d",
            FlashbackHits.slugFromKey(FlashbackOwnership.movieKey("van-helsing-deanna-d")),
        )
        assertEquals("", FlashbackHits.slugFromKey(FlashbackOwnership.WELCOME_KEY))
        assertEquals("/foo", FlashbackHits.moviePath("foo"))
        assertEquals("/foo", FlashbackHits.moviePath("/foo"))
        assertEquals("", FlashbackHits.moviePath("  "))
    }

    @Test
    fun titleSlugFallsBackToStorageKey() {
        val hits = FlashbackHits.hitsToReport(
            storageKey = FlashbackOwnership.movieKey("fallback-slug"),
            already = false,
            hadWelcome = true,
            slugHint = null,
        )
        assertEquals(
            listOf(FlashbackHits.Hit(FlashbackHits.KIND_MOVIE, "/fallback-slug")),
            hits,
        )
    }

    @Test
    fun payloadJsonMatchesWebShape() {
        assertEquals(
            """{"kind":"site_view"}""",
            FlashbackHits.payloadJson(FlashbackHits.Hit(FlashbackHits.KIND_SITE)),
        )
        assertEquals(
            """{"kind":"movie_view","path":"/van-helsing-deanna-d"}""",
            FlashbackHits.payloadJson(
                FlashbackHits.Hit(FlashbackHits.KIND_MOVIE, "/van-helsing-deanna-d"),
            ),
        )
    }

    @Test
    fun hitsUrlJoinsOrigin() {
        assertEquals(
            "https://flashbackrepository.org/api/hits",
            FlashbackHits.hitsUrl("https://flashbackrepository.org/"),
        )
        assertEquals(
            "https://flashbackrepository.org/api/hits",
            FlashbackHits.hitsUrl("https://flashbackrepository.org"),
        )
    }
}
