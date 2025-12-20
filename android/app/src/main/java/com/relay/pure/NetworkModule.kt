package com.relay.pure

import android.util.Log

object NetworkModule {
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("android_jni")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NetworkModule", "Failed to load native library", e)
        }
    }

    fun checkPort(host: String, port: Int): Boolean {
        if (!nativeLoaded) return false
        return nativeCheckPort(host, port)
    }

    private external fun nativeCheckPort(host: String, port: Int): Boolean
}
