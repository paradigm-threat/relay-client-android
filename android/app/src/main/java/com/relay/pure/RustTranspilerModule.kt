package com.relay.pure

import android.util.Log

object RustTranspilerModule {
    private const val TAG = "RustTranspiler"
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("android_jni")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun transpile(code: String, filename: String, includeRuntime: Boolean = false): String {
        if (!nativeLoaded) return ""
        return nativeTranspile(code, filename, includeRuntime)
    }

    fun getVersion(): String {
        if (!nativeLoaded) return "unknown"
        return nativeGetVersion()
    }

    private external fun nativeTranspile(code: String, filename: String, includeRuntime: Boolean): String
    private external fun nativeGetVersion(): String
}
