package com.relay.client

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
 * Site pages live in a Material [DrawerLayout] (permanent rail on wide layouts).
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
    private val drawerLayout: DrawerLayout
    private val permanentDrawer: Boolean

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

    init {
        setBackgroundColor(house)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        RelayMediaUrls.fetchBaseUrl = origin.trim().trimEnd('/')

        permanentDrawer = resources.configuration.smallestScreenWidthDp >= 720
        drawerLayout = DrawerLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(house)
            setScrimColor(Color.parseColor("#99000000"))
        }
        contentHost.layoutParams = DrawerLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        drawerLayout.addView(contentHost)

        val drawerPanel = buildNavDrawer()
        drawerPanel.layoutParams = DrawerLayout.LayoutParams(
            dp(300),
            ViewGroup.LayoutParams.MATCH_PARENT,
            GravityCompat.START,
        )
        drawerLayout.addView(drawerPanel)
        addView(drawerLayout)

        bugFooter = buildBugReportFooter()
        addView(
            bugFooter,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        if (permanentDrawer) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN)
            drawerLayout.setScrimColor(Color.TRANSPARENT)
        }

        refreshBugFooterVisibility()
        showLoading("Loading library…")
        Thread {
            try {
                val library = FlashbackCatalog.fetch(origin)
                main.post {
                    applyLibrary(library)
                    showLibrary()
                    maybeCheckForUpdates(silent = true)
                }
            } catch (e: Exception) {
                main.post {
                    showError(e.message ?: "Could not load library")
                    maybeCheckForUpdates(silent = true)
                }
            }
        }.start()
    }

    /** Close the sliding drawer on back (phones); permanent rail stays open. */
    fun onBackPressed(): Boolean {
        if (!permanentDrawer && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        return false
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
        setDrawerEnabled(true)
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        if (permanentDrawer) {
            drawerLayout.setDrawerLockMode(
                if (enabled) DrawerLayout.LOCK_MODE_LOCKED_OPEN else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            )
            return
        }
        if (!enabled) drawerLayout.closeDrawer(GravityCompat.START)
        drawerLayout.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
        )
    }

    private fun openDrawer() {
        if (!permanentDrawer) drawerLayout.openDrawer(GravityCompat.START)
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
        val col = column()
        col.addView(heading("Flashback Repository"))
        col.addView(body(msg))
        col.addView(
            primaryButton("Try again") {
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
            },
        )
        wrapScroll(col)
    }

    private fun showLibrary() {
        clear()
        currentScreen = "library"
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        selected = null
        refreshBugFooterVisibility()
        val col = column()
        col.addView(heading("Flashback Repository"))
        col.addView(body("Own the original. Then improve it."))
        updateBanner()?.let { col.addView(it) }
        col.addView(siteMenuRow())

        val showing = FlashbackCatalog.resolveNowShowing(projects, nowShowingSlugs)
        if (showing.isNotEmpty()) {
            col.addView(sectionTitle("Now Showing"))
            col.addView(nowShowingRow(showing))
        }

        val mixes = projects.filter { it.category == "FanMixes" }
        val kids = projects.filter { it.category == "KidFriendly" }
        if (mixes.isNotEmpty()) {
            col.addView(sectionTitle("FanMixes"))
            for (p in mixes) col.addView(projectRow(p))
        }
        if (kids.isNotEmpty()) {
            col.addView(sectionTitle("KidFriendly"))
            for (p in kids) col.addView(projectRow(p))
        }
        if (showing.isEmpty() && mixes.isEmpty() && kids.isEmpty()) {
            col.addView(body("No titles in the catalog yet."))
        }
        wrapScroll(col)
    }

    /** Horizontal poster strip — web Now Showing carousel analogue. */
    private fun nowShowingRow(items: List<FlashbackProject>): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        for (p in items) {
            row.addView(nowShowingCard(p))
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
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
            isClickable = true
            isFocusable = true
            setOnClickListener { showTitle(p) }
            val poster = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(cardW, posterH)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#2a1018"))
                contentDescription = p.title
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
                },
            )
        }
    }

    /** Compact in-page shortcuts (full list is in the navigation drawer). */
    private fun siteMenuRow(): LinearLayout =
        linkRow(
            "Setup" to { showMarkdownDoc("Setup for playback", "content/setup.md") },
            "Legal" to { showMarkdownDoc("Ownership policy", "content/legal.md") },
            "Why" to { showMarkdownDoc("Why we’re doing this", "content/motivation.md") },
            "Request" to { showMarkdownDoc("Request downloads", "content/request.md") },
        )

    private fun buildNavDrawer(): View {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(drawerBg)
            isFillViewport = true
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        col.addView(
            TextView(context).apply {
                text = "Flashback"
                setTextColor(gold)
                textSize = 20f
                typeface = Typeface.SERIF
                setPadding(0, 0, 0, dp(4))
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
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onDrawerItem(item) }
                },
            )
        }
        scroll.addView(col)
        return scroll
    }

    private fun onDrawerItem(item: FlashbackSiteContent.NavItem) {
        if (!permanentDrawer) drawerLayout.closeDrawer(GravityCompat.START)
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
            setPadding(0, 0, 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        if (!permanentDrawer) {
            bar.addView(
                TextView(context).apply {
                    text = "☰"
                    setTextColor(gold)
                    textSize = 22f
                    setPadding(0, dp(4), dp(16), dp(4))
                    contentDescription = "Open menu"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openDrawer() }
                },
            )
        }
        return bar
    }

    private fun projectRow(p: FlashbackProject): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { showTitle(p) }
            addView(
                TextView(context).apply {
                    text = p.title
                    setTextColor(gold)
                    textSize = 17f
                    typeface = Typeface.SERIF
                },
            )
            if (p.blurb.isNotBlank()) {
                addView(
                    RelayMarkdownView(context).apply {
                        setMarkdown(p.blurb)
                        maxLines = 3
                        setPadding(0, dp(2), 0, 0)
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
        val stream = p.masterStreamUrl(origin)

        val col = column()
        col.addView(
            TextView(context).apply {
                text = "← Library"
                setTextColor(gold)
                textSize = 15f
                isClickable = true
                setOnClickListener { showLibrary() }
                setPadding(0, 0, 0, dp(12))
            },
        )
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

        if (!movieAck) {
            col.addView(sectionTitle("CONFIRM"))
            col.addView(
                body(
                    "I confirm I own a legal copy of ${p.originalTitle.ifBlank { p.title }} before I play its edit.",
                ),
            )
            col.addView(
                body(
                    "Same house rule fanedit communities have used for years: the edit is for people who already have the movie.",
                ),
            )
            col.addView(
                primaryButton("OK — I confirm") {
                    FlashbackHits.acknowledgeTitle(context, origin, p.slug)
                    // Stay on the movie page — do not autoplay (web: gate unlocks chrome; Play is separate).
                    showTitle(p)
                },
            )
            col.addView(
                linkRow(
                    "Ownership policy" to { showMarkdownDoc("Ownership policy", "content/legal.md") },
                    "Terms" to { showMarkdownDoc("Terms", "content/terms.md") },
                ),
            )
        } else if (!stream.isNullOrBlank()) {
            col.addView(sectionTitle("Watch"))
            col.addView(
                primaryButton("Play") {
                    showTheater(p, stream)
                },
            )
            col.addView(
                secondaryButton("Open in external player") {
                    if (!ExternalMediaIntents.open(context, stream)) {
                        Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            col.addView(
                secondaryButton("Get VLC") {
                    if (!ExternalMediaIntents.openVlcPlayStore(context)) {
                        Toast.makeText(context, "Could not open Play Store", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else if (p.hasStreamMedia) {
            col.addView(body("Feature media is catalogued but the stream URL could not be built."))
        } else {
            col.addView(body("Feature media is not catalogued for streaming yet."))
        }

        col.addView(siteMenuRow())
        wrapScroll(col)
    }

    /**
     * Full-bleed landscape theater — player fills the window; chrome is overlay only.
     * Playback errors use [PlaybackErrors] card inside [RelayVideoPlayerView] (not Toast).
     */
    private fun showTheater(p: FlashbackProject, stream: String) {
        clear()
        inTheater = true
        selected = p
        currentScreen = "theater"
        setDrawerEnabled(false)
        refreshBugFooterVisibility()
        // Lock landscape before attaching the player so the first bind is not torn down mid-prepare.
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enterImmersive()

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }

        val player = RelayVideoPlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onPlaybackFailed = { line -> lastPlaybackError = line }
        }
        playerView = player
        root.addView(player)

        root.addView(
            TextView(context).apply {
                text = "← Back"
                setTextColor(gold)
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.parseColor("#990d0505"))
                isClickable = true
                setOnClickListener { showTitle(p) }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ).apply {
                    topMargin = dp(8)
                    marginStart = dp(8)
                }
            },
        )

        root.addView(
            TextView(context).apply {
                text = "External"
                setTextColor(cream)
                textSize = 13f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.parseColor("#990d0505"))
                isClickable = true
                setOnClickListener {
                    if (!ExternalMediaIntents.open(context, stream)) {
                        Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
                    }
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = dp(8)
                    marginEnd = dp(8)
                }
            },
        )

        contentHost.addView(root)
        // Bind after the view is attached so PlayerView has a surface; autoplay via playWhenReady.
        player.post {
            if (!player.released) player.bindSrc(stream, autoPlay = true)
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
        col.addView(
            TextView(context).apply {
                text = "← Library"
                setTextColor(gold)
                textSize = 15f
                isClickable = true
                setOnClickListener { showLibrary() }
                setPadding(0, 0, 0, dp(12))
            },
        )
        col.addView(heading("Playback lab"))
        col.addView(
            body(
                "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). " +
                    report.summaryLine(),
            ),
        )
        col.addView(body("API ${report.apiLevel}. In-app play needs HEVC hardware; otherwise use external player."))
        updateBanner()?.let { col.addView(it) }
        col.addView(
            secondaryButton("Check for updates") {
                maybeCheckForUpdates(silent = false)
            },
        )
        if (report.decoderNames.isNotEmpty()) {
            col.addView(sectionTitle("Decoders"))
            col.addView(body(report.decoderNames.joinToString("\n")))
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
                primaryButton("Try sample stream (${sample.title})") {
                    player.bindSrc(sampleUrl, autoPlay = true)
                },
            )
            col.addView(
                secondaryButton("Open sample externally") {
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
        wrapScroll(col)
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
        col.addView(
            TextView(context).apply {
                text = "← Back"
                setTextColor(gold)
                textSize = 15f
                isClickable = true
                setOnClickListener {
                    val sel = selected
                    if (sel != null) showTitle(sel) else showLibrary()
                }
                setPadding(0, 0, 0, dp(12))
            },
        )
        col.addView(heading(title))
        val mdView = RelayMarkdownView(context)
        mdView.setPlainFallback("Loading…")
        col.addView(mdView)
        wrapScroll(col)
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
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showBugReportForm() }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(
                TextView(context).apply {
                    text = "✕"
                    setTextColor(creamMuted)
                    textSize = 18f
                    contentDescription = "Dismiss bug report bar"
                    setPadding(dp(12), dp(4), dp(8), dp(4))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        bugFooterDismissedThisSession = true
                        FlashbackBugReport.markFooterDismissed(context)
                        refreshBugFooterVisibility()
                    }
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
        col.addView(
            TextView(context).apply {
                text = "← Back"
                setTextColor(gold)
                textSize = 15f
                isClickable = true
                setOnClickListener {
                    val sel = selected
                    if (sel != null) showTitle(sel) else showLibrary()
                }
                setPadding(0, 0, 0, dp(12))
            },
        )
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
        wrapScroll(col)
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
        val apkUrl = FlashbackAppUpdate.resolveApkUrl(origin, remote)
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

    private fun wrapScroll(col: LinearLayout) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(house)
            addView(topBar())
            addView(
                ScrollView(context).apply {
                    isFillViewport = true
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
            setBackgroundColor(Color.parseColor("#3a1820"))
            setOnClickListener { onClick() }
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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
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
                        setPadding(0, 0, dp(16), 0)
                        isClickable = true
                        setOnClickListener { action() }
                    },
                )
            }
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
