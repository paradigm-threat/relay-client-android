package com.relay.client

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Web-parity theatre atmosphere for the Flashback shell:
 * full-bleed cover backdrop (mobile/tablet/desktop/wide via resource qualifiers)
 * plus a soft screen wash. Hide while fullscreen theater plays.
 *
 * Theme ids match web `flashback.theaterTheme.v1`: velvet (default) / classic.
 */
object FlashbackTheaterChrome {

    private val house = Color.parseColor("#0d0505")
    private val glowCenter = Color.parseColor("#1Fd4a574") // ~12% warm gold
    private const val TAG_BACKDROP_IMAGE = "fb_theater_bg_image"

    fun houseBackground(): GradientDrawable =
        GradientDrawable().apply {
            colors = intArrayOf(house, house)
        }

    fun sidebarBackground(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#2a1218"),
                Color.parseColor("#1a0a0c"),
                Color.parseColor("#120608"),
            ),
        )

    fun contentScrim(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#E60d0505"),
                Color.parseColor("#B30d0505"),
                Color.parseColor("#CC0d0505"),
            ),
        )

    fun backdropDrawableRes(theme: String): Int =
        when (FlashbackTheaterPrefs.normalize(theme)) {
            FlashbackTheaterPrefs.THEME_CLASSIC -> R.drawable.fb_theater_bg_classic
            else -> R.drawable.fb_theater_bg_velvet
        }

    fun createBackdrop(context: Context): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(house)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

        val theme = FlashbackTheaterPrefs.theme(context)
        root.addView(
            ImageView(context).apply {
                tag = TAG_BACKDROP_IMAGE
                setImageResource(backdropDrawableRes(theme))
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )

        // Soft projector wash (matches web .fb-screen-glow)
        root.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(glowCenter, Color.TRANSPARENT)
                    setGradientCenter(0.5f, 0.5f)
                    gradientRadius = dp(context, 420).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    dp(context, 640),
                    dp(context, 280),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply { topMargin = dp(context, 48) }
                alpha = 0.7f
                startBreathe(this, 0.45f, 0.85f, 5500L, 0L)
            },
        )

        return root
    }

    /** Apply persisted (or explicit) theme to an existing backdrop root. */
    fun applyTheme(backdropRoot: View, theme: String? = null) {
        val ctx = backdropRoot.context
        val t = theme?.let { FlashbackTheaterPrefs.normalize(it) } ?: FlashbackTheaterPrefs.theme(ctx)
        val image = findBackdropImage(backdropRoot) ?: return
        image.setImageResource(backdropDrawableRes(t))
    }

    private fun findBackdropImage(root: View): ImageView? {
        if (root is ImageView && root.tag == TAG_BACKDROP_IMAGE) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findBackdropImage(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun startBreathe(view: View, from: Float, to: Float, durationMs: Long, delayMs: Long) {
        ObjectAnimator.ofFloat(view, View.ALPHA, from, to).apply {
            duration = durationMs
            startDelay = delayMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
}
