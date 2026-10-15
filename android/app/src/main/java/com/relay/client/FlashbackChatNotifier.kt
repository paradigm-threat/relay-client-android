package com.relay.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Coalesced IRC activity notifications — one digest, quiet window between alerts.
 */
object FlashbackChatNotifier {
    const val CHANNEL_ID = "flashback-chat"
    const val NOTIFICATION_ID = 74
    const val EXTRA_OPEN_CHAT = "flashback.openChat"
    const val EXTRA_CHAT_CHANNEL = "flashback.chatChannel"

    data class CoalesceDecision(
        val title: String,
        val text: String,
        val alert: Boolean,
        val counts: Map<String, Int>,
        val preferredChannel: String,
    )

    fun onIncoming(context: Context, msg: FlashbackChatMessage) {
        val app = context.applicationContext
        ensureChannel(app)
        val now = System.currentTimeMillis()
        val quietMs = FlashbackChatPrefs.quietMinutes(app) * 60_000L
        val lastAlert = FlashbackChatPrefs.lastAlertMs(app)
        val counts = FlashbackChatPrefs.decodeDigestCounts(FlashbackChatPrefs.digestJson(app))
        counts[msg.channel] = (counts[msg.channel] ?: 0) + 1
        val preview =
            if (msg.kind == "action") {
                "* ${msg.nick} ${msg.text}"
            } else {
                "<${msg.nick}> ${msg.text}"
            }.take(160)
        val decision =
            decide(
                counts = counts,
                preview = preview,
                lastChannel = msg.channel,
                nowMs = now,
                lastAlertMs = lastAlert,
                quietMs = quietMs,
            )
        FlashbackChatPrefs.setDigestJson(app, FlashbackChatPrefs.encodeDigestCounts(decision.counts))
        if (decision.alert) {
            FlashbackChatPrefs.setLastAlertMs(app, now)
        }
        post(app, decision)
    }

    fun decide(
        counts: Map<String, Int>,
        preview: String,
        lastChannel: String,
        nowMs: Long,
        lastAlertMs: Long,
        quietMs: Long,
    ): CoalesceDecision {
        val alert = lastAlertMs <= 0L || (nowMs - lastAlertMs) >= quietMs
        val channels = counts.keys.sorted()
        val preferred = FlashbackChatApi.normalizeChannel(
            lastChannel.ifBlank {
                channels.firstOrNull() ?: FlashbackChatApi.DEFAULT_CHANNEL
            },
        )
        val total = counts.values.sum().coerceAtLeast(1)
        val title =
            when {
                channels.size <= 1 -> "Chat activity in #$preferred"
                else -> "Chat activity in ${channels.size} channels"
            }
        val summary =
            if (channels.size <= 1) {
                preview
            } else {
                val head = channels.take(2).joinToString(", ") { "#$it" }
                val more = channels.size - 2
                val scope =
                    if (more > 0) "$head + $more more"
                    else head
                "$scope · $total new · $preview"
            }
        return CoalesceDecision(
            title = title,
            text = summary.take(240),
            alert = alert,
            counts = counts,
            preferredChannel = preferred,
        )
    }

    fun clearDigest(context: Context) {
        val app = context.applicationContext
        FlashbackChatPrefs.clearDigest(app)
        val mgr = app.getSystemService(NotificationManager::class.java) ?: return
        mgr.cancel(NOTIFICATION_ID)
    }

    private fun post(context: Context, decision: CoalesceDecision) {
        val launch =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_CHAT, true)
                putExtra(EXTRA_CHAT_CHANNEL, decision.preferredChannel)
            }
        val piFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(context, 0, launch, piFlags)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(decision.title)
                .setContentText(decision.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(decision.text))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (!decision.alert) {
            builder.setSilent(true)
        }
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(NOTIFICATION_ID, builder.build())
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Flashback chat",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "IRC activity digests (coalesced)"
            },
        )
    }
}
