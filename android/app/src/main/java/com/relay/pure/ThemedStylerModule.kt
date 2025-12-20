package com.relay.pure

import android.util.Log

object ThemedStylerModule {
    private const val TAG = "ThemedStyler"
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("android_jni")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun renderCss(usageJson: String, themesJson: String): String {
        if (!nativeLoaded) return ""
        return nativeRenderCss(usageJson, themesJson)
    }

    fun getRnStyles(selector: String, classesJson: String, themesJson: String): String {
        if (!nativeLoaded) return "{}"
        return nativeGetRnStyles(selector, classesJson, themesJson)
    }

    fun getAndroidStyles(selector: String, classesJson: String, themesJson: String): String {
        if (!nativeLoaded) return "{}"
        return nativeGetAndroidStyles(selector, classesJson, themesJson)
    }

    fun getDefaultState(): String {
        if (!nativeLoaded) return "{}"
        return nativeGetDefaultState()
    }

    fun getVersion(): String {
        if (!nativeLoaded) return "unknown"
        return nativeGetVersion()
    }

    private external fun nativeRenderCss(usageJson: String, themesJson: String): String
    private external fun nativeGetRnStyles(selector: String, classesJson: String, themesJson: String): String
    private external fun nativeGetAndroidStyles(selector: String, classesJson: String, themesJson: String): String
    private external fun nativeGetDefaultState(): String
    private external fun nativeGetVersion(): String
}
