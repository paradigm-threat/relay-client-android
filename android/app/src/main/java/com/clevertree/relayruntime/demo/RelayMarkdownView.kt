package com.clevertree.relayruntime.demo

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Reusable markdown TextView for native Android shells (no themed-styler / CleverScript).
 * Same IR goals as `@clevertree/markdown` on web — headings, lists, links, emphasis —
 * painted with Markwon so Setup/Legal/blurbs stay editable as `.md` on the origin.
 */
class RelayMarkdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    companion object {
        private val DEFAULT_BODY = Color.parseColor("#c9b896")
        private val DEFAULT_LINK = Color.parseColor("#e8c872")
    }

    var bodyColor: Int = DEFAULT_BODY
        set(value) {
            field = value
            setTextColor(value)
        }

    var linkColor: Int = DEFAULT_LINK
        set(value) {
            field = value
            setLinkTextColor(value)
        }

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .linkColor(DEFAULT_LINK)
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(
                        floatArrayOf(1.6f, 1.35f, 1.15f, 1.05f, 1f, 0.95f),
                    )
            }
        })
        .build()

    init {
        setTextColor(bodyColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.3f)
        setLinkTextColor(linkColor)
    }

    fun setMarkdown(md: String) {
        markwon.setMarkdown(this, md.trim())
    }

    fun setPlainFallback(text: String) {
        this.text = text
    }
}
