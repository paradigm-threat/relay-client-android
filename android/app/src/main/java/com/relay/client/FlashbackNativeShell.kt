package com.relay.client

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.clevertree.relayruntime.demo.ExternalMediaIntents
import com.clevertree.relayruntime.demo.HevcCapability
import com.clevertree.relayruntime.demo.PlaybackErrors
import com.clevertree.relayruntime.demo.RelayMarkdownView
import com.clevertree.relayruntime.demo.RelayMediaUrls
import com.clevertree.relayruntime.demo.RelayVideoPlayerView
import java.net.URL

/**
 * Flashback flavor UI without CleverScript: home (Now Showing) → title → ownership → Play.
 * Affirmation unlocks theater chrome only; playback starts when the user taps Play.
 * Mobile: sliding [DrawerLayout]. Widescreen / TV: docked left menu (open by default, closable).
 */
class FlashbackNativeShell(
    context: Context,
    private val origin: String,
) : FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val house = Color.parseColor("#0d0505")
    private val cream = Color.parseColor("#f5e6be")
    private val creamMuted = Color.parseColor("#c9b896")
    private val gold = Color.parseColor("#e8c872")
    private val drawerBg = Color.parseColor("#1a0a0c")

    private val contentHost = FrameLayout(context)
    private val drawerPanel: View
    /** TV / wide desktop: docked sidebar. Phones keep a sliding drawer. */
    private val wideLayout: Boolean
    private val drawerLayout: DrawerLayout?
    private val sidebarHost: FrameLayout?
    /** Widescreen only — menu open by default, user can hide it. */
    private var sidebarOpen: Boolean = true
    private var navChromeEnabled: Boolean = true

    private var projects: List<FlashbackProject> = emptyList()
    private var nowShowingSlugs: List<String> = emptyList()
    private var selected: FlashbackProject? = null
    private var playerView: RelayVideoPlayerView? = null
    private var inTheater: Boolean = false
    private var pendingUpdate: RemoteAppVersion? = null
    private var updateCheckStarted: Boolean = false
    private var bannerDismissedThisSession: Boolean = false
    private var currentScreen: String = "library"
    private var lastPlaybackError: String? = null
    private var bugFooterDismissedThisSession: Boolean = false
    private val bugFooter: View
    private val theatreBackdrop: View

    init {
        background = FlashbackTheaterChrome.houseBackground()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        RelayMediaUrls.fetchBaseUrl = origin.trim().trimEnd('/')

        wideLayout = isWideLayout(resources.configuration)
        contentHost.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        contentHost.setBackgroundColor(Color.TRANSPARENT)
        drawerPanel = buildNavDrawer()

        // Atmosphere behind chrome (curtains + sconces + screen glow) — web fb-backdrop.
        theatreBackdrop = FlashbackTheaterChrome.createBackdrop(context)
        addView(theatreBackdrop)

        if (wideLayout) {
            drawerLayout = null
            val side = FrameLayout(context).apply {
                background = FlashbackTheaterChrome.sidebarBackground()
                layoutParams = LinearLayout.LayoutParams(
                    dp(280),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(
                    drawerPanel,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                // Right edge hairline against content.
                addView(
                    View(context).apply {
                        setBackgroundColor(Color.parseColor("#66e8c872"))
                        layoutParams = FrameLayout.LayoutParams(
                            dp(1),
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.END,
                        )
                    },
                )
            }
            sidebarHost = side
            contentHost.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f,
            )
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
                addView(side)
                addView(contentHost)
            }
            addView(row)
            applySidebarVisibility()
        } else {
            sidebarHost = null
            val dl = DrawerLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
                setScrimColor(Color.parseColor("#99000000"))
            }
            contentHost.layoutParams = DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            dl.addView(contentHost)
            drawerPanel.layoutParams = DrawerLayout.LayoutParams(
                dp(300),
                ViewGroup.LayoutParams.MATCH_PARENT,
                GravityCompat.START,
            )
            dl.addView(drawerPanel)
            dl.addDrawerListener(
                object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerOpened(drawerView: View) {
                        contentHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        FlashbackFocus.seedFocus(findFirstFocusable(drawerPanel))
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        contentHost.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    }
                },
            )
            drawerLayout = dl
            addView(dl)
        }

        bugFooter = buildBugReportFooter()
        addView(
            bugFooter,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        refreshBugFooterVisibility()
        showLoading("Loading library…")
        // Warm Kubo when the user already has Keep-offline pins (or always start in background
        // after catalog load so first Play is faster).
        if (IpfsOfflineStore(context).all().isNotEmpty()) {
            IpfsSession.startService(context)
        }
        Thread {
            try {
                val library = FlashbackCatalog.fetch(origin)
                main.post {
                    applyLibrary(library)
                    showLibrary()
                    maybeCheckForUpdates(silent = true)
                    IpfsSession.startService(context)
                }
            } catch (e: Exception) {
                main.post {
                    showError(e.message ?: "Could not load library")
                    maybeCheckForUpdates(silent = true)
                }
            }
        }.start()
    }

    /**
     * Pop one navigation step. Returns true if consumed (do not finish Activity).
     * Only library root (drawer closed) returns false so Back may leave the app.
     */
    fun onBackPressed(): Boolean {
        val dl = drawerLayout
        if (dl != null && dl.isDrawerOpen(GravityCompat.START)) {
            dl.closeDrawer(GravityCompat.START)
            return true
        }
        if (inTheater || currentScreen == "theater") {
            leaveTheater()
            return true
        }
        return when (currentScreen) {
            "title" -> {
                showLibrary()
                true
            }
            "markdown", "lab", "bug-report" -> {
                leaveOverlay()
                true
            }
            "library" -> false
            else -> {
                // Loading / error / unknown → return to library instead of exiting.
                if (projects.isNotEmpty()) {
                    showLibrary()
                    true
                } else {
                    false
                }
            }
        }
    }

    /** Same path as theater Back chip and system Back from theater. */
    private fun leaveTheater() {
        // Reclaim unpinned ephemeral blocks if the on-device repo is over budget.
        Thread {
            try {
                IpfsSession.maybeGcIfOverBudget(context)
            } catch (_: Exception) {
            }
        }.start()
        val p = selected
        if (p != null) showTitle(p) else showLibrary()
    }

    private fun leaveOverlay() {
        val sel = selected
        if (sel != null) showTitle(sel) else showLibrary()
    }

    private fun applyLibrary(library: FlashbackLibrary) {
        projects = library.projects
        nowShowingSlugs = library.nowShowingSlugs
    }

    fun onConfigurationChanged() {
        // Fullscreen theater uses MATCH_PARENT — no rebuild (avoids restarting the stream).
    }

    private fun clear() {
        playerView?.releasePlayer()
        playerView = null
        contentHost.removeAllViews()
        inTheater = false
        theatreBackdrop.visibility = View.VISIBLE
        setDrawerEnabled(true)
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        navChromeEnabled = enabled
        if (wideLayout) {
            if (!enabled) {
                sidebarHost?.visibility = View.GONE
            } else {
                applySidebarVisibility()
            }
            return
        }
        val dl = drawerLayout ?: return
        if (!enabled) dl.closeDrawer(GravityCompat.START)
        dl.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
        )
    }

    private fun openDrawer() {
        if (wideLayout) {
            sidebarOpen = true
            applySidebarVisibility()
            FlashbackFocus.seedFocus(findFirstFocusable(drawerPanel))
            return
        }
        drawerLayout?.openDrawer(GravityCompat.START)
    }

    private fun toggleSidebar() {
        if (!wideLayout || !navChromeEnabled) return
        sidebarOpen = !sidebarOpen
        applySidebarVisibility()
        if (sidebarOpen) {
            FlashbackFocus.seedFocus(findFirstFocusable(drawerPanel))
        }
    }

    private fun applySidebarVisibility() {
        val side = sidebarHost ?: return
        side.visibility = if (sidebarOpen && navChromeEnabled) View.VISIBLE else View.GONE
    }

    /** Fire TV often reports sw540dp at 320dpi — use TV flag / width, not only smallestWidth. */
    private fun isWideLayout(cfg: Configuration): Boolean {
        val uiType = cfg.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (cfg.screenWidthDp >= 900) return true
        if (cfg.smallestScreenWidthDp >= 720) return true
        return false
    }

    private fun showLoading(msg: String) {
        clear()
        contentHost.addView(
            ProgressBar(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
            },
        )
        contentHost.addView(
            TextView(context).apply {
                text = msg
                setTextColor(creamMuted)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ).apply {
                    topMargin = dp(72)
                }
            },
        )
    }

    private fun showError(msg: String) {
        clear()
        currentScreen = "error"
        val col = column()
        col.addView(heading("Flashback Repository"))
        col.addView(body(msg))
        val retry = primaryButton("Try again") {
            showLoading("Loading library…")
            Thread {
                try {
                    val library = FlashbackCatalog.fetch(origin)
                    main.post {
                        applyLibrary(library)
                        showLibrary()
                    }
                } catch (e: Exception) {
                    main.post { showError(e.message ?: "Could not load library") }
                }
            }.start()
        }
        col.addView(retry)
        wrapScroll(col, retry)
    }

    private fun showLibrary() {
        clear()
        currentScreen = "library"
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        selected = null
        refreshBugFooterVisibility()
        val col = column()
        // Brand lives in the header (topBar). App / Setup / Legal / Why / Request are in the menu.
        // First content matches the website: Now showing, then the rest of the library.
        var firstFocus: View? = null
        val showing = FlashbackCatalog.resolveNowShowing(projects, nowShowingSlugs)
        if (showing.isNotEmpty()) {
            col.addView(sectionTitle("Now showing"))
            val strip = nowShowingRow(showing)
            col.addView(strip)
            firstFocus = findFirstFocusable(strip)
        }
        updateBanner()?.let { col.addView(it) }

        val mixes = projects.filter { it.category == "FanMixes" }
        val kids = projects.filter { it.category == "KidFriendly" }
        if (mixes.isNotEmpty()) {
            col.addView(sectionTitle("FanMixes"))
            for (p in mixes) {
                val row = projectRow(p)
                col.addView(row)
                if (firstFocus == null) firstFocus = row
            }
        }
        if (kids.isNotEmpty()) {
            col.addView(sectionTitle("KidFriendly"))
            for (p in kids) {
                val row = projectRow(p)
                col.addView(row)
                if (firstFocus == null) firstFocus = row
            }
        }
        if (showing.isEmpty() && mixes.isEmpty() && kids.isEmpty()) {
            col.addView(body("No titles in the catalog yet."))
        }
        wrapScroll(col, firstFocus)
    }

    /**
     * Horizontal poster strip — Right wraps last→first (web-style loop for more titles).
     * Left from the first card leaves the strip (menu / Hide Menu), no wrap-around.
     */
    private fun nowShowingRow(items: List<FlashbackProject>): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val cards = ArrayList<View>(items.size)
        for (p in items) {
            val card = nowShowingCard(p).also { it.id = View.generateViewId() }
            cards.add(card)
            row.addView(card)
        }
        if (cards.size > 1) {
            for (i in cards.indices) {
                // Right always loops; Left only chains within the strip (not first→last).
                cards[i].nextFocusRightId = cards[(i + 1) % cards.size].id
                if (i > 0) {
                    cards[i].nextFocusLeftId = cards[i - 1].id
                }
            }
        }
        return object : HorizontalScrollView(context) {
            override fun focusSearch(focused: View?, direction: Int): View? {
                if (focused != null && cards.isNotEmpty()) {
                    val idx = cards.indexOf(focused)
                    if (idx >= 0) {
                        when (direction) {
                            View.FOCUS_RIGHT -> {
                                if (cards.size > 1) return cards[(idx + 1) % cards.size]
                            }
                            View.FOCUS_LEFT -> {
                                if (idx > 0) return cards[idx - 1]
                                // First card: defer to system (sidebar / Menu toggle).
                            }
                        }
                    }
                }
                return super.focusSearch(focused, direction)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && cards.isNotEmpty()) {
                    val focused = findFocus()
                    val idx = cards.indexOf(focused)
                    if (idx >= 0) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (cards.size > 1) {
                                    cards[(idx + 1) % cards.size].requestFocus()
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (idx > 0) {
                                    cards[idx - 1].requestFocus()
                                    return true
                                }
                                // First card: do not consume — let focus leave leftward.
                            }
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            FlashbackFocus.keepFocusedChildVisible(this)
            addView(row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun nowShowingCard(p: FlashbackProject): View {
        val cardW = dp(120)
        val posterH = dp(180)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(12)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { showTitle(p) }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, p.title)
            val poster = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(cardW, posterH)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#2a1018"))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(poster)
            loadPoster(poster, p.posterUrl(origin))
            addView(
                TextView(context).apply {
                    text = p.title
                    setTextColor(gold)
                    textSize = 13f
                    typeface = Typeface.SERIF
                    maxLines = 3
                    setPadding(0, dp(6), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
        }
    }

    private fun buildNavDrawer(): View {
        val scroll = ScrollView(context).apply {
            background = FlashbackTheaterChrome.sidebarBackground()
            isFillViewport = true
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        col.addView(
            ImageView(context).apply {
                setImageResource(com.relay.client.R.drawable.fb_logo_header)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                // 40% of former full-rail width (sidebar/drawer minus horizontal padding).
                val railInnerDp = (if (wideLayout) 280 else 300) - 32
                layoutParams = LinearLayout.LayoutParams(
                    dp((railInnerDp * 0.4f).toInt()),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) }
                contentDescription = "Flashback Repository"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            },
        )
        col.addView(
            TextView(context).apply {
                text = "Menu"
                setTextColor(creamMuted)
                textSize = 13f
                setPadding(0, 0, 0, dp(16))
            },
        )
        for (item in FlashbackSiteContent.drawerItems) {
            col.addView(
                TextView(context).apply {
                    text = item.title
                    setTextColor(cream)
                    textSize = 16f
                    setPadding(dp(8), dp(14), dp(8), dp(14))
                    setOnClickListener { onDrawerItem(item) }
                    FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, item.title)
                },
            )
        }
        FlashbackFocus.keepFocusedChildVisible(scroll)
        scroll.addView(col)
        return scroll
    }

    private fun onDrawerItem(item: FlashbackSiteContent.NavItem) {
        if (!wideLayout) drawerLayout?.closeDrawer(GravityCompat.START)
        when (val action = item.action) {
            is FlashbackSiteContent.NavAction.Library -> {
                selected = null
                showLibrary()
            }
            is FlashbackSiteContent.NavAction.Markdown ->
                showMarkdownDoc(action.title, action.path)
            is FlashbackSiteContent.NavAction.PlaybackLab -> showPlaybackLab()
            is FlashbackSiteContent.NavAction.Board -> showBugReportForm()
            is FlashbackSiteContent.NavAction.CheckUpdates -> maybeCheckForUpdates(silent = false)
        }
    }

    private fun topBar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Extra top pad so focus scale / stroke is not clipped under the status/overscan edge.
            setPadding(dp(4), dp(12), dp(4), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        if (!navChromeEnabled) return bar
        val menuChip =
            if (wideLayout) {
                val open = sidebarOpen
                TextView(context).apply {
                    text = if (open) "⟨ Menu" else "☰ Menu"
                    setTextColor(gold)
                    textSize = 16f
                    setPadding(dp(12), dp(10), dp(14), dp(10))
                    setOnClickListener { toggleSidebar() }
                    FlashbackFocus.decorate(
                        this,
                        FlashbackFocus.Style.CHIP,
                        if (open) "Hide menu" else "Show menu",
                    )
                }
            } else {
                TextView(context).apply {
                    text = "☰"
                    setTextColor(gold)
                    textSize = 22f
                    setPadding(dp(12), dp(10), dp(16), dp(10))
                    setOnClickListener { openDrawer() }
                    FlashbackFocus.decorate(this, FlashbackFocus.Style.CHIP, "Open menu")
                }
            }
        bar.addView(menuChip)
        // Brand optically centered: balance menu width with a matching end spacer.
        bar.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8), 0, dp(8), 0)
                addView(
                    TextView(context).apply {
                        text = "Flashback Repository"
                        setTextColor(gold)
                        textSize = 17f
                        typeface = Typeface.SERIF
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    },
                )
                addView(
                    TextView(context).apply {
                        text = "Reaffirming our First Amendment one Movie at a time"
                        setTextColor(creamMuted)
                        textSize = 11f
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(2), 0, 0)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    },
                )
            },
        )
        bar.addView(
            View(context).apply {
                // Measured after layout so spacer matches the menu chip width.
                layoutParams = LinearLayout.LayoutParams(dp(48), 1)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                menuChip.post {
                    layoutParams = LinearLayout.LayoutParams(menuChip.width.coerceAtLeast(dp(48)), 1)
                    requestLayout()
                }
            },
        )
        return bar
    }

    private fun projectRow(p: FlashbackProject): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            // Whole row is one tap / D-pad target — children must not steal clicks
            // (Markwon linkify installs a MovementMethod that eats blurb taps).
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnClickListener { showTitle(p) }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, p.title)
            addView(
                TextView(context).apply {
                    text = p.title
                    setTextColor(gold)
                    textSize = 17f
                    typeface = Typeface.SERIF
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            if (p.blurb.isNotBlank()) {
                addView(
                    RelayMarkdownView(context).apply {
                        setMarkdown(p.blurb)
                        maxLines = 3
                        setPadding(0, dp(2), 0, 0)
                        movementMethod = null
                        linksClickable = false
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                )
            }
            val stats = p.statsLine()
            if (stats.isNotBlank()) {
                addView(
                    TextView(context).apply {
                        text = stats
                        setTextColor(creamMuted)
                        textSize = 12f
                        setPadding(0, dp(4), 0, 0)
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                )
            }
        }
    }

    /**
     * Blurb + compact stats/change list via [RelayMarkdownView].
     * (Page ScrollView already scrolls long copy; web carousel scrolls inside the card.)
     * Change bullets load async from project README.
     */
    private fun titleCopyBlock(p: FlashbackProject): View {
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
        if (p.blurb.isNotBlank()) {
            wrap.addView(
                RelayMarkdownView(context).apply {
                    setMarkdown(p.blurb)
                    setPadding(0, 0, 0, dp(4))
                },
            )
        }
        val stats = p.statsLine()
        val meta = RelayMarkdownView(context).apply {
            textSize = 12f
            bodyColor = creamMuted
            setPadding(0, dp(6), 0, 0)
            if (stats.isNotBlank()) {
                setMarkdown("**Stats** — $stats")
            }
        }
        wrap.addView(meta)
        Thread {
            val readme = FlashbackCatalog.fetchText(origin, "${p.path}/README.md")
            val changes = FlashbackCatalog.changesBriefFromReadme(readme)
            if (changes.isBlank() && stats.isBlank()) return@Thread
            val md = buildString {
                if (stats.isNotBlank()) append("**Stats** — $stats")
                if (changes.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("**Changes**\n")
                    append(changes)
                }
            }
            main.post { meta.setMarkdown(md) }
        }.start()
        return wrap
    }

    private fun showTitle(p: FlashbackProject) {
        selected = p
        clear()
        currentScreen = "title"
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        refreshBugFooterVisibility()

        val movieAck = FlashbackOwnership.hasAck(context, FlashbackOwnership.movieKey(p.slug))
        val ladder = FlashbackStreamLadder.options(p, origin)
        val hevc = HevcCapability.probe()
        val preferredIdx = FlashbackStreamLadder.preferredIndex(
            ladder,
            maxHevcHeight = hevc.maxHeight,
            masterHeight = p.height,
        )
        val preferred = ladder.getOrNull(preferredIdx)

        val col = column()
        val backLib = TextView(context).apply {
            text = "← Library"
            setTextColor(gold)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(12))
            setOnClickListener { showLibrary() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, "Back to library")
        }
        col.addView(backLib)
        col.addView(heading(p.title))
        col.addView(titleCopyBlock(p))

        val posterUrl = p.posterUrl(origin)
        if (!posterUrl.isNullOrBlank()) {
            val poster = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(200), dp(300)).apply {
                    bottomMargin = dp(12)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#2a1018"))
                contentDescription = p.title
            }
            col.addView(poster)
            loadPoster(poster, posterUrl)
        }

        // Confirm / Ways share one slot so OK does not rebuild the page (which reset ScrollView to top).
        val watchSlot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        col.addView(watchSlot)

        var seed: View = backLib
        fun revealWays(focusFirst: Boolean): View? {
            watchSlot.removeAllViews()
            val first =
                fillWaysToWatch(
                    host = watchSlot,
                    p = p,
                    ladder = ladder,
                    preferredIdx = preferredIdx,
                    preferred = preferred,
                    hevc = hevc,
                )
            if (focusFirst && first != null) {
                // Focus the new controls without recreating the ScrollView (keeps scrollY).
                first.post { first.requestFocus() }
            }
            return first
        }

        if (!movieAck) {
            watchSlot.addView(sectionTitle("CONFIRM"))
            watchSlot.addView(
                body(
                    "I confirm I own a legal copy of ${p.originalTitle.ifBlank { p.title }} before I play its edit.",
                ),
            )
            watchSlot.addView(
                body(
                    "Same house rule fanedit communities have used for years: the edit is for people who already have the movie.",
                ),
            )
            val affirm = primaryButton("OK — I confirm") {
                FlashbackHits.acknowledgeTitle(context, origin, p.slug)
                revealWays(focusFirst = true)
            }
            watchSlot.addView(affirm)
            seed = affirm
            watchSlot.addView(
                linkRow(
                    "Ownership policy" to { showMarkdownDoc("Ownership policy", "content/legal.md") },
                    "Terms" to { showMarkdownDoc("Terms", "content/terms.md") },
                ),
            )
        } else {
            seed = revealWays(focusFirst = false) ?: backLib
        }

        wrapScroll(col, seed)
    }

    /**
     * Ways to watch panel into [host]. Returns the preferred focus seed (Play or Request).
     */
    private fun fillWaysToWatch(
        host: LinearLayout,
        p: FlashbackProject,
        ladder: List<FlashbackStreamLadder.Option>,
        preferredIdx: Int,
        preferred: FlashbackStreamLadder.Option?,
        hevc: HevcCapability.Report,
    ): View? {
        val catalogShowing =
            p.isShowing || nowShowingSlugs.any { it.equals(p.slug, ignoreCase = true) }
        val keepCid = (preferred?.cid ?: "").ifBlank { p.ipfsCid }.trim()
        val inCatalog = keepCid.length >= 8
        val streamUrl = preferred?.url.orEmpty()
        val hasStreamUrl = streamUrl.isNotEmpty()
        var seed: View? = null

        host.addView(sectionTitle("Ways to watch"))
        host.addView(
            body("Stream when this server has the file, download via IPFS when catalogued, or request Drive links."),
        )

        host.addView(sectionTitle("1. Stream it now"))
        host.addView(
            body("Works for Now showing titles, or when the site gateway already has the bytes."),
        )
        if (preferred != null &&
            !hevc.canDecodeHeight(p.height) &&
            ladder.any { it.kind != FlashbackStreamLadder.Kind.MASTER }
        ) {
            host.addView(
                body(
                    "This device’s HEVC decode tops out below the 4K master — " +
                        "Play uses ${preferred.label} when streaming is available.",
                ),
            )
        }

        val streamControls = ArrayList<View>()
        val streamStatus = body(
            when {
                !hasStreamUrl -> "No stream URL for this title yet."
                catalogShowing -> "Ready on this server (Now showing)."
                else -> "Checking if this server has the file…"
            },
        )
        var streamReady = catalogShowing && hasStreamUrl

        fun applyStreamEnabled(on: Boolean) {
            streamReady = on
            for (v in streamControls) {
                setActionEnabled(v, on)
            }
        }

        if (preferred != null) {
            val play = primaryButton("Play · ${preferred.label}") {
                if (!streamReady) {
                    Toast.makeText(
                        context,
                        "Not ready to stream on this server yet.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@primaryButton
                }
                showTheater(p, ladder, preferredIdx)
            }
            host.addView(play)
            streamControls.add(play)
            seed = play
            if (ladder.size > 1) {
                host.addView(
                    body("Default quality is picked for this TV/phone. Other rungs stay available to try."),
                )
                for ((idx, opt) in ladder.withIndex()) {
                    val mark = if (idx == preferredIdx) "★ " else ""
                    val q = secondaryButton("$mark${opt.label}") {
                        if (!streamReady) {
                            Toast.makeText(
                                context,
                                "Not ready to stream on this server yet.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@secondaryButton
                        }
                        showTheater(p, ladder, idx)
                    }
                    host.addView(q)
                    streamControls.add(q)
                }
            }
            val openBrowser = secondaryButton("Open in browser") {
                if (!streamReady) return@secondaryButton
                if (!ExternalMediaIntents.openInBrowser(context, preferred.url)) {
                    Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                }
            }
            host.addView(openBrowser)
            streamControls.add(openBrowser)
            val openExternal = secondaryButton("Open in external player") {
                if (!streamReady) return@secondaryButton
                val playUrl =
                    if (keepCid.isNotEmpty() && IpfsSession.isReady()) {
                        IpfsSession.gatewayUrl(keepCid)
                    } else {
                        preferred.url
                    }
                if (!ExternalMediaIntents.open(context, playUrl)) {
                    Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
                }
            }
            host.addView(openExternal)
            streamControls.add(openExternal)
        } else if (p.hasStreamMedia) {
            host.addView(body("Feature media is catalogued but the stream URL could not be built."))
        } else {
            host.addView(body("Feature media is not catalogued for streaming yet."))
        }
        host.addView(streamStatus)
        applyStreamEnabled(streamReady)

        if (hasStreamUrl && !catalogShowing) {
            Thread {
                val probe = FlashbackAssetStatus.probeUrl(streamUrl)
                main.post {
                    if (selected?.slug != p.slug || currentScreen != "title") return@post
                    if (probe.ready) {
                        streamStatus.text = "Ready on this server."
                        applyStreamEnabled(true)
                    } else {
                        streamStatus.text =
                            "Not on this server right now. Try Download (if listed) or Request Drive links."
                        applyStreamEnabled(false)
                    }
                }
            }.start()
        } else if (!hasStreamUrl) {
            applyStreamEnabled(false)
        }

        host.addView(sectionTitle("2. Download the file"))
        host.addView(
            body("Needs an IPFS CID in the catalog. Save via on-device Kubo when possible."),
        )
        val dlStatus = body(
            if (inCatalog) {
                "Ready to download via IPFS."
            } else {
                "This title is not in the IPFS catalog yet."
            },
        )
        val downloadControls = ArrayList<View>()

        fun applyDownloadEnabled(on: Boolean) {
            for (v in downloadControls) {
                setActionEnabled(v, on)
            }
        }

        if (inCatalog) {
            host.addView(
                body(
                    "Keep pins this title in the on-device Kubo node (Bitswap). " +
                        "Play can stream from the local gateway while blocks download.",
                ),
            )
            val store = IpfsOfflineStore(context)
            val keepBox =
                CheckBox(context).apply {
                    text = "Keep offline copy"
                    isChecked = store.isKept(keepCid)
                    setTextColor(cream)
                    textSize = 15f
                    FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, text.toString())
                    setOnCheckedChangeListener { _, checked ->
                        if (!isEnabled) return@setOnCheckedChangeListener
                        isEnabled = false
                        Thread {
                            val ok =
                                try {
                                    if (!IpfsSession.ensureStartedAndReady(context, 120_000L)) {
                                        false
                                    } else if (checked) {
                                        IpfsSession.ensureFetching(keepCid)
                                        val pinned = IpfsSession.pinAdd(keepCid)
                                        if (pinned) {
                                            store.keep(keepCid, p.title, p.slug)
                                        }
                                        pinned
                                    } else {
                                        IpfsSession.pinRm(keepCid)
                                        store.remove(keepCid)
                                        IpfsSession.repoGc()
                                        true
                                    }
                                } catch (_: Exception) {
                                    false
                                }
                            main.post {
                                isEnabled = true
                                if (!ok) {
                                    isChecked = store.isKept(keepCid)
                                    Toast.makeText(
                                        context,
                                        IpfsSession.lastError().ifBlank { "IPFS keep failed" },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (checked) "Kept offline (pinned)" else "Offline copy removed",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }.start()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4); bottomMargin = dp(8) }
                }
            host.addView(keepBox)
            downloadControls.add(keepBox)

            val saveBtn = secondaryButton("Save offline file") {
                Toast.makeText(
                    context,
                    "Starting IPFS… saving via local gateway",
                    Toast.LENGTH_SHORT,
                ).show()
                Thread {
                    val ready = IpfsSession.ensureStartedAndReady(context, 120_000L)
                    val src =
                        if (ready) {
                            IpfsSession.ensureFetching(keepCid)
                            IpfsSession.gatewayUrl(keepCid)
                        } else {
                            streamUrl.ifBlank { "$origin/ipfs/$keepCid" }
                        }
                    val dest = MovieDownloadService.fileFor(context, src)
                    main.post {
                        if (java.io.File(dest.path + ".done").isFile) {
                            Toast.makeText(
                                context,
                                "Already saved — play in-app or Open in external player",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@post
                        }
                        val intent = android.content.Intent(context, MovieDownloadService::class.java)
                            .putExtra(MovieDownloadService.EXTRA_URL, src)
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            Toast.makeText(
                                context,
                                if (ready) {
                                    "Saving offline from local IPFS gateway…"
                                } else {
                                    "IPFS unavailable — saving via site HTTPS…"
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Save failed: ${e.message ?: "unknown"}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }.start()
            }
            host.addView(saveBtn)
            downloadControls.add(saveBtn)
        }
        host.addView(dlStatus)
        applyDownloadEnabled(inCatalog)

        host.addView(sectionTitle("3. Get links by email"))
        host.addView(
            body("Send the request email — the automatic reply includes the shared Drive library."),
        )
        val requestDrive = secondaryButton("Request Drive links") {
            val url = origin.trim().trimEnd('/') + "/request"
            if (!ExternalMediaIntents.openInBrowser(context, url)) {
                Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }
        host.addView(requestDrive)
        if (seed == null) seed = requestDrive

        host.addView(
            secondaryButton("Get VLC") {
                if (!ExternalMediaIntents.openVlcPlayStore(context)) {
                    Toast.makeText(context, "Could not open Play Store", Toast.LENGTH_SHORT).show()
                }
            },
        )
        return seed
    }

    /**
     * Full-bleed landscape theater — player fills the window; chrome is overlay only.
     * Edge chrome (Back / External / CC) shows and hides with the Media3 controller.
     * Playback errors use [PlaybackErrors] card inside [RelayVideoPlayerView] (not Toast).
     */
    private fun showTheater(
        p: FlashbackProject,
        ladder: List<FlashbackStreamLadder.Option> = FlashbackStreamLadder.options(p, origin),
        startIndex: Int = 0,
    ) {
        clear()
        inTheater = true
        selected = p
        currentScreen = "theater"
        theatreBackdrop.visibility = View.GONE
        setDrawerEnabled(false)
        refreshBugFooterVisibility()
        // Lock landscape before attaching the player so the first bind is not torn down mid-prepare.
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enterImmersive()

        var ladderIndex = startIndex.coerceIn(0, (ladder.size - 1).coerceAtLeast(0))
        fun currentOption(): FlashbackStreamLadder.Option? = ladder.getOrNull(ladderIndex)
        fun catalogStream(): String = currentOption()?.url.orEmpty()
        if (catalogStream().isEmpty()) return

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }

        val ipfsStatus = TextView(context).apply {
            text = "IPFS…"
            setTextColor(creamMuted)
            textSize = 12f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.parseColor("#CC1a0a0c"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            visibility = View.GONE
        }

        val player = RelayVideoPlayerView(context).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onPlaybackFailed = { line -> lastPlaybackError = line }
            onRequestFallbackStream = { _ ->
                val next = FlashbackStreamLadder.nextIndex(ladder, ladderIndex)
                if (next < 0) {
                    null
                } else {
                    ladderIndex = next
                    Toast.makeText(
                        context,
                        "Switching to ${ladder[next].label}…",
                        Toast.LENGTH_SHORT,
                    ).show()
                    // Fallback also goes through local IPFS when CID present.
                    FlashbackStreamLadder.resolveLocalPlayUrl(ladder[next])
                        ?: ladder[next].url.takeIf { ladder[next].cid.isBlank() }
                }
            }
        }
        playerView = player
        root.addView(player)
        root.addView(ipfsStatus)

        // Edge chrome only (wrap_content) so empty screen areas still reach PlayerView taps.
        // Visibility mirrors the Media3 controller — not permanently over the picture.
        val chromeViews = ArrayList<View>(3)

        val back = TextView(context).apply {
            text = "← Back"
            setTextColor(gold)
            textSize = 14f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            includeFontPadding = false
            setOnClickListener { leaveTheater() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.CHIP, "Back to title")
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                topMargin = dp(8)
                marginStart = dp(8)
            }
            id = View.generateViewId()
        }
        chromeViews.add(back)
        root.addView(back)

        val topEnd = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            }
        }
        fun endChip(label: String, desc: String, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextColor(cream)
                textSize = 13f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                includeFontPadding = false
                setOnClickListener { onClick() }
                FlashbackFocus.decorate(this, FlashbackFocus.Style.CHIP, desc)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) }
                id = View.generateViewId()
            }
        val hasSubs = try {
            org.json.JSONArray(p.subtitlesJson).length() > 0
        } catch (_: Exception) {
            false
        }
        val ccChip = if (hasSubs) {
            endChip("CC", "Subtitles") {
                player.showController()
                player.showSubtitlePicker()
            }.also { topEnd.addView(it) }
        } else {
            null
        }
        val browserChip = endChip("Browser", "Open stream in browser") {
            val url = catalogStream().ifBlank {
                FlashbackStreamLadder.resolveLocalPlayUrl(currentOption()!!) ?: ""
            }
            if (url.isBlank() || !ExternalMediaIntents.openInBrowser(context, url)) {
                Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }
        topEnd.addView(browserChip)
        val externalChip = endChip("External", "Open in external player") {
            val url =
                FlashbackStreamLadder.resolveLocalPlayUrl(currentOption()!!)
                    ?: catalogStream()
            if (!ExternalMediaIntents.open(context, url)) {
                Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
            }
        }
        topEnd.addView(externalChip)
        chromeViews.add(topEnd)
        root.addView(topEnd)

        // D-pad: Back ← → player ← → CC / Browser / External
        back.nextFocusRightId = player.id
        player.nextFocusLeftId = back.id
        player.nextFocusUpId = back.id
        if (ccChip != null) {
            ccChip.nextFocusLeftId = player.id
            browserChip.nextFocusLeftId = ccChip.id
            externalChip.nextFocusLeftId = browserChip.id
            player.nextFocusRightId = ccChip.id
        } else {
            browserChip.nextFocusLeftId = player.id
            externalChip.nextFocusLeftId = browserChip.id
            player.nextFocusRightId = browserChip.id
        }

        // Save offline rides the same show/hide cycle as Back / CC / Browser / External.
        var saveChromeEnabled = false
        player.onControllerVisibilityChanged = { visible ->
            val v = if (visible) View.VISIBLE else View.GONE
            for (chip in chromeViews) chip.visibility = v
            if (saveChromeEnabled) {
                player.setSaveChromeVisible(visible)
            }
        }

        contentHost.addView(root)
        // Bind after Kubo is ready (when CID present) so Ranges hit the local gateway.
        player.post {
            if (player.released) return@post
            val opt = currentOption() ?: return@post
            val cid = opt.cid.trim()
            if (cid.isEmpty()) {
                player.bindSrc(opt.url, autoPlay = true, subtitlesJson = p.subtitlesJson)
                saveChromeEnabled = true
                player.setSaveChromeVisible(chromeViews.any { it.visibility == View.VISIBLE })
                player.requestPlayerFocus()
                return@post
            }
            ipfsStatus.visibility = View.VISIBLE
            ipfsStatus.text = "Starting IPFS (${IpfsSession.abiLabel()})…"
            Thread {
                val ready = IpfsSession.ensureStartedAndReady(context, 120_000L)
                if (!ready) {
                    main.post {
                        ipfsStatus.text =
                            "IPFS unavailable: ${IpfsSession.lastError().ifBlank { "node failed" }}"
                        lastPlaybackError = ipfsStatus.text.toString()
                        player.showSimulatedError(PlaybackErrors.Kind.NETWORK)
                    }
                    return@Thread
                }
                IpfsSession.ensureFetching(cid)
                val local = FlashbackStreamLadder.resolveLocalPlayUrl(opt)
                main.post {
                    if (player.released) return@post
                    if (local.isNullOrBlank()) {
                        ipfsStatus.text = "IPFS ready but play URL missing"
                        player.showSimulatedError(PlaybackErrors.Kind.NETWORK)
                        return@post
                    }
                    ipfsStatus.text =
                        "IPFS ${IpfsSession.abiMode()} · peers ${IpfsSession.peerCount()} · streaming via localhost"
                    ipfsStatus.postDelayed({
                        if (ipfsStatus.parent != null) ipfsStatus.visibility = View.GONE
                    }, 8_000)
                    player.bindSrc(local, autoPlay = true, subtitlesJson = p.subtitlesJson)
                    saveChromeEnabled = true
                    player.setSaveChromeVisible(chromeViews.any { it.visibility == View.VISIBLE })
                    player.requestPlayerFocus()
                    // Refresh peer count while watching.
                    main.postDelayed({ IpfsSession.refreshPeersAsync() }, 5_000)
                }
            }.start()
        }
    }

    /**
     * Device / error-overlay sandbox for APK compatibility checks (also documented at /android-lab).
     */
    private fun showPlaybackLab() {
        clear()
        currentScreen = "lab"
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        refreshBugFooterVisibility()
        val report = HevcCapability.probe()
        val col = column()
        val backLab = TextView(context).apply {
            text = "← Library"
            setTextColor(gold)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(12))
            setOnClickListener { leaveOverlay() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, "Back")
        }
        col.addView(backLab)
        col.addView(heading("Playback lab"))
        col.addView(
            body(
                "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). " +
                    report.summaryLine(),
            ),
        )
        col.addView(
            body(
                "API ${report.apiLevel}. In-app Play prefers 4K HEVC when this device can decode it; " +
                    "otherwise it auto-picks 1080p/720p H.264 when catalogued.",
            ),
        )
        updateBanner()?.let { col.addView(it) }
        col.addView(
            secondaryButton("Check for updates") {
                maybeCheckForUpdates(silent = false)
            },
        )
        col.addView(sectionTitle("Subtitles"))
        col.addView(
            body(
                "Captions turn on automatically when a title has subtitle files. " +
                    "On TV they use a larger outline style. Change these before Play.",
            ),
        )
        val subPrefs = FlashbackSubtitlePrefs(context)
        col.addView(
            CheckBox(context).apply {
                text = "Auto-enable first available subtitles"
                isChecked = subPrefs.autoEnable
                setTextColor(cream)
                textSize = 15f
                FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, text.toString())
                setOnCheckedChangeListener { _, checked -> subPrefs.autoEnable = checked }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
            },
        )
        col.addView(
            CheckBox(context).apply {
                text = "Prefer Enhanced / Citations track when present"
                isChecked = subPrefs.preferEnhanced
                setTextColor(cream)
                textSize = 15f
                FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, text.toString())
                setOnCheckedChangeListener { _, checked -> subPrefs.preferEnhanced = checked }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4); bottomMargin = dp(8) }
            },
        )
        if (report.decoderNames.isNotEmpty()) {
            col.addView(sectionTitle("Decoders"))
            col.addView(body(report.decoderNames.joinToString("\n")))
        }

        col.addView(sectionTitle("IPFS (embedded Kubo)"))
        val ipfsInfo = body(
            "Mode: ${IpfsSession.abiLabel()}\n" +
                "Ready: ${IpfsSession.isReady()}  peers: ${IpfsSession.peerCount()}\n" +
                "Node: ${IpfsSession.nodeId().ifBlank { "—" }}\n" +
                "Error: ${IpfsSession.lastError().ifBlank { "—" }}\n" +
                "Kept: ${IpfsOfflineStore(context).all().size} title(s)",
        )
        col.addView(ipfsInfo)
        col.addView(
            primaryButton("Start / refresh IPFS node") {
                ipfsInfo.text = "Starting Kubo…"
                Thread {
                    val ok = IpfsSession.ensureStartedAndReady(context, 120_000L)
                    IpfsSession.refreshPeersAsync()
                    Thread.sleep(800)
                    main.post {
                        ipfsInfo.text =
                            if (ok) {
                                "Mode: ${IpfsSession.abiLabel()}\n" +
                                    "Ready: true  peers: ${IpfsSession.peerCount()}\n" +
                                    "Node: ${IpfsSession.nodeId()}\n" +
                                    "Gateway: ${IpfsSession.gatewayBaseUrl()}"
                            } else {
                                "IPFS failed: ${IpfsSession.lastError()}"
                            }
                    }
                }.start()
            },
        )
        col.addView(
            secondaryButton("Gateway Range probe (sample CID)") {
                val sampleCid =
                    sampleStreamProject()?.ipfsCid?.trim().orEmpty().ifBlank {
                        // Tiny well-known empty dir CID as last resort for node liveness.
                        "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
                    }
                ipfsInfo.text = "Probing gateway Range for ${sampleCid.take(20)}…"
                Thread {
                    val ready = IpfsSession.ensureStartedAndReady(context, 120_000L)
                    if (ready) IpfsSession.ensureFetching(sampleCid)
                    val ok = ready && IpfsSession.gatewayRangeOk(sampleCid)
                    main.post {
                        ipfsInfo.text =
                            "Range probe ${if (ok) "OK" else "FAIL"} · ready=$ready · " +
                                "peers=${IpfsSession.peerCount()} · ${IpfsSession.lastError().ifBlank { "no error" }}"
                    }
                }.start()
            },
        )
        col.addView(
            secondaryButton("Stop IPFS node") {
                IpfsNodeService.stop(context)
                ipfsInfo.text = "IPFS stop requested"
            },
        )
        val kept = IpfsOfflineStore(context).all()
        if (kept.isNotEmpty()) {
            col.addView(sectionTitle("Kept offline"))
            col.addView(body(kept.joinToString("\n") { "• ${it.title.ifBlank { it.cid.take(16) }}" }))
        }

        val sample = sampleStreamProject()
        val sampleUrl = sample?.masterStreamUrl(origin)

        val player = RelayVideoPlayerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240),
            )
            onPlaybackFailed = { line -> lastPlaybackError = line }
        }
        playerView = player
        col.addView(player)

        col.addView(
            primaryButton("Simulate decode error") {
                val msg = PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER)
                lastPlaybackError = msg.bannerLine()
                player.showSimulatedError(PlaybackErrors.Kind.DECODER)
            },
        )
        col.addView(
            secondaryButton("Simulate network error") {
                val msg = PlaybackErrors.messageFor(PlaybackErrors.Kind.NETWORK)
                lastPlaybackError = msg.bannerLine()
                player.showSimulatedError(PlaybackErrors.Kind.NETWORK)
            },
        )
        if (sample != null && !sampleUrl.isNullOrBlank()) {
            col.addView(
                primaryButton("Try sample via IPFS localhost (${sample.title})") {
                    val cid = sample.ipfsCid.trim()
                    if (cid.isEmpty()) {
                        Toast.makeText(context, "Sample has no CID", Toast.LENGTH_SHORT).show()
                        return@primaryButton // leave onClick early
                    }
                    ipfsInfo.text = "Fetching $cid via Bitswap…"
                    Thread {
                        val ready = IpfsSession.ensureStartedAndReady(context, 120_000L)
                        if (!ready) {
                            main.post { ipfsInfo.text = "IPFS failed: ${IpfsSession.lastError()}" }
                            return@Thread
                        }
                        IpfsSession.ensureFetching(cid)
                        val local = IpfsSession.gatewayUrl(cid)
                        main.post {
                            ipfsInfo.text = "Playing $local"
                            player.bindSrc(local, autoPlay = true)
                        }
                    }.start()
                },
            )
            col.addView(
                secondaryButton("Open sample externally (catalog URL)") {
                    if (!ExternalMediaIntents.open(context, sampleUrl)) {
                        Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else {
            col.addView(body("No catalogued master stream yet — load the library online first."))
        }
        col.addView(
            linkRow(
                "Web lab notes" to { showMarkdownDoc("Android lab", "content/android-lab.md") },
            ),
        )
        wrapScroll(col, backLab)
    }

    /** Prefer a `showing` title (e.g. The Mummy Debugged) for Lab sample streams. */
    private fun sampleStreamProject(): FlashbackProject? {
        val withMedia = projects.filter { !it.masterStreamUrl(origin).isNullOrBlank() }
        return withMedia.firstOrNull { it.isShowing && it.slug.contains("mummy") }
            ?: withMedia.firstOrNull { it.isShowing }
            ?: withMedia.firstOrNull()
    }

    private fun showMarkdownDoc(title: String, path: String) {
        clear()
        currentScreen = "markdown"
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        refreshBugFooterVisibility()
        val col = column()
        val backMd = TextView(context).apply {
            text = "← Back"
            setTextColor(gold)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(12))
            setOnClickListener { leaveOverlay() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, "Back")
        }
        col.addView(backMd)
        col.addView(heading(title))
        val mdView = RelayMarkdownView(context)
        mdView.setPlainFallback("Loading…")
        col.addView(mdView)
        wrapScroll(col, backMd)
        Thread {
            val text = FlashbackSiteContent.loadMarkdown(context, origin, path)
            main.post {
                if (text.isNullOrBlank()) {
                    mdView.setPlainFallback(
                        "Could not load $path (offline copy missing). Check your connection.",
                    )
                } else {
                    mdView.setMarkdown(text)
                }
            }
        }.start()
    }

    private fun openSitePath(path: String) {
        val uri = if (path.startsWith("http://") || path.startsWith("https://")) {
            Uri.parse(path)
        } else {
            val base = origin.trim().trimEnd('/')
            val rel = if (path.startsWith("/")) path else "/$path"
            Uri.parse("$base$rel")
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildBugReportFooter(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#2a1018"))
            setPadding(dp(14), dp(12), dp(10), dp(12))
            elevation = dp(8).toFloat()
            addView(
                TextView(context).apply {
                    text = "Submit a bug report"
                    setTextColor(gold)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener { showBugReportForm() }
                    FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, "Submit a bug report")
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(
                TextView(context).apply {
                    text = "✕"
                    setTextColor(creamMuted)
                    textSize = 18f
                    setPadding(dp(12), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        bugFooterDismissedThisSession = true
                        FlashbackBugReport.markFooterDismissed(context)
                        refreshBugFooterVisibility()
                    }
                    FlashbackFocus.decorate(this, FlashbackFocus.Style.CHIP, "Dismiss bug report bar")
                },
            )
        }
    }

    private fun refreshBugFooterVisibility() {
        val show = !inTheater &&
            currentScreen != "bug-report" &&
            !bugFooterDismissedThisSession &&
            FlashbackBugReport.shouldShowFooter(context)
        bugFooter.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun boardPagePath(): String {
        val slug = selected?.slug?.trim().orEmpty()
        return if (slug.isNotEmpty()) "/$slug" else "/android"
    }

    private fun collectDebugSnapshot(): FlashbackBugReport.DebugSnapshot {
        return FlashbackBugReport.DebugSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            packageName = context.packageName,
            deviceModel = FlashbackBugReport.deviceModelLabel(),
            sdkInt = android.os.Build.VERSION.SDK_INT,
            origin = origin.trim().trimEnd('/'),
            screen = currentScreen,
            slug = selected?.slug,
            lastPlaybackError = lastPlaybackError,
            logcatSnippet = FlashbackBugReport.readLogcatSnippet(),
        )
    }

    private fun showBugReportForm() {
        clear()
        currentScreen = "bug-report"
        inTheater = false
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        setDrawerEnabled(true)
        refreshBugFooterVisibility()

        val col = column()
        val backBug = TextView(context).apply {
            text = "← Back"
            setTextColor(gold)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(12))
            setOnClickListener { leaveOverlay() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, "Back")
        }
        col.addView(backBug)
        col.addView(heading("Report a bug"))
        col.addView(
            body(
                "Posts publicly on the git comments branch (same Board as the website). " +
                    "A short Android debug snapshot is attached automatically.",
            ),
        )

        val titleInput = EditText(context).apply {
            hint = "Title"
            setTextColor(cream)
            setHintTextColor(creamMuted)
            setBackgroundColor(Color.parseColor("#1a0a0c"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setSingleLine(true)
        }
        col.addView(
            TextView(context).apply {
                text = "Title"
                setTextColor(creamMuted)
                textSize = 13f
                setPadding(0, dp(8), 0, dp(4))
            },
        )
        col.addView(titleInput)

        val bodyInput = EditText(context).apply {
            hint = "What happened?"
            setTextColor(cream)
            setHintTextColor(creamMuted)
            setBackgroundColor(Color.parseColor("#1a0a0c"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }
        col.addView(
            TextView(context).apply {
                text = "Message"
                setTextColor(creamMuted)
                textSize = 13f
                setPadding(0, dp(12), 0, dp(4))
            },
        )
        col.addView(bodyInput)

        val nameInput = EditText(context).apply {
            hint = "Optional"
            setTextColor(cream)
            setHintTextColor(creamMuted)
            setBackgroundColor(Color.parseColor("#1a0a0c"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setSingleLine(true)
        }
        col.addView(
            TextView(context).apply {
                text = "Name (optional)"
                setTextColor(creamMuted)
                textSize = 13f
                setPadding(0, dp(12), 0, dp(4))
            },
        )
        col.addView(nameInput)

        val status = TextView(context).apply {
            setTextColor(creamMuted)
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
        }
        val submit = primaryButton("Post publicly") {}
        submit.setOnClickListener {
            val title = titleInput.text?.toString().orEmpty()
            val message = bodyInput.text?.toString().orEmpty()
            val name = nameInput.text?.toString().orEmpty()
            submit.isEnabled = false
            status.setTextColor(creamMuted)
            status.text = "Posting…"
            Thread {
                val result = FlashbackBugReport.submit(
                    origin,
                    FlashbackBugReport.SubmitRequest(
                        title = title,
                        body = message,
                        name = name,
                        pagePath = boardPagePath(),
                        snapshot = collectDebugSnapshot(),
                    ),
                )
                main.post {
                    submit.isEnabled = true
                    when (result) {
                        is FlashbackBugReport.SubmitResult.Ok -> {
                            status.setTextColor(gold)
                            status.text =
                                "Posted. Thank you — it is public on the comments branch."
                            bodyInput.setText("")
                            titleInput.setText("")
                        }
                        is FlashbackBugReport.SubmitResult.ValidationError -> {
                            status.setTextColor(Color.parseColor("#e8a0a0"))
                            status.text = result.message
                        }
                        is FlashbackBugReport.SubmitResult.HttpError -> {
                            status.setTextColor(Color.parseColor("#e8a0a0"))
                            status.text =
                                "Could not post (HTTP ${result.status}: ${result.detail}). " +
                                    "This site’s publish node must accept PUT on branch comments."
                        }
                        is FlashbackBugReport.SubmitResult.NetworkError -> {
                            status.setTextColor(Color.parseColor("#e8a0a0"))
                            status.text =
                                "Could not post (${result.message}). Check your connection and try again."
                        }
                    }
                }
            }.start()
        }
        col.addView(submit)
        col.addView(status)
        col.addView(
            linkRow(
                "View public Board" to { openSitePath("/board?kind=bug") },
            ),
        )
        wrapScroll(col, submit)
    }

    private fun loadPoster(view: ImageView, src: String?) {
        val url = src?.trim().orEmpty()
        if (url.isEmpty()) return
        Thread {
            try {
                val bytes = URL(url).openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                main.post { view.setImageBitmap(bmp) }
            } catch (_: Exception) {
                /* poster is decorative */
            }
        }.start()
    }

    /**
     * Fetch `{origin}/downloads/flashback.json` and compare to [BuildConfig.VERSION_CODE].
     * Silent launch checks only refresh the library banner; manual checks show a dialog.
     */
    private fun maybeCheckForUpdates(silent: Boolean) {
        if (silent && updateCheckStarted) return
        if (silent) updateCheckStarted = true
        Thread {
            val remote = try {
                FlashbackAppUpdate.fetch(origin)
            } catch (_: Exception) {
                null
            }
            main.post {
                if (remote == null) {
                    if (!silent) {
                        AlertDialog.Builder(context)
                            .setTitle("Updates")
                            .setMessage("Could not reach the update check. Try again when online.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@post
                }
                val outdated = FlashbackAppUpdate.isOutdated(BuildConfig.VERSION_CODE, remote)
                pendingUpdate = if (outdated) remote else null
                if (!outdated) {
                    if (!silent) {
                        AlertDialog.Builder(context)
                            .setTitle("Up to date")
                            .setMessage(
                                "You’re on ${BuildConfig.VERSION_NAME} " +
                                    "(${BuildConfig.VERSION_CODE}). Latest is ${remote.versionName}.",
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@post
                }
                if (silent) {
                    if (FlashbackAppUpdate.shouldShowPrompt(context) &&
                        !bannerDismissedThisSession &&
                        selected == null &&
                        !inTheater
                    ) {
                        showLibrary()
                    }
                } else {
                    showUpdateDialog(remote)
                }
            }
        }.start()
    }

    private fun updateBanner(): View? {
        val remote = pendingUpdate ?: return null
        if (bannerDismissedThisSession) return null
        if (!FlashbackAppUpdate.shouldShowPrompt(context)) return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#2a1018"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
            addView(
                TextView(context).apply {
                    text = "Update available"
                    setTextColor(gold)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                },
            )
            addView(
                TextView(context).apply {
                    text =
                        "Installed ${BuildConfig.VERSION_NAME} → ${remote.versionName}. " +
                            "Install the latest APK from the site."
                    setTextColor(creamMuted)
                    textSize = 14f
                    setPadding(0, dp(4), 0, dp(8))
                    setLineSpacing(0f, 1.2f)
                },
            )
            addView(
                primaryButton("Install update") {
                    startUpdateInstall(remote)
                },
            )
            addView(
                secondaryButton("Not now") {
                    bannerDismissedThisSession = true
                    FlashbackAppUpdate.markDismissed(context)
                    showLibrary()
                },
            )
        }
    }

    private fun showUpdateDialog(remote: RemoteAppVersion) {
        AlertDialog.Builder(context)
            .setTitle("Update available")
            .setMessage(
                "A newer Flashback is available: ${remote.versionName} " +
                    "(${remote.versionCode}). You have ${BuildConfig.VERSION_NAME} " +
                    "(${BuildConfig.VERSION_CODE}).",
            )
            .setPositiveButton("Install update") { _, _ -> startUpdateInstall(remote) }
            .setNegativeButton("Not now") { _, _ ->
                bannerDismissedThisSession = true
                FlashbackAppUpdate.markDismissed(context)
                if (selected == null && !inTheater) showLibrary()
            }
            .show()
    }

    private fun startUpdateInstall(remote: RemoteAppVersion) {
        val act = activity() ?: return
        val apkUrl = FlashbackAppUpdate.resolveApkUrlForDevice(origin, remote)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !act.packageManager.canRequestPackageInstalls()
        ) {
            when (FlashbackAppUpdate.installApk(act, apkUrl)) {
                FlashbackAppUpdate.InstallResult.NEEDS_UNKNOWN_SOURCES -> {
                    AlertDialog.Builder(context)
                        .setTitle("Allow installs")
                        .setMessage(
                            "Turn on “Allow from this source” for Flashback, then tap Install update again.",
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
                FlashbackAppUpdate.InstallResult.FAILED -> openApkFallback(apkUrl)
                FlashbackAppUpdate.InstallResult.STARTED_INSTALLER -> Unit
            }
            return
        }
        Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = FlashbackAppUpdate.installApk(act, apkUrl)
            main.post {
                when (result) {
                    FlashbackAppUpdate.InstallResult.STARTED_INSTALLER -> Unit
                    FlashbackAppUpdate.InstallResult.NEEDS_UNKNOWN_SOURCES -> Unit
                    FlashbackAppUpdate.InstallResult.FAILED -> openApkFallback(apkUrl)
                }
            }
        }.start()
    }

    private fun openApkFallback(apkUrl: String) {
        if (!FlashbackAppUpdate.openApkUrl(context, apkUrl)) {
            AlertDialog.Builder(context)
                .setTitle("Update")
                .setMessage("Could not open the APK. Download it from Setup on the website.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun wrapScroll(col: LinearLayout, preferredFocus: View? = null) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
            // Soft scrim so type stays readable over curtains / sconces.
            background = FlashbackTheaterChrome.contentScrim()
            addView(topBar())
            addView(
                ScrollView(context).apply {
                    isFillViewport = true
                    FlashbackFocus.keepFocusedChildVisible(this)
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
                    addView(col)
                },
            )
        }
        // Extra bottom pad so the floating bug-report footer does not cover the last controls.
        col.setPadding(dp(18), dp(4), dp(18), dp(72))
        contentHost.addView(root)
        FlashbackFocus.seedFocus(preferredFocus)
    }

    private fun column(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(40))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun heading(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(gold)
            textSize = 22f
            typeface = Typeface.SERIF
            setPadding(0, 0, 0, dp(10))
        }

    private fun sectionTitle(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(gold)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
            letterSpacing = 0.08f
        }

    private fun body(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(creamMuted)
            textSize = 15f
            setPadding(0, 0, 0, dp(10))
            setLineSpacing(0f, 1.25f)
        }

    private fun primaryButton(label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(gold)
            setOnClickListener { onClick() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.PRIMARY, label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(cream)
            setOnClickListener { onClick() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.SECONDARY, label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        }

    /** Dim and defocus actions that are not available (catalog / Now showing / IPFS). */
    private fun setActionEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.42f
        view.isFocusable = enabled
        view.isFocusableInTouchMode = enabled
        if (view is Button) {
            view.isClickable = enabled
        }
    }

    private fun linkRow(vararg links: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            for ((label, action) in links) {
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(gold)
                        textSize = 15f
                        setPadding(dp(8), dp(10), dp(16), dp(10))
                        setOnClickListener { action() }
                        FlashbackFocus.decorate(this, FlashbackFocus.Style.ROW, label)
                    },
                )
            }
        }

    private fun findFirstFocusable(root: View): View? {
        if (root.isFocusable && root.visibility == View.VISIBLE) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirstFocusable(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun enterImmersive() {
        val act = activity() ?: return
        @Suppress("DEPRECATION")
        act.window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun exitImmersive() {
        val act = activity() ?: return
        @Suppress("DEPRECATION")
        act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun activity(): AppCompatActivity? = context as? AppCompatActivity

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
