package com.relay.client

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cross-service activity for Kubo FGS notification + lifecycle.
 *
 * Notification is shown only when seeding kept titles or actively downloading/pinning.
 * Ephemeral theater play may hold the daemon without a tray notification.
 */
object IpfsNetworkState {
    private val activeDownloads = AtomicInteger(0)
    private val activePinJobs = AtomicInteger(0)
    private val playbackHold = AtomicBoolean(false)

    fun beginDownload() {
        activeDownloads.incrementAndGet()
    }

    fun endDownload() {
        activeDownloads.updateAndGet { n -> if (n > 0) n - 1 else 0 }
    }

    fun activeDownloadCount(): Int = activeDownloads.get()

    fun beginPinJob() {
        activePinJobs.incrementAndGet()
    }

    fun endPinJob() {
        activePinJobs.updateAndGet { n -> if (n > 0) n - 1 else 0 }
    }

    fun activePinJobCount(): Int = activePinJobs.get()

    fun setPlaybackHold(hold: Boolean) {
        playbackHold.set(hold)
    }

    fun playbackHold(): Boolean = playbackHold.get()

    fun isDownloading(): Boolean = activeDownloads.get() > 0 || activePinJobs.get() > 0

    fun seededCount(context: Context): Int {
        val store = IpfsOfflineStore(context.applicationContext)
        if (!store.seedingEnabled()) return 0
        return store.all().size
    }

    /** Tray notification should be visible. */
    fun shouldNotify(context: Context): Boolean =
        seededCount(context) > 0 || isDownloading()

    /** Keep Kubo process alive (may be silent while playing). */
    fun shouldKeepAlive(context: Context): Boolean =
        shouldNotify(context) || playbackHold.get()

    fun notificationText(context: Context): String {
        val seeds = seededCount(context)
        val dl = isDownloading()
        return when {
            seeds > 0 && dl -> "Seeding $seeds · downloading…"
            seeds > 0 -> "Seeding $seeds offline title${if (seeds == 1) "" else "s"}"
            dl -> "Downloading…"
            else -> "Idle"
        }
    }

    /** Start, refresh, or stop the IPFS FGS based on current activity. */
    fun reconcile(context: Context) {
        val app = context.applicationContext
        if (shouldKeepAlive(app)) {
            IpfsNodeService.start(app)
            IpfsNodeService.refreshNotification(app)
        } else {
            IpfsNodeService.stop(app)
        }
    }
}
