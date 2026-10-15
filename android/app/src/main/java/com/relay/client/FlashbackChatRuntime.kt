package com.relay.client

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground-default IRC session: connect while app is in use; disconnect when idle
 * unless [FlashbackChatPrefs.listenWhileBackground] is enabled (then FGS).
 */
object FlashbackChatRuntime {
    private val bound = AtomicBoolean(false)
    @Volatile private var origin: String = BuildConfig.RELAY_DEFAULT_ORIGIN
    @Volatile private var appContext: Context? = null

    fun bind(context: Context, origin: String) {
        this.origin = origin.trim().trimEnd('/')
        this.appContext = context.applicationContext
        if (!bound.compareAndSet(false, true)) return
        val observer =
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    onAppForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    onAppBackground()
                }
            }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        // If already foreground when bound, start immediately.
        onAppForeground()
    }

    fun origin(): String = origin

    fun onNotifyPrefsChanged() {
        val ctx = appContext ?: return
        FlashbackChatSession.peek()?.refreshJoinsFromPrefs()
        if (FlashbackChatPrefs.listenWhileBackground(ctx)) {
            // Keep FGS in sync if already backgrounded with listen on.
        } else {
            ChatListenService.stop(ctx)
        }
    }

    private fun onAppForeground() {
        val ctx = appContext ?: return
        ChatListenService.stop(ctx)
        FlashbackChatSession.get(ctx, origin).start()
    }

    private fun onAppBackground() {
        val ctx = appContext ?: return
        if (FlashbackChatPrefs.listenWhileBackground(ctx) &&
            FlashbackChatPrefs.notifyChannels(ctx).isNotEmpty()
        ) {
            ChatListenService.start(ctx, origin)
        } else {
            FlashbackChatSession.peek()?.stop()
            ChatListenService.stop(ctx)
        }
    }
}
