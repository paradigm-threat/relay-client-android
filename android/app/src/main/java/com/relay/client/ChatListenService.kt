package com.relay.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Opt-in foreground service that keeps the chat WebSocket alive in the background.
 */
class ChatListenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                FlashbackChatSession.peek()?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!FlashbackChatPrefs.listenWhileBackground(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureForeground()
                val origin =
                    intent?.getStringExtra(EXTRA_ORIGIN)?.trim().orEmpty()
                        .ifBlank { BuildConfig.RELAY_DEFAULT_ORIGIN }
                FlashbackChatSession.get(this, origin).start()
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        val channelId = "flashback-chat-listen"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Chat background listen",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val launch =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(FlashbackChatNotifier.EXTRA_OPEN_CHAT, true)
            }
        val piFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 1, launch, piFlags)
        val note =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }.setContentTitle("Flashback chat")
                .setContentText("Listening for IRC activity")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        startForeground(FOREGROUND_ID, note)
    }

    companion object {
        const val ACTION_STOP = "com.relay.client.chat.STOP"
        const val EXTRA_ORIGIN = "origin"
        private const val FOREGROUND_ID = 75

        fun start(context: Context, origin: String) {
            if (!FlashbackChatPrefs.listenWhileBackground(context)) return
            val i =
                Intent(context, ChatListenService::class.java).apply {
                    putExtra(EXTRA_ORIGIN, origin.trim().trimEnd('/'))
                }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i =
                Intent(context, ChatListenService::class.java).apply {
                    action = ACTION_STOP
                }
            try {
                context.startService(i)
            } catch (_: Exception) {
                context.stopService(Intent(context, ChatListenService::class.java))
            }
        }
    }
}
