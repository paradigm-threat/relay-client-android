package com.relay.client

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat

/**
 * TV / D-pad focus chrome for the Flashback native shell.
 * Solid backgrounds wipe default focus rings — use these selectors instead.
 */
object FlashbackFocus {
    enum class Style {
        PRIMARY,
        SECONDARY,
        ROW,
        CHIP,
    }

    fun decorate(view: View, style: Style, contentDescription: String? = null) {
        view.isFocusable = true
        view.isClickable = true
        val res = when (style) {
            Style.PRIMARY -> com.relay.client.R.drawable.fb_focus_primary
            Style.SECONDARY -> com.relay.client.R.drawable.fb_focus_secondary
            Style.ROW -> com.relay.client.R.drawable.fb_focus_row
            Style.CHIP -> com.relay.client.R.drawable.fb_focus_chip
        }
        view.background = ContextCompat.getDrawable(view.context, res)
        if (!contentDescription.isNullOrBlank()) {
            view.contentDescription = contentDescription
        }
        view.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.03f else 1f
            v.scaleY = if (hasFocus) 1.03f else 1f
            if (hasFocus) ensureVisibleInScrollParents(v)
        }
        val min = (48 * view.resources.displayMetrics.density).toInt()
        if (view.minimumHeight < min) view.minimumHeight = min
    }

    fun seedFocus(preferred: View?) {
        preferred ?: return
        preferred.post {
            if (preferred.isAttachedToWindow) preferred.requestFocus()
        }
    }

    fun keepFocusedChildVisible(scroll: HorizontalScrollView) {
        scroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        scroll.isFocusable = false
    }

    fun keepFocusedChildVisible(scroll: ScrollView) {
        scroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        scroll.isFocusable = false
    }

    private fun ensureVisibleInScrollParents(focused: View) {
        var parent = focused.parent
        while (parent is ViewGroup) {
            when (parent) {
                is HorizontalScrollView -> {
                    val child = parent.getChildAt(0) as? ViewGroup ?: break
                    var x = 0
                    var v: View? = focused
                    while (v != null && v !== child) {
                        x += v.left
                        v = v.parent as? View
                    }
                    val target = (x - (parent.width - focused.width) / 2).coerceAtLeast(0)
                    parent.smoothScrollTo(target, 0)
                }
                is ScrollView -> {
                    val child = parent.getChildAt(0) as? ViewGroup ?: break
                    var y = 0
                    var v: View? = focused
                    while (v != null && v !== child) {
                        y += v.top
                        v = v.parent as? View
                    }
                    val target = (y - (parent.height - focused.height) / 2).coerceAtLeast(0)
                    parent.smoothScrollTo(0, target)
                }
            }
            parent = parent.parent
        }
    }
}
