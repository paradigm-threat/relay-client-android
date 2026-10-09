package com.clevertree.relayruntime.demo

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process gallery store for Android (parity with web `__relayGallery`).
 * Soft-retry schedule matches web: 250→500→1s→2s→4s, max 6, `_rg=` cache-bust.
 */
object RelayGalleryStore {
    data class Image(
        val src: String,
        val alt: String = "",
        val highResSrc: String = "",
    )

    private val images = CopyOnWriteArrayList<Image>()
    @Volatile private var overrideList: List<Image>? = null
    @Volatile private var index: Int = -1
    @Volatile private var open: Boolean = false
    @Volatile private var dialog: AlertDialog? = null

    fun register(src: String, alt: String = "", highResSrc: String = "") {
        if (src.isEmpty()) return
        if (images.any { it.src == src }) return
        images.add(Image(src, alt, highResSrc))
    }

    fun unregister(src: String) {
        images.removeAll { it.src == src }
    }

    fun openAt(src: String) {
        val list = activeList()
        val idx = list.indexOfFirst { it.src == src }
        if (idx < 0) {
            overrideList = listOf(Image(src))
            index = 0
        } else {
            overrideList = null
            index = idx
        }
        open = true
    }

    fun close() {
        open = false
        overrideList = null
        dialog?.dismiss()
        dialog = null
    }

    fun next() {
        val list = activeList()
        if (list.size <= 1) return
        index = (index + 1) % list.size
    }

    fun prev() {
        val list = activeList()
        if (list.size <= 1) return
        index = (index - 1 + list.size) % list.size
    }

    fun isOpen(): Boolean = open

    fun current(): Image? {
        if (!open) return null
        val list = activeList()
        if (index < 0 || index >= list.size) return null
        return list[index]
    }

    private fun activeList(): List<Image> = overrideList ?: images.toList()

    fun displaySrc(img: Image): String =
        if (img.highResSrc.isNotEmpty()) img.highResSrc else img.src

    /**
     * Show fullscreen-ish dialog for the current gallery item (soft-retry image load).
     */
    fun presentIfOpen(context: Context) {
        if (!open) {
            dialog?.dismiss()
            dialog = null
            return
        }
        val cur = current() ?: return
        dialog?.dismiss()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            setPadding(24, 48, 24, 48)
        }
        val header = TextView(context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            text = "${index + 1} / ${activeList().size} — ${cur.src}"
        }
        val imgView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = cur.alt.ifEmpty { "Gallery image" }
        }
        val nav = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val prevBtn = TextView(context).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            textSize = 32f
            setPadding(48, 16, 48, 16)
            setOnClickListener {
                prev()
                presentIfOpen(context)
            }
        }
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(48, 16, 48, 16)
            setOnClickListener { close() }
        }
        val nextBtn = TextView(context).apply {
            text = "›"
            setTextColor(Color.WHITE)
            textSize = 32f
            setPadding(48, 16, 48, 16)
            setOnClickListener {
                next()
                presentIfOpen(context)
            }
        }
        nav.addView(prevBtn)
        nav.addView(closeBtn)
        nav.addView(nextBtn)

        val caption = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            text = cur.alt
        }

        root.addView(header)
        root.addView(
            FrameLayout(context).apply {
                addView(
                    imgView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(nav)
        if (cur.alt.isNotEmpty()) root.addView(caption)

        loadWithRetry(imgView, displaySrc(cur))

        dialog = AlertDialog.Builder(context)
            .setView(root)
            .setOnCancelListener { close() }
            .create()
            .also { it.show() }
    }

    private fun loadWithRetry(view: ImageView, src: String, maxAttempts: Int = 6) {
        val base = RelayMediaUrls.resolve(src) ?: src
        fun cacheBust(url: String, attempt: Int): String {
            if (attempt <= 0) return url
            val hashIdx = url.indexOf('#')
            val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
            val noHash = if (hashIdx >= 0) url.substring(0, hashIdx) else url
            val qIdx = noHash.indexOf('?')
            val path = if (qIdx >= 0) noHash.substring(0, qIdx) else noHash
            val qs = if (qIdx >= 0) noHash.substring(qIdx + 1) else ""
            val parts = qs.split('&').filter { it.isNotEmpty() && !it.startsWith("_rg=") }.toMutableList()
            parts.add("_rg=$attempt")
            return path + "?" + parts.joinToString("&") + hash
        }
        fun backoffMs(i: Int): Long = when {
            i <= 0 -> 250L
            i == 1 -> 500L
            i == 2 -> 1000L
            i == 3 -> 2000L
            else -> 4000L
        }
        Thread {
            var attempt = 0
            while (attempt < maxAttempts) {
                val url = cacheBust(base, attempt)
                try {
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        view.post { view.setImageBitmap(bmp) }
                        return@Thread
                    }
                } catch (_: Exception) {
                }
                attempt += 1
                if (attempt >= maxAttempts) break
                try {
                    Thread.sleep(backoffMs(attempt - 1))
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.start()
    }
}
