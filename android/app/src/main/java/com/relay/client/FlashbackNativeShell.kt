package com.relay.client

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clevertree.relayruntime.demo.ExternalMediaIntents
import com.clevertree.relayruntime.demo.HevcCapability
import com.clevertree.relayruntime.demo.PlaybackErrors
import com.clevertree.relayruntime.demo.RelayMarkdownView
import com.clevertree.relayruntime.demo.RelayMediaUrls
import com.clevertree.relayruntime.demo.RelayVideoPlayerView

/**
 * Flashback flavor UI without CleverScript: library → title → ownership → ExoPlayer.
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

    private var projects: List<FlashbackProject> = emptyList()
    private var selected: FlashbackProject? = null
    private var playerView: RelayVideoPlayerView? = null
    private var inTheater: Boolean = false
    private var pendingUpdate: RemoteAppVersion? = null
    private var updateCheckStarted: Boolean = false
    private var bannerDismissedThisSession: Boolean = false

    init {
        setBackgroundColor(house)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        RelayMediaUrls.fetchBaseUrl = origin.trim().trimEnd('/')
        showLoading("Loading library…")
        Thread {
            try {
                val list = FlashbackCatalog.fetch(origin)
                main.post {
                    projects = list
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

    fun onConfigurationChanged() {
        // Fullscreen theater uses MATCH_PARENT — no rebuild (avoids restarting the stream).
    }

    private fun clear() {
        removeAllViews()
        playerView = null
        inTheater = false
    }

    private fun showLoading(msg: String) {
        clear()
        addView(
            ProgressBar(context).apply {
                layoutParams = LayoutParams(dp(48), dp(48), Gravity.CENTER)
            },
        )
        addView(
            TextView(context).apply {
                text = msg
                setTextColor(creamMuted)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
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
                        val list = FlashbackCatalog.fetch(origin)
                        main.post {
                            projects = list
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
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
        selected = null
        val col = column()
        col.addView(heading("Flashback Repository"))
        col.addView(body("Own the original. Then improve it."))
        updateBanner()?.let { col.addView(it) }
        col.addView(siteMenuRow())
        col.addView(
            linkRow(
                "Lab" to { showPlaybackLab() },
                "Board" to { openSitePath("/board") },
                "Check for updates" to { maybeCheckForUpdates(silent = false) },
            ),
        )

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
        if (mixes.isEmpty() && kids.isEmpty()) {
            col.addView(body("No titles in the catalog yet."))
        }
        wrapScroll(col)
    }

    /** Site pages the web exposes as markdown — rendered via [RelayMarkdownView]. */
    private fun siteMenuRow(): LinearLayout =
        linkRow(
            "Setup" to { showMarkdownDoc("Setup", "content/setup.md") },
            "Legal" to { showMarkdownDoc("Ownership", "content/legal.md") },
            "Motivation" to { showMarkdownDoc("Motivation", "content/motivation.md") },
            "Terms" to { showMarkdownDoc("Terms", "content/terms.md") },
            "Request" to { showMarkdownDoc("Request downloads", "content/request.md") },
        )

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
        }
    }

    private fun showTitle(p: FlashbackProject) {
        selected = p
        clear()

        val movieAck = FlashbackOwnership.hasAck(context, FlashbackOwnership.movieKey(p.slug))
        if (movieAck) {
            val stream = p.masterStreamUrl(origin)
            if (!stream.isNullOrBlank()) {
                showTheater(p, stream)
                return
            }
        }

        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()

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
        if (p.blurb.isNotBlank()) {
            col.addView(
                RelayMarkdownView(context).apply {
                    setMarkdown(p.blurb)
                    setPadding(0, 0, 0, dp(10))
                },
            )
        }

        if (!movieAck) {
            col.addView(sectionTitle("CONFIRM"))
            col.addView(
                body(
                    "I confirm I own a legal copy of ${p.originalTitle.ifBlank { p.title }} before I play its edit.",
                ),
            )
            col.addView(
                primaryButton("OK — I confirm") {
                    FlashbackHits.acknowledgeTitle(context, origin, p.slug)
                    showTitle(p)
                },
            )
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
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enterImmersive()

        val root = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }

        val player = RelayVideoPlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onPlaybackFailed = { /* card already shown inside player */ }
        }
        playerView = player
        root.addView(player)

        root.addView(
            TextView(context).apply {
                text = "← Library"
                setTextColor(gold)
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.parseColor("#990d0505"))
                isClickable = true
                setOnClickListener { showLibrary() }
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

        addView(root)
        player.bindSrc(stream, autoPlay = true)
    }

    /**
     * Device / error-overlay sandbox for APK compatibility checks (also documented at /android-lab).
     */
    private fun showPlaybackLab() {
        clear()
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
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
        }
        playerView = player
        col.addView(player)

        col.addView(
            primaryButton("Simulate decode error") {
                player.showSimulatedError(PlaybackErrors.Kind.DECODER)
            },
        )
        col.addView(
            secondaryButton("Simulate network error") {
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
        activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitImmersive()
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
        col.addView(mdView)
        wrapScroll(col)
        Thread {
            val text = FlashbackCatalog.fetchText(origin, path)
            main.post {
                if (text.isNullOrBlank()) {
                    mdView.setPlainFallback("Could not load $path. Check your connection.")
                } else {
                    mdView.setMarkdown(text)
                }
            }
        }.start()
    }

    private fun openSitePath(path: String) {
        val base = origin.trim().trimEnd('/')
        val rel = if (path.startsWith("/")) path else "/$path"
        val uri = Uri.parse("$base$rel")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open $rel", Toast.LENGTH_SHORT).show()
        }
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
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            addView(col)
        }
        addView(scroll)
    }

    private fun column(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(40))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
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
