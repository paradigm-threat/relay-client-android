package com.relay.client

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
/**
 * Flashback flavor: native catalog + ExoPlayer theater (no CleverScript client hook).
 * Relay flavor: [RelayClientShell] (servers, tabs, settings, HookRenderer).
 */
class MainActivity : AppCompatActivity() {
    private var flashbackShell: FlashbackNativeShell? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestDownloadNotification()
        if (BuildConfig.RELAY_LOCK_ORIGIN) {
            val shell = FlashbackNativeShell(this, BuildConfig.RELAY_DEFAULT_ORIGIN).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            flashbackShell = shell
            setContentView(shell)
            return
        }
        setContentView(RelayClientShell(this))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        flashbackShell?.onConfigurationChanged()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.RELAY_LOCK_ORIGIN && keyCode == KeyEvent.KEYCODE_SETTINGS) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestDownloadNotification() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted == PackageManager.PERMISSION_GRANTED) return
        AlertDialog.Builder(this)
            .setTitle("Downloads")
            .setMessage(
                "Saving a movie for offline play stores the full file in this app’s private storage. " +
                    "It can be several gigabytes and is removed when you clear the app’s data. " +
                    "A notification shows download progress."
            )
            .setPositiveButton("Continue") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
            }
            .setNegativeButton("Not now", null)
            .show()
    }
}
