package com.clevertree.relayruntime.demo

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.clevertree.relayruntime.RelayRuntime
import com.relay.client.FlashbackFocus
import com.relay.client.FlashbackSubtitlePrefs
import com.relay.client.FlashbackSubtitles
import com.relay.client.MovieDownloadService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Native ExoPlayer shell for `relay-video-player` VNodes and the Flashback native theater.
 * Feature MKVs stream over progressive HTTP (no SimpleCache thrash). HW HEVC only;
 * decode failures offer Open in external player.
 */
@OptIn(UnstableApi::class)
class RelayVideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "RelayVideoPlayer"
        const val CMD_PLAY = 1
        const val CMD_PAUSE = 2
        const val CMD_SEEK = 3

        @Volatile
        var anyPlaying: Boolean = false
    }

    @Volatile
    var released: Boolean = false

    /** Optional callback when native UI (not CleverScript) needs error messaging. */
    var onPlaybackFailed: ((String) -> Unit)? = null

    /**
     * When set, decoder/format failures ask the host for another stream URL before showing
     * the permanent error card. Return the next URL, or null to show the error overlay.
     */
    var onRequestFallbackStream: ((PlaybackErrors.Kind) -> String?)? = null

    /**
     * Fired when the Media3 transport controller shows or hides.
     * Theater overlays (Back / External / CC) should mirror this so they don't sit forever on the picture.
     */
    var onControllerVisibilityChanged: ((Boolean) -> Unit)? = null

    private val playerView: PlayerView
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var boundSrc: String? = null
    /** Src for which [applySubtitleAutoSelect] already ran (once per bind). */
    private var autoSubsAppliedFor: String? = null
    private var lastCommandSeq: Long = -1L
    private var lastPlayingProp: Boolean? = null
    private var lastSeekProp: Double? = null
    private var onRenderRequested: (() -> Unit)? = null
    private var propsRef: JSONObject? = null
    private val errorOverlay: FrameLayout
    private val errorTitle: TextView
    private val errorBody: TextView
    private val openExternalBtn: Button
    private val getVlcBtn: Button
    private val saveChrome: LinearLayout

    init {
        val mediaAudio = Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        // Progressive multi‑GB masters over IPFS Range windows: keep a deep ahead buffer so
        // brief gateway latency doesn't rebuffer (4K HEVC burns through buffer quickly).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 50_000,
                /* maxBufferMs */ 120_000,
                // Still start fairly soon, but after a stall wait for a healthier cushion.
                /* bufferForPlaybackMs */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 8_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(mediaAudio, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
        // Avoid a MediaSession controller / OEM media stack pausing us before the first frame.
        // Theater + Lab drive the player directly; session is optional for lockscreen artwork later.
        try {
            mediaSession = MediaSession.Builder(context, player).build()
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession unavailable", e)
        }

        playerView = PlayerView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
            useController = true
            isFocusable = true
            isFocusableInTouchMode = true
            controllerShowTimeoutMs = 3500
            controllerHideOnTouch = true
            setShowSubtitleButton(true)
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    onControllerVisibilityChanged?.invoke(visibility == VISIBLE)
                },
            )
            // Show spinner while progressive HTTPS masters connect (not only after playWhenReady).
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            keepScreenOn = true
            player = this@RelayVideoPlayerView.player
        }
        styleSubtitleView(playerView)
        addView(playerView)

        saveChrome = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            setPadding(dp(8), dp(8), dp(8), dp(8))
            // Starts hidden; native theater toggles with the Media3 controller.
            visibility = GONE
        }
        val saveBtn = Button(context).apply {
            text = "Save offline"
            setOnClickListener { confirmSave() }
            FlashbackFocus.decorate(this, FlashbackFocus.Style.PRIMARY, "Save offline")
        }
        saveChrome.addView(saveBtn)
        addView(saveChrome)
        // Full-bleed error card — never use Toast for long HEVC copy (truncates).
        errorTitle = TextView(context).apply {
            setTextColor(Color.parseColor("#e8c872"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        errorBody = TextView(context).apply {
            setTextColor(Color.parseColor("#f5e6be"))
            textSize = 15f
            setLineSpacing(0f, 1.25f)
            setPadding(0, 0, 0, dp(12))
        }
        openExternalBtn = Button(context).apply {
            text = PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER).primaryAction
            isAllCaps = false
            setOnClickListener { openBoundExternally() }
            FlashbackFocus.decorate(
                this,
                FlashbackFocus.Style.PRIMARY,
                PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER).primaryAction,
            )
        }
        getVlcBtn = Button(context).apply {
            text = PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER).secondaryAction
            isAllCaps = false
            setOnClickListener {
                ExternalMediaIntents.open(
                    context,
                    "https://play.google.com/store/apps/details?id=org.videolan.vlc",
                )
            }
            FlashbackFocus.decorate(
                this,
                FlashbackFocus.Style.SECONDARY,
                PlaybackErrors.messageFor(PlaybackErrors.Kind.DECODER).secondaryAction,
            )
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2a1218"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            }
            addView(errorTitle)
            addView(errorBody)
            addView(openExternalBtn)
            addView(getVlcBtn)
        }
        val cardScroll = ScrollView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            isFillViewport = false
            addView(card)
        }
        errorOverlay = FrameLayout(context).apply {
            visibility = GONE
            setBackgroundColor(Color.parseColor("#CC0d0505"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isClickable = true
            addView(cardScroll)
        }
        addView(errorOverlay)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (released) return
                anyPlaying = isPlaying
                keepScreenOn = isPlaying || player.playbackState == Player.STATE_BUFFERING
                Log.i(TAG, "isPlaying=$isPlaying state=${player.playbackState} ready=${player.playWhenReady}")
                invokeHandlerProp("onPlay", isPlaying)
                invokeHandlerProp("onPause", !isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (released) return
                val name = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "OTHER($state)"
                }
                Log.i(TAG, "state=$name playWhenReady=${player.playWhenReady} pos=${player.currentPosition}")
                if (state == Player.STATE_READY) applySubtitleAutoSelect(player.currentTracks)
                if (state == Player.STATE_ENDED) invokeHandlerProp("onEnded", true)
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (released) return
                applySubtitleAutoSelect(tracks)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (released) return
                Log.i(TAG, "playWhenReady=$playWhenReady reason=$reason state=${player.playbackState}")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "playback error code=${error.errorCode}", error)
                val msg = PlaybackErrors.classify(error.errorCode, error.message)
                if (msg.kind == PlaybackErrors.Kind.DECODER || msg.kind == PlaybackErrors.Kind.FORMAT) {
                    val next = onRequestFallbackStream?.invoke(msg.kind)
                    if (!next.isNullOrBlank() && next != boundSrc) {
                        Log.i(TAG, "falling back to lower rung after ${msg.kind}")
                        val keepSubs = propsRef?.let { propString(it, "subtitlesJson") }
                        bindSrc(next, autoPlay = true, subtitlesJson = keepSubs)
                        return
                    }
                }
                showPlaybackError(msg)
                invokeHandlerProp("onError", true)
                onPlaybackFailed?.invoke(msg.bannerLine())
            }
        })
    }

    /** Lab / tests: show the same overlay without a real ExoPlayer failure. */
    fun showSimulatedError(kind: PlaybackErrors.Kind = PlaybackErrors.Kind.DECODER) {
        showPlaybackError(PlaybackErrors.messageFor(kind))
    }

    fun bindSrc(
        src: String,
        autoPlay: Boolean = true,
        subtitlesJson: String? = null,
    ) {
        saveChrome.visibility = GONE
        val props = JSONObject()
            .put("src", src)
            .put("controls", true)
            .put("autoPlay", autoPlay)
        if (!subtitlesJson.isNullOrBlank() && subtitlesJson != "[]") {
            props.put("subtitlesJson", subtitlesJson)
        }
        bindProps(props) {}
    }

    /** CleverScript / hook path: show the offline-save chrome above the controller. */
    fun setSaveChromeVisible(visible: Boolean) {
        saveChrome.visibility = if (visible) VISIBLE else GONE
    }

    /** True when the bound media advertised at least one sidecar subtitle track. */
    fun hasSubtitleTracks(): Boolean {
        val json = propString(propsRef ?: return false, "subtitlesJson") ?: return false
        return try {
            JSONArray(json).length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Open Media3 text-track picker (Off + each sidecar / embedded text track). */
    fun showSubtitlePicker() {
        if (released) return
        try {
            TrackSelectionDialogBuilder(context, "Subtitles", player, C.TRACK_TYPE_TEXT)
                .setShowDisableOption(true)
                .setAllowAdaptiveSelections(false)
                .build()
                .show()
        } catch (e: Exception) {
            Log.w(TAG, "subtitle picker failed", e)
            Toast.makeText(context, "Subtitles unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    /** Keep theater edge chrome in sync: briefly re-show the transport bar. */
    fun showController() {
        if (released) return
        try {
            playerView.showController()
        } catch (_: Exception) {
        }
    }

    /** Seed D-pad focus onto the Media3 PlayerView (play/pause cluster). */
    fun requestPlayerFocus() {
        if (released) return
        FlashbackFocus.seedFocus(playerView)
    }

    private fun showPlaybackError(msg: PlaybackErrors.Message) {
        try {
            player.pause()
        } catch (_: Exception) {
        }
        playerView.useController = false
        errorTitle.text = msg.title
        errorBody.text = msg.body
        openExternalBtn.text = msg.primaryAction
        getVlcBtn.text = msg.secondaryAction
        errorOverlay.visibility = VISIBLE
        errorOverlay.bringToFront()
        FlashbackFocus.seedFocus(openExternalBtn)
    }

    private fun hidePlaybackError() {
        errorOverlay.visibility = GONE
        playerView.useController = true
    }

    private fun openBoundExternally() {
        val src = boundSrc ?: return
        if (!ExternalMediaIntents.open(context, src)) {
            Toast.makeText(context, PlaybackErrors.shortNoHandlerToast(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmSave() {
        val src = boundSrc ?: return
        val dest = MovieDownloadService.fileFor(context, src)
        if (File(dest.path + ".done").isFile) {
            Toast.makeText(context, "Already saved on this device", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val bytes = MovieDownloadService.probeLength(src)
            val free = MovieDownloadService.freeBytes(context)
            val sizeLabel = if (bytes != null && bytes > 0) formatBytes(bytes) else "unknown size"
            val freeLabel = formatBytes(free)
            post {
                val enough = bytes == null || bytes <= 0 || free > bytes
                android.app.AlertDialog.Builder(context)
                    .setTitle("Save offline")
                    .setMessage(
                        "This file is $sizeLabel. About $freeLabel is free in this app’s private storage. " +
                            "The copy stays until you delete it or clear the app’s data."
                    )
                    .setPositiveButton(if (enough) "Save" else "Not enough space", null)
                    .setNegativeButton("Cancel", null)
                    .create()
                    .also { dialog ->
                        dialog.setOnShowListener {
                            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                if (!enough) {
                                    dialog.dismiss()
                                    return@setOnClickListener
                                }
                                val intent = Intent(context, MovieDownloadService::class.java)
                                    .putExtra(MovieDownloadService.EXTRA_URL, src)
                                if (Build.VERSION.SDK_INT >= 26) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                dialog.dismiss()
                            }
                        }
                    }
                    .show()
            }
        }.start()
    }

    private fun formatBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val kb = n / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    fun bindProps(props: JSONObject, onRenderRequested: () -> Unit) {
        if (released) {
            Log.w(TAG, "bindProps ignored — player already released")
            return
        }
        this.onRenderRequested = onRenderRequested
        propsRef = props

        val controls = propBoolLoose(props, "controls") ?: true
        playerView.useController = controls

        val src = RelayMediaUrls.resolve(propString(props, "src"))
        if (src != null && src != boundSrc) {
            boundSrc = src
            autoSubsAppliedFor = null
            Log.i(TAG, "bind src=$src")
            hidePlaybackError()
            val local = MovieDownloadService.fileFor(context, src)
            val uri = if (local.isFile && local.length() > 0L && File(local.path + ".done").isFile) {
                Uri.fromFile(local)
            } else {
                Uri.parse(src)
            }
            val subPrefs = FlashbackSubtitlePrefs(context)
            val subs = subtitleConfigs(props, preferEnhanced = subPrefs.preferEnhanced)
            val itemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setSubtitleConfigurations(subs)
            // Hint Matroska for HTTPS masters so the progressive extractor binds quickly.
            if (uri.scheme != "file" && looksLikeMatroska(src)) {
                itemBuilder.setMimeType(MimeTypes.VIDEO_MATROSKA)
            }
            val item = itemBuilder.build()
            // Keep text tracks enabled up front; exact track pick runs in applySubtitleAutoSelect.
            if (subs.isNotEmpty() && subPrefs.autoEnable) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("en")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            } else if (!subPrefs.autoEnable) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            // Progressive HTTP for multi‑GB masters — no SimpleCache (avoids thrash).
            // Use bounded Range chunks: full GET / open-ended Range hang on Relay/IPFS edges.
            if (uri.scheme == "file") {
                player.setMediaItem(item)
            } else {
                val upstream = RangeEnforcingDataSource.Factory()
                player.setMediaSource(ProgressiveMediaSource.Factory(upstream).createMediaSource(item))
            }
            // Set playWhenReady BEFORE prepare — otherwise first BUFFERING lands paused (pause icon @ 0s).
            val wantAutoplay = propBoolLoose(props, "autoPlay") != false
            player.playWhenReady = wantAutoplay
            player.prepare()
            if (wantAutoplay) {
                player.play()
                // ColorOS / focus races can clear playWhenReady right after prepare — re-assert shortly.
                postDelayed({
                    if (!released && wantAutoplay && !player.playWhenReady && boundSrc == src) {
                        Log.w(TAG, "playWhenReady cleared after prepare — retrying play()")
                        player.playWhenReady = true
                        player.play()
                    }
                }, 400)
            }
            Log.i(TAG, "prepared autoplay=$wantAutoplay playWhenReady=${player.playWhenReady}")
            // Audio focus is owned by ExoPlayer (handleAudioFocus=true) — do not dual-request.
            invokeHandlerProp("onLoadedMetadata", true)
        }

        propBoolLoose(props, "muted")?.let { muted ->
            if (muted) player.volume = 0f
            else propNumber(props, "volume")?.let { player.volume = it.toFloat().coerceIn(0f, 1f) }
                ?: run { if (player.volume == 0f) player.volume = 1f }
        } ?: propNumber(props, "volume")?.let { player.volume = it.toFloat().coerceIn(0f, 1f) }
        propNumber(props, "playbackRate")?.let { player.setPlaybackSpeed(it.toFloat()) }
        propBoolLoose(props, "loop")?.let { player.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

        val playing = propBoolLoose(props, "playing")
        if (playing != null && playing != lastPlayingProp) {
            lastPlayingProp = playing
            if (playing) player.play() else player.pause()
        }

        val seek = propNumber(props, "currentTime")
        if (seek != null && (lastSeekProp == null || kotlin.math.abs(seek - lastSeekProp!!) > 0.25)) {
            lastSeekProp = seek
            player.seekTo((seek * 1000).toLong())
            invokeHandlerProp("onSeeked", true)
        }

        val cmdSeq = propNumber(props, "commandSeq")?.toLong()
        val cmd = propNumber(props, "command")?.toInt()
        val cmdArg = propNumber(props, "commandArg")
        if (cmdSeq != null && cmdSeq != lastCommandSeq) {
            lastCommandSeq = cmdSeq
            when (cmd) {
                CMD_PLAY -> player.play()
                CMD_PAUSE -> player.pause()
                CMD_SEEK -> if (cmdArg != null) player.seekTo((cmdArg * 1000).toLong())
            }
        }

        // Re-assert autoplay after other props (playing/seek/commands) so nothing leaves us paused @ 0.
        propBoolLoose(props, "autoPlay")?.let { auto ->
            if (auto && !player.playWhenReady) {
                Log.i(TAG, "re-assert playWhenReady after props")
                player.playWhenReady = true
                player.play()
            } else if (!auto) {
                player.playWhenReady = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Orientation / immersive theater briefly detaches views — never release here.
        if (!released) startTimeUpdates()
    }

    override fun onDetachedFromWindow() {
        stopTimeUpdates()
        anyPlaying = false
        super.onDetachedFromWindow()
    }

    /**
     * Release ExoPlayer + MediaSession. Call from the shell when leaving theater / clearing UI.
     * Do not release from [onDetachedFromWindow] — landscape theater detaches during rotation.
     */
    fun releasePlayer() {
        if (released) return
        released = true
        stopTimeUpdates()
        anyPlaying = false
        try {
            playerView.player = null
        } catch (_: Exception) {
        }
        try {
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    private val timeRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            val props = propsRef ?: return
            val handler = props.optJSONObject("onTimeUpdate") ?: return
            val id = handler.optInt("value", 0)
            if (id <= 0) {
                postDelayed(this, 250)
                return
            }
            try {
                RelayRuntime.invokeHandler(id)
                if (RelayRuntime.consumeRenderRequested()) onRenderRequested?.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "onTimeUpdate failed", e)
            }
            postDelayed(this, 250)
        }
    }

    private fun startTimeUpdates() {
        removeCallbacks(timeRunnable)
        postDelayed(timeRunnable, 250)
    }

    private fun stopTimeUpdates() {
        removeCallbacks(timeRunnable)
    }

    private fun invokeHandlerProp(key: String, condition: Boolean) {
        if (!condition) return
        val props = propsRef ?: return
        val handler = props.optJSONObject(key) ?: return
        val id = handler.optInt("value", 0)
        if (id <= 0) return
        try {
            RelayRuntime.invokeHandler(id)
            if (RelayRuntime.consumeRenderRequested()) onRenderRequested?.invoke()
        } catch (e: Exception) {
            Log.w(TAG, "$key handler failed", e)
        }
    }

    /**
     * Couch-readable captions on TV / wide displays; phone keeps ExoPlayer defaults.
     */
    private fun styleSubtitleView(pv: PlayerView) {
        val sub = pv.subtitleView ?: return
        val uiType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val tvOrWide = uiType == Configuration.UI_MODE_TYPE_TELEVISION ||
            resources.configuration.screenWidthDp >= 900
        if (!tvOrWide) return
        try {
            sub.setApplyEmbeddedFontSizes(false)
            sub.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            sub.setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.parseColor("#CC000000"),
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    Typeface.DEFAULT_BOLD,
                ),
            )
            sub.setBottomPaddingFraction(0.08f)
        } catch (e: Exception) {
            Log.w(TAG, "subtitleView style failed", e)
        }
    }

    /**
     * Once per [boundSrc]: honor [FlashbackSubtitlePrefs] — enable first/default text track,
     * or Enhanced / Citations when that preference is on.
     */
    private fun applySubtitleAutoSelect(tracks: Tracks) {
        val src = boundSrc ?: return
        if (autoSubsAppliedFor == src) return
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
        if (textGroups.isEmpty()) return
        autoSubsAppliedFor = src

        val prefs = FlashbackSubtitlePrefs(context)
        if (!prefs.autoEnable) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Log.i(TAG, "subtitles auto-enable off — text tracks disabled")
            return
        }

        data class Cand(val group: Tracks.Group, val index: Int, val label: String)
        val cands = ArrayList<Cand>()
        for (g in textGroups) {
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val label = listOfNotNull(f.label, f.language, f.id)
                    .joinToString(" ")
                    .ifBlank { "track-$i" }
                cands.add(Cand(g, i, label))
            }
        }
        if (cands.isEmpty()) return

        val pick = FlashbackSubtitles.preferredIndex(
            cands.map { it.label },
            preferEnhanced = prefs.preferEnhanced,
        ).coerceAtLeast(0)
        val chosen = cands[pick.coerceAtMost(cands.lastIndex)]
        val override = TrackSelectionOverride(
            chosen.group.mediaTrackGroup,
            listOf(chosen.index),
        )
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(override)
            .setPreferredTextLanguage("en")
            .setSelectUndeterminedTextLanguage(true)
            .build()
        Log.i(
            TAG,
            "subtitles auto-select label=${chosen.label} enhancedPref=${prefs.preferEnhanced}",
        )
    }

    private fun subtitleConfigs(
        props: JSONObject,
        preferEnhanced: Boolean,
    ): List<MediaItem.SubtitleConfiguration> {
        val json = propString(props, "subtitlesJson") ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val labels = ArrayList<String>()
            val builders = ArrayList<MediaItem.SubtitleConfiguration.Builder>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val src = RelayMediaUrls.resolve(t.optString("src")) ?: continue
                val mime = when {
                    src.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                    src.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
                    else -> MimeTypes.TEXT_VTT
                }
                val label = t.optString("label").ifBlank { "English" }
                labels.add(label)
                val b = MediaItem.SubtitleConfiguration.Builder(Uri.parse(src))
                    .setMimeType(mime)
                    .setLabel(label)
                t.optString("lang").takeIf { it.isNotEmpty() }?.let { b.setLanguage(it) }
                builders.add(b)
            }
            val prefer = FlashbackSubtitles.preferredIndex(labels, preferEnhanced)
            builders.mapIndexed { i, b ->
                if (i == prefer || (prefer < 0 && i == 0)) {
                    b.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                }
                b.build()
            }
        } catch (e: Exception) {
            Log.w(TAG, "subtitlesJson parse failed", e)
            emptyList()
        }
    }

    private fun propString(props: JSONObject, key: String): String? {
        val v = props.opt(key) ?: return null
        return when (v) {
            is String -> v
            is JSONObject -> v.optString("value").ifEmpty { v.optString("String") }.ifEmpty { null }
            else -> v.toString()
        }
    }

    private fun propBoolLoose(props: JSONObject, key: String): Boolean? {
        val v = props.opt(key) ?: return null
        return when (v) {
            is Boolean -> v
            is JSONObject -> when (v.optString("type")) {
                "Bool" -> v.optBoolean("value")
                else -> null
            }
            else -> null
        }
    }

    private fun propNumber(props: JSONObject, key: String): Double? {
        val v = props.opt(key) ?: return null
        return when (v) {
            is Number -> v.toDouble()
            is JSONObject -> v.optDouble("value", Double.NaN).let { if (it.isNaN()) null else it }
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun looksLikeMatroska(url: String): Boolean =
        url.lowercase().contains(".mkv")

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
