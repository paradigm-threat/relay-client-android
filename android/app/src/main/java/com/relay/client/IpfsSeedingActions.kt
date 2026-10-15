package com.relay.client

import android.content.Context

/** Bulk seed / pin helpers for Downloads & seeding UI. */
object IpfsSeedingActions {
    /**
     * Master off: stop advertising seeds (unpin all), keep the offline list so Enable all
     * can re-pin. Stops Kubo when nothing else needs it.
     */
    fun disableAllSeeding(context: Context) {
        val app = context.applicationContext
        val store = IpfsOfflineStore(app)
        store.setSeedingEnabled(false)
        val entries = store.all()
        if (entries.isNotEmpty()) {
            IpfsSession.ensureStartedAndReady(app, 60_000L)
            for (e in entries) {
                try {
                    IpfsSession.pinRm(e.cid)
                } catch (_: Exception) {
                }
            }
            try {
                IpfsSession.repoGc()
            } catch (_: Exception) {
            }
        }
        IpfsNetworkState.reconcile(app)
    }

    /** Master on + re-pin every kept title. */
    fun enableAllSeeding(context: Context) {
        val app = context.applicationContext
        val store = IpfsOfflineStore(app)
        store.setSeedingEnabled(true)
        val entries = store.all()
        if (entries.isEmpty()) {
            IpfsNetworkState.reconcile(app)
            return
        }
        if (!IpfsSession.ensureStartedAndReady(app, 120_000L)) {
            IpfsNetworkState.reconcile(app)
            return
        }
        for (e in entries) {
            try {
                IpfsNetworkState.beginPinJob()
                IpfsSession.ensureFetching(e.cid)
                IpfsSession.pinAdd(e.cid)
            } catch (_: Exception) {
            } finally {
                IpfsNetworkState.endPinJob()
            }
        }
        IpfsNetworkState.reconcile(app)
    }

    /** Remove every kept title (unpin + clear list) and stop seeding. */
    fun removeAllOffline(context: Context) {
        val app = context.applicationContext
        val store = IpfsOfflineStore(app)
        val entries = store.all()
        if (entries.isNotEmpty() && IpfsSession.isReady()) {
            for (e in entries) {
                try {
                    IpfsSession.pinRm(e.cid)
                } catch (_: Exception) {
                }
            }
            try {
                IpfsSession.repoGc()
            } catch (_: Exception) {
            }
        }
        store.clearAll()
        store.setSeedingEnabled(true)
        IpfsNetworkState.reconcile(app)
    }

    fun cancelAllDownloads(context: Context) {
        MovieDownloadService.cancelAll(context.applicationContext)
        IpfsNetworkState.reconcile(context.applicationContext)
    }
}
