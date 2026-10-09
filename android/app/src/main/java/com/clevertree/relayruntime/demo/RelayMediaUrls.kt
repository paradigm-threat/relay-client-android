package com.clevertree.relayruntime.demo

/**
 * Gateway base for resolving relative media/subtitle paths (set by [HookRenderer] before render).
 */
object RelayMediaUrls {
    @Volatile
    var fetchBaseUrl: String? = null

    fun resolve(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("file:")) return u
        val base = fetchBaseUrl?.trim()?.trimEnd('/') ?: return u
        return UrlResolve.resolve(base, u)
    }
}
