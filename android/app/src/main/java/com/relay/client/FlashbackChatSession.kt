package com.relay.client

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single JSON WebSocket to chat-edge. Joins watched notify channels ∪ active channel.
 */
class FlashbackChatSession private constructor(
    private val appContext: Context,
    private val origin: String,
) {
    interface Listener {
        fun onState(state: String) {}
        fun onHello(nick: String) {}
        fun onMessage(msg: FlashbackChatMessage) {}
        fun onNames(channel: String, names: List<String>) {}
        fun onTopic(channel: String, topic: String) {}
        fun onJoin(channel: String, nick: String) {}
        fun onPart(channel: String, nick: String) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val joined = HashSet<String>()
    private val wantJoin = HashSet<String>()
    private val running = AtomicBoolean(false)
    private val client =
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var nick: String = ""
    @Volatile private var activeChannel: String = FlashbackChatApi.DEFAULT_CHANNEL
    @Volatile private var focusedChannel: String? = null
    @Volatile private var reconnectAttempt = 0
    private var reconnectPosted = false

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun currentNick(): String = nick

    fun activeChannel(): String = activeChannel

    fun focusedChannel(): String? = focusedChannel

    fun setFocusedChannel(channel: String?) {
        focusedChannel = channel?.let { FlashbackChatApi.normalizeChannel(it) }
    }

    fun setActiveChannel(channel: String) {
        activeChannel = FlashbackChatApi.normalizeChannel(channel)
        syncJoins()
    }

    fun isConnected(): Boolean = socket != null && running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) {
            refreshWantedJoins()
            syncJoins()
            return
        }
        reconnectAttempt = 0
        refreshWantedJoins()
        connect()
    }

    fun stop() {
        running.set(false)
        reconnectPosted = false
        main.removeCallbacksAndMessages(null)
        try {
            socket?.close(1000, "stop")
        } catch (_: Exception) {
        }
        socket = null
        joined.clear()
        emitState("stopped")
    }

    fun privmsg(channel: String, text: String) {
        val ch = FlashbackChatApi.normalizeChannel(channel)
        val body = text.trim().take(400)
        if (body.isEmpty()) return
        send(JSONObject().put("type", "privmsg").put("channel", ch).put("text", body))
    }

    fun changeNick(newNick: String) {
        val n = newNick.trim().take(24)
        if (n.length < 2) return
        nick = n
        FlashbackChatPrefs.setNick(appContext, n)
        send(JSONObject().put("type", "nick").put("nick", n))
    }

    private fun refreshWantedJoins() {
        wantJoin.clear()
        wantJoin.add(FlashbackChatApi.normalizeChannel(activeChannel))
        wantJoin.addAll(FlashbackChatPrefs.notifyChannels(appContext))
    }

    private fun connect() {
        if (!running.get()) return
        emitState("connecting")
        ensureNickAsync {
            val req =
                Request.Builder()
                    .url(FlashbackChatApi.wsUrl(origin))
                    .build()
            socket = client.newWebSocket(req, listener)
        }
    }

    private fun ensureNickAsync(then: () -> Unit) {
        val stored = FlashbackChatPrefs.nick(appContext)
        if (stored.length >= 2) {
            nick = stored
            then()
            return
        }
        Thread {
            val n =
                try {
                    FlashbackChatApi.fetchName(origin)
                } catch (_: Exception) {
                    "Lobby-A1"
                }
            FlashbackChatPrefs.setNick(appContext, n)
            nick = n
            main.post { then() }
        }.start()
    }

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                joined.clear()
                if (nick.isNotBlank()) {
                    send(JSONObject().put("type", "nick").put("nick", nick))
                }
                main.post {
                    emitState("open")
                    refreshWantedJoins()
                    syncJoins()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = FlashbackChatApi.parseWsEvent(text) ?: return
                val (type, o) = parsed
                main.post { handleEvent(type, o) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                joined.clear()
                main.post {
                    emitState("closed")
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "chat ws failure: ${t.message}")
                socket = null
                joined.clear()
                main.post {
                    emitState("error")
                    scheduleReconnect()
                }
            }
        }

    private fun handleEvent(type: String, o: JSONObject) {
        when (type) {
            "hello", "nick" -> {
                val n = o.optString("nick")
                if (n.isNotBlank()) {
                    nick = n
                    FlashbackChatPrefs.setNick(appContext, n)
                    listeners.forEach { it.onHello(n) }
                }
            }
            "message" -> {
                val msg = FlashbackChatApi.parseMessageObject(o) ?: return
                listeners.forEach { it.onMessage(msg) }
                maybeNotify(msg)
            }
            "names" -> {
                val ch = FlashbackChatApi.normalizeChannel(o.optString("channel"))
                val arr = o.optJSONArray("names")
                val names = ArrayList<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) names.add(arr.optString(i))
                }
                listeners.forEach { it.onNames(ch, names) }
            }
            "topic" -> {
                val ch = FlashbackChatApi.normalizeChannel(o.optString("channel"))
                val topic = o.optString("topic")
                listeners.forEach { it.onTopic(ch, topic) }
            }
            "join" -> {
                val ch = FlashbackChatApi.normalizeChannel(o.optString("channel"))
                val n = o.optString("nick")
                listeners.forEach { it.onJoin(ch, n) }
            }
            "part" -> {
                val ch = FlashbackChatApi.normalizeChannel(o.optString("channel"))
                val n = o.optString("nick")
                listeners.forEach { it.onPart(ch, n) }
            }
            "pong", "open", "close", "error", "notice", "disconnected" -> {
                /* ignore / status already handled */
            }
        }
    }

    private fun maybeNotify(msg: FlashbackChatMessage) {
        if (msg.local) return
        if (msg.nick.isNotBlank() && msg.nick.equals(nick, ignoreCase = true)) return
        val watched = FlashbackChatPrefs.notifyChannels(appContext)
        if (msg.channel !in watched) return
        val focused = focusedChannel
        if (focused != null && focused == msg.channel) return
        FlashbackChatNotifier.onIncoming(appContext, msg)
    }

    private fun syncJoins() {
        refreshWantedJoins()
        val ws = socket ?: return
        val toJoin = wantJoin - joined
        val toPart = joined - wantJoin
        for (ch in toPart) {
            send(JSONObject().put("type", "part").put("channel", ch), ws)
            joined.remove(ch)
        }
        for (ch in toJoin) {
            send(JSONObject().put("type", "join").put("channel", ch), ws)
            joined.add(ch)
        }
    }

    /** Public refresh after prefs change. */
    fun refreshJoinsFromPrefs() {
        syncJoins()
    }

    private fun send(obj: JSONObject, ws: WebSocket? = socket) {
        try {
            ws?.send(obj.toString())
        } catch (_: Exception) {
        }
    }

    private fun scheduleReconnect() {
        if (!running.get() || reconnectPosted) return
        reconnectPosted = true
        val delay = (1000L * (1 shl reconnectAttempt.coerceAtMost(5))).coerceAtMost(30_000L)
        reconnectAttempt++
        main.postDelayed(
            {
                reconnectPosted = false
                if (running.get() && socket == null) connect()
            },
            delay,
        )
    }

    private fun emitState(state: String) {
        listeners.forEach { it.onState(state) }
    }

    companion object {
        private const val TAG = "FlashbackChat"

        @Volatile private var instance: FlashbackChatSession? = null

        fun get(context: Context, origin: String): FlashbackChatSession {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance
                    ?: FlashbackChatSession(
                        context.applicationContext,
                        origin.trim().trimEnd('/'),
                    ).also { instance = it }
            }
        }

        fun peek(): FlashbackChatSession? = instance
    }
}
