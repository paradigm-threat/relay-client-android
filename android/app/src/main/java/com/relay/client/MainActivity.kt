package com.relay.client

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
/**
 * Flashback flavor: native catalog + ExoPlayer theater (no CleverScript client hook).
 * Relay flavor: [RelayClientShell] (servers, tabs, settings, HookRenderer).
 */
class MainActivity : AppCompatActivity() {
    private var flashbackShell: FlashbackNativeShell? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (flashbackShell?.onBackPressed() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.RELAY_LOCK_ORIGIN && keyCode == KeyEvent.KEYCODE_SETTINGS) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
