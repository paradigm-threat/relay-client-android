package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FlashbackLobbyPlaylistTest {
    private val sample = """
        {
          "format": "flashback-logical-playlist/v1",
          "state": {
            "season": {
              "source": "calendar",
              "christmas": { "start": "11-15", "end": "01-02" }
            },
            "theater": { "source": "host" },
            "enabled": { "source": "host" }
          },
          "defaultSong": "publicDomain",
          "when": [
            { "if": "!enabled || theater", "do": "fadeOut", "ms": 800 },
            {
              "if": "enabled && !theater && season == christmas",
              "do": "play",
              "song": "christmas"
            },
            { "if": "enabled && !theater", "do": "play", "song": "publicDomain" }
          ],
          "songs": {
            "publicDomain": {
              "loop": true,
              "shuffle": true,
              "tracks": [
                "https://example.com/a.mp3",
                "https://example.com/b.mp3"
              ]
            },
            "christmas": {
              "loop": true,
              "shuffle": true,
              "tracks": ["https://example.com/xmas.mp3"]
            }
          },
          "volume": 0.28,
          "fadeInMs": 1200,
          "fadeOutMs": 800
        }
    """.trimIndent()

    private fun ms(year: Int, month1: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month1 - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun calendarSeason_wrapsNov15ToJan2() {
        assertEquals(
            "christmas",
            FlashbackLobbyPlaylist.calendarSeason(ms(2026, 11, 15)),
        )
        assertEquals(
            "christmas",
            FlashbackLobbyPlaylist.calendarSeason(ms(2026, 12, 25)),
        )
        assertEquals(
            "christmas",
            FlashbackLobbyPlaylist.calendarSeason(ms(2027, 1, 2)),
        )
        assertEquals(
            "default",
            FlashbackLobbyPlaylist.calendarSeason(ms(2026, 11, 14)),
        )
        assertEquals(
            "default",
            FlashbackLobbyPlaylist.calendarSeason(ms(2027, 1, 3)),
        )
        assertEquals(
            "default",
            FlashbackLobbyPlaylist.calendarSeason(ms(2026, 10, 15)),
        )
    }

    @Test
    fun evaluate_fadeOutWhenDisabledOrTheater() {
        val off = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = false,
                theater = false,
                nowMs = ms(2026, 6, 1),
            ),
        )
        assertEquals("fadeOut", off.action)
        assertEquals(800, off.fadeOutMs)

        val theater = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = true,
                theater = true,
                nowMs = ms(2026, 6, 1),
            ),
        )
        assertEquals("fadeOut", theater.action)
    }

    @Test
    fun evaluate_publicDomainOutsideChristmas() {
        val dec = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = true,
                theater = false,
                nowMs = ms(2026, 10, 15),
            ),
        )
        assertEquals("play", dec.action)
        assertEquals("publicDomain", dec.song)
        assertEquals("default", dec.season)
        assertEquals(2, dec.tracks.size)
        assertTrue(dec.shuffle)
        assertEquals(0.28f, dec.volume, 0.001f)
    }

    @Test
    fun evaluate_christmasInSeason() {
        val dec = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = true,
                theater = false,
                nowMs = ms(2026, 12, 20),
            ),
        )
        assertEquals("play", dec.action)
        assertEquals("christmas", dec.song)
        assertEquals("christmas", dec.season)
        assertEquals(listOf("https://example.com/xmas.mp3"), dec.tracks)
    }

    @Test
    fun evaluate_skipsNonHttpTracks() {
        val json = """
            {
              "when": [
                { "if": "enabled && !theater", "do": "play", "song": "publicDomain" }
              ],
              "songs": {
                "publicDomain": {
                  "tracks": ["not-a-url", "https://ok.example/a.mp3", "ftp://bad"]
                }
              }
            }
        """.trimIndent()
        val dec = FlashbackLobbyPlaylist.evaluate(
            json,
            FlashbackLobbyPlaylist.HostState(enabled = true, theater = false),
        )
        assertEquals(listOf("https://ok.example/a.mp3"), dec.tracks)
    }

    @Test
    fun shuffle_deterministicAndPreservesMembership() {
        val tracks = listOf("a", "b", "c", "d", "e")
        val a = FlashbackLobbyPlaylist.shuffle(tracks, seed = 42L)
        val b = FlashbackLobbyPlaylist.shuffle(tracks, seed = 42L)
        assertEquals(a, b)
        assertNotEquals(tracks, a)
        assertEquals(tracks.sorted(), a.sorted())
        assertFalse(a === tracks)
    }

    @Test
    fun evaluate_matchesWebSampleMidyearAndChristmas() {
        // Parity smoke vs LobbyPlaylist.js tip rules (same SAMPLE shape).
        val mid = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = true,
                theater = false,
                nowMs = ms(2026, 6, 15),
            ),
        )
        assertEquals("publicDomain", mid.song)

        val xmas = FlashbackLobbyPlaylist.evaluate(
            sample,
            FlashbackLobbyPlaylist.HostState(
                enabled = true,
                theater = false,
                nowMs = ms(2026, 12, 1),
            ),
        )
        assertEquals("christmas", xmas.song)
    }
}
