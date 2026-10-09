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
import android.widget.LinearLayout

/**
 * Web-parity theatre atmosphere for the Flashback shell:
 * radial house glow, velvet curtains, breathing sconce lights, soft screen wash.
 * Pointer-events none — sits behind content; hide while fullscreen theater plays.
 */
object FlashbackTheaterChrome {

    private val house = Color.parseColor("#0d0505")
    private val warmSpot = Color.parseColor("#2a1810")
    private val velvet = Color.parseColor("#3a1018")
    private val velvetDeep = Color.parseColor("#1a080c")
    private val glowCenter = Color.parseColor("#2Ed4a574") // ~18% warm gold

    fun houseBackground(): GradientDrawable =
        GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(warmSpot, house)
            setGradientCenter(0.5f, 0.28f)
            gradientRadius = 1200f
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

    fun createBackdrop(context: Context): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            background = houseBackground().also {
                it.gradientRadius = dp(context, 900).toFloat()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

        // Velvet curtains (left / right)
        root.addView(curtain(context, left = true))
        root.addView(curtain(context, left = false))

        // Soft projector wash
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
                alpha = 0.85f
                startBreathe(this, 0.55f, 0.95f, 5500L, 0L)
            },
        )

        root.addView(wallLights(context, left = true))
        root.addView(wallLights(context, left = false))
        return root
    }

    private fun curtain(context: Context, left: Boolean): View {
        val orient = if (left) {
            GradientDrawable.Orientation.LEFT_RIGHT
        } else {
            GradientDrawable.Orientation.RIGHT_LEFT
        }
        return View(context).apply {
            background = GradientDrawable(
                orient,
                intArrayOf(velvetDeep, velvet, velvetDeep),
            ).also { it.alpha = 200 }
            layoutParams = FrameLayout.LayoutParams(
                dp(context, 96),
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (left) Gravity.START else Gravity.END,
            )
            alpha = 0.72f
        }
    }

    private fun wallLights(context: Context, left: Boolean): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                dp(context, 88),
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (left) Gravity.START else Gravity.END,
            ).apply {
                topMargin = dp(context, 72)
                bottomMargin = dp(context, 96)
            }
            setPadding(0, dp(context, 24), 0, dp(context, 24))
        }
        for (i in 0 until 3) {
            val sconce = ImageView(context).apply {
                setImageResource(com.relay.client.R.drawable.fb_sconce)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(
                    dp(context, 56),
                    dp(context, 104),
                ).apply {
                    weight = 1f
                    topMargin = dp(context, 8)
                    bottomMargin = dp(context, 8)
                }
                alpha = 0.55f
                if (!left) scaleX = -1f
                // Screen-like blend: slightly boost brightness via alpha breathe only.
                startBreathe(this, 0.42f, 0.78f, 7500L, i * 1600L)
            }
            col.addView(sconce)
        }
        return col
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
