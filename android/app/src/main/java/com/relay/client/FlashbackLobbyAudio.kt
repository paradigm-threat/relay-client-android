package com.relay.client

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * APK lobby OST — on by default when foreground and not in theater.
 * Enabled flag persisted via [FlashbackLobbyPrefs] (SharedPreferences).
 */
class FlashbackLobbyAudio(
    private val context: Context,
    private val origin: String,
) {
    companion object {
        private const val TAG = "FlashbackLobby"
    }
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playlistJson: String? = null
    private var queue: List<String> = emptyList()
    private var queueIdx = 0
    private var activeSong = ""
    private var theater = false
    private var enabled: Boolean = FlashbackLobbyPrefs.isEnabled(context)
    private var foreground = true
    private var targetVol = 0.28f
    private var fadeRunnable: Runnable? = null
    private var currentVol = 0f

    fun isEnabled(): Boolean = enabled

    fun setTheater(on: Boolean) {
        theater = on
        apply()
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        FlashbackLobbyPrefs.setEnabled(context, on)
        Log.i(TAG, "enabled=$on (persisted)")
        apply()
    }

    fun onForeground(on: Boolean) {
        foreground = on
        apply()
    }

    fun start() {
        Thread {
            val json = FlashbackLobbyPlaylist.fetchPlaylist(origin)
            if (json == null) {
                Log.w(TAG, "playlist fetch failed origin=$origin")
                return@Thread
            }
            Log.i(TAG, "playlist loaded bytes=${json.length}")
            main.post {
                playlistJson = json
                apply()
            }
        }.start()
    }

    fun release() {
        stopFade()
        player?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        player = null
    }

    private fun apply() {
        val json = playlistJson ?: return
        val wantEnabled = enabled && foreground
        val dec = FlashbackLobbyPlaylist.evaluate(
            json,
            FlashbackLobbyPlaylist.HostState(
                enabled = wantEnabled,
                theater = theater,
            ),
        )
        targetVol = dec.volume
        when (dec.action) {
            "fadeOut" -> {
                Log.i(TAG, "fadeOut theater=$theater enabled=$enabled foreground=$foreground")
                fadeTo(0f, dec.fadeOutMs) {
                    pausePlayer()
                }
            }
            "play" -> {
                if (dec.tracks.isEmpty()) {
                    fadeTo(0f, dec.fadeOutMs) { pausePlayer() }
                    return
                }
                val needRebuild =
                    dec.song != activeSong || queue.size != dec.tracks.size
                if (needRebuild) {
                    activeSong = dec.song.orEmpty()
                    queue = if (dec.shuffle) {
                        FlashbackLobbyPlaylist.shuffle(dec.tracks)
                    } else {
                        dec.tracks
                    }
                    queueIdx = 0
                    Log.i(TAG, "play song=$activeSong tracks=${queue.size} season=${dec.season}")
                    playIndex(0, fadeIn = true, fadeInMs = dec.fadeInMs)
                } else {
                    val p = player
                    if (p == null || !p.isPlaying) {
                        playIndex(queueIdx, fadeIn = true, fadeInMs = dec.fadeInMs)
                    } else if (currentVol < targetVol * 0.9f) {
                        fadeTo(targetVol, dec.fadeInMs)
                    }
                }
            }
            else -> fadeTo(0f, dec.fadeOutMs) { pausePlayer() }
        }
    }

    private fun playIndex(i: Int, fadeIn: Boolean, fadeInMs: Int) {
        if (queue.isEmpty()) return
        queueIdx = ((i % queue.size) + queue.size) % queue.size
        val url = queue[queueIdx]
        Log.i(TAG, "track[$queueIdx] $url")
        releasePlayerOnly()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(url)
                setOnPreparedListener { prepared ->
                    currentVol = if (fadeIn) 0f else targetVol
                    prepared.setVolume(currentVol, currentVol)
                    prepared.start()
                    if (fadeIn) fadeTo(targetVol, fadeInMs)
                }
                setOnCompletionListener {
                    playIndex(queueIdx + 1, fadeIn = false, fadeInMs = fadeInMs)
                }
                setOnErrorListener { _, _, _ ->
                    playIndex(queueIdx + 1, fadeIn = false, fadeInMs = fadeInMs)
                    true
                }
                prepareAsync()
            }
            player = mp
        } catch (_: Exception) {
            main.postDelayed({
                playIndex(queueIdx + 1, fadeIn = false, fadeInMs = fadeInMs)
            }, 400)
        }
    }

    private fun pausePlayer() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
    }

    private fun releasePlayerOnly() {
        stopFade()
        player?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        player = null
    }

    private fun stopFade() {
        fadeRunnable?.let { main.removeCallbacks(it) }
        fadeRunnable = null
    }

    private fun fadeTo(end: Float, ms: Int, onDone: (() -> Unit)? = null) {
        stopFade()
        val start = currentVol
        val dur = ms.coerceAtLeast(16).toLong()
        if (abs(start - end) < 0.01f) {
            currentVol = end
            player?.setVolume(end, end)
            onDone?.invoke()
            return
        }
        val t0 = System.currentTimeMillis()
        val step = object : Runnable {
            override fun run() {
                val u = ((System.currentTimeMillis() - t0).toFloat() / dur).coerceIn(0f, 1f)
                currentVol = start + (end - start) * u
                try {
                    player?.setVolume(currentVol, currentVol)
                } catch (_: Exception) {
                }
                if (u >= 1f) {
                    fadeRunnable = null
                    onDone?.invoke()
                } else {
                    main.postDelayed(this, 32)
                }
            }
        }
        fadeRunnable = step
        main.post(step)
    }
}
