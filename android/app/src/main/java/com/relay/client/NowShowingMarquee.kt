package com.relay.client

import android.content.ContentResolver
import android.os.Handler
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView

/**
 * Continuous slow rightward scroll of a duplicated Now Showing strip.
 *
 * Content must be laid out as [set A][set A] (same cards twice). When scrollX
 * reaches the width of the first set, we jump back by that width so the loop
 * is seamless — same idea as the web LCD marquee (`translateX(-50%)`).
 *
 * Pauses while the user touches or holds DPAD focus in the strip; resumes after
 * a short idle. Disabled when system animator scale is 0 (reduced motion).
 */
class NowShowingMarquee(
    private val scroll: HorizontalScrollView,
    private val firstSetWidthPx: () -> Int,
    private val main: Handler,
    private val pxPerTick: Int = DEFAULT_PX_PER_TICK,
    private val tickMs: Long = DEFAULT_TICK_MS,
    private val resumeIdleMs: Long = DEFAULT_RESUME_IDLE_MS,
) {
    companion object {
        /** ~40 px/s — a typical poster (~220px) takes ~5–6s to pass. */
        const val DEFAULT_PX_PER_TICK = 2
        const val DEFAULT_TICK_MS = 50L
        const val DEFAULT_RESUME_IDLE_MS = 4_000L

        fun animationsEnabled(resolver: ContentResolver): Boolean {
            return try {
                Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) > 0f
            } catch (_: Exception) {
                true
            }
        }

        /** Keep scrollX inside the first copy when content is duplicated. */
        fun wrapScrollX(scrollX: Int, firstSetWidthPx: Int): Int {
            val half = firstSetWidthPx
            if (half <= 0) return scrollX.coerceAtLeast(0)
            var x = scrollX
            while (x >= half) x -= half
            while (x < 0) x += half
            return x
        }
    }

    private var running = false
    private var userPaused = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running || userPaused) return
            val half = firstSetWidthPx()
            if (half <= 0) {
                main.postDelayed(this, tickMs)
                return
            }
            val x = wrapScrollX(scroll.scrollX + pxPerTick, half)
            scroll.scrollTo(x, 0)
            main.postDelayed(this, tickMs)
        }
    }

    private val resume = Runnable {
        if (!running) return@Runnable
        userPaused = false
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun start() {
        if (running) return
        running = true
        userPaused = false
        main.removeCallbacks(tick)
        main.removeCallbacks(resume)
        main.postDelayed(tick, 800L)
    }

    fun stop() {
        running = false
        userPaused = false
        main.removeCallbacks(tick)
        main.removeCallbacks(resume)
    }

    fun pauseForUser() {
        if (!running) return
        userPaused = true
        main.removeCallbacks(tick)
        main.removeCallbacks(resume)
        main.postDelayed(resume, resumeIdleMs)
    }

    fun attachInteractionListeners() {
        scroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                -> pauseForUser()
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> pauseForUser()
            }
            false
        }
        scroll.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && isDescendant(scroll, newFocus)) {
                pauseForUser()
            }
        }
    }

    private fun isDescendant(root: View, child: View): Boolean {
        var v: View? = child
        while (v != null) {
            if (v === root) return true
            val p = v.parent
            v = p as? View
        }
        return false
    }
}
