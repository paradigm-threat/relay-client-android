package com.relay.client

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

/**
 * Web-parity theatre atmosphere for the Flashback shell:
 * vertical-tiling wall texture + left/right sconce columns, soft screen wash.
 * Pointer-events none — sits behind content; hide while fullscreen theater plays.
 */
object FlashbackTheaterChrome {

    private val house = Color.parseColor("#0d0505")
    private val glowCenter = Color.parseColor("#2Ed4a574") // ~18% warm gold

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

    fun createBackdrop(context: Context): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            background = tiledDrawable(context, com.relay.client.R.drawable.fb_wall_texture)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

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

        root.addView(wallColumn(context, left = true))
        root.addView(wallColumn(context, left = false))
        return root
    }

    private fun wallColumn(context: Context, left: Boolean): View {
        val res =
            if (left) {
                com.relay.client.R.drawable.fb_wall_sconce_left
            } else {
                com.relay.client.R.drawable.fb_wall_sconce_right
            }
        return View(context).apply {
            background = tiledDrawable(context, res)
            layoutParams = FrameLayout.LayoutParams(
                dp(context, 96),
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (left) Gravity.START else Gravity.END,
            )
            alpha = 0.95f
            startBreathe(this, 0.82f, 1f, 7500L, if (left) 0L else 1600L)
        }
    }

    private fun tiledDrawable(context: Context, resId: Int): BitmapDrawable {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
        return BitmapDrawable(context.resources, bitmap).apply {
            setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
            gravity = Gravity.FILL_HORIZONTAL or Gravity.TOP
        }
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
