package com.relay.client

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackChatTest {
    @Test
    fun normalizeChannelStripsHashAndJunk() {
        assertEquals("flashback", FlashbackChatApi.normalizeChannel("#FlashBack"))
        // Path separators kept for movie channels; punctuation stripped.
        assertEquals("movie/congo", FlashbackChatApi.normalizeChannel("movie/congo!!"))
        assertEquals("flashback", FlashbackChatApi.normalizeChannel("   "))
    }

    @Test
    fun wsUrlUsesWssForHttpsOrigin() {
        assertEquals(
            "wss://flashbackrepository.org/chat/ws",
            FlashbackChatApi.wsUrl("https://flashbackrepository.org"),
        )
        assertEquals(
            "ws://localhost:8080/chat/ws",
            FlashbackChatApi.wsUrl("http://localhost:8080"),
        )
    }

    @Test
    fun parseMessageObjectFromWsFrame() {
        val o =
            JSONObject()
                .put("type", "message")
                .put("id", "1")
                .put("ts", "2026-01-01T00:00:00Z")
                .put("channel", "#flashback")
                .put("nick", "Alice")
                .put("kind", "privmsg")
                .put("text", "hello")
        val msg = FlashbackChatApi.parseMessageObject(o)!!
        assertEquals("flashback", msg.channel)
        assertEquals("Alice", msg.nick)
        assertEquals("hello", msg.text)
    }

    @Test
    fun parseChannelsArray() {
        val arr =
            JSONArray()
                .put(JSONObject().put("name", "flashback").put("topic", "Lobby").put("messages", 3))
                .put(JSONObject().put("name", "#page-setup").put("topic", "Setup"))
        val list = FlashbackChatApi.parseChannels(arr)
        assertEquals(2, list.size)
        assertEquals("flashback", list[0].name)
        assertEquals("page-setup", list[1].name)
    }

    @Test
    fun digestEncodeDecodeRoundTrip() {
        val enc = FlashbackChatPrefs.encodeDigestCounts(mapOf("b" to 2, "a" to 1))
        assertEquals("a=1|b=2", enc)
        val dec = FlashbackChatPrefs.decodeDigestCounts(enc)
        assertEquals(1, dec["a"])
        assertEquals(2, dec["b"])
    }

    @Test
    fun coalesceQuietWindow() {
        val counts = mapOf("flashback" to 3, "page-setup" to 1)
        val first =
            FlashbackChatNotifier.decide(
                counts = counts,
                preview = "<n> hi",
                lastChannel = "flashback",
                nowMs = 100_000L,
                lastAlertMs = 0L,
                quietMs = 10 * 60_000L,
            )
        assertTrue(first.alert)
        assertTrue(first.title.contains("2 channels"))

        val within =
            FlashbackChatNotifier.decide(
                counts = counts,
                preview = "<n> hi",
                lastChannel = "flashback",
                nowMs = 100_000L + 60_000L,
                lastAlertMs = 100_000L,
                quietMs = 10 * 60_000L,
            )
        assertFalse(within.alert)

        val after =
            FlashbackChatNotifier.decide(
                counts = mapOf("flashback" to 1),
                preview = "<n> hi",
                lastChannel = "flashback",
                nowMs = 100_000L + 11 * 60_000L,
                lastAlertMs = 100_000L,
                quietMs = 10 * 60_000L,
            )
        assertTrue(after.alert)
        assertTrue(after.title.contains("#flashback"))
    }

    @Test
    fun parseWsEventType() {
        val pair = FlashbackChatApi.parseWsEvent("""{"type":"hello","nick":"X"}""")!!
        assertEquals("hello", pair.first)
        assertEquals("X", pair.second.getString("nick"))
    }
}
