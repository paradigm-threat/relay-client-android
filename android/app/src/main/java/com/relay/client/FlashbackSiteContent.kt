package com.relay.client

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Site pages mirrored from web `routeKind` / header+footer nav in `hooks/client/get.jsx`.
 * Markdown loads: network → optional files-dir cache → APK assets.
 */
object FlashbackSiteContent {

    sealed class NavAction {
        object Library : NavAction()
        data class Markdown(val title: String, val path: String) : NavAction()
        object PlaybackLab : NavAction()
        object SeedingManage : NavAction()
        object Board : NavAction()
        object Chat : NavAction()
        object CheckUpdates : NavAction()
        object CycleTheaterTheme : NavAction()
    }

    data class NavItem(
        val id: String,
        val title: String,
        val action: NavAction,
    )

    /** Drawer order: header nav, then footer-only Terms, then Android extras. */
    fun drawerItems(context: Context? = null): List<NavItem> {
        val themeId =
            if (context != null) FlashbackTheaterPrefs.theme(context)
            else FlashbackTheaterPrefs.DEFAULT_THEME
        val themeLabel = FlashbackTheaterPrefs.label(themeId)
        return listOf(
            NavItem("library", "Library", NavAction.Library),
            NavItem("app", "App", NavAction.Markdown("Flashback App", "content/app.md")),
            NavItem("setup", "Setup", NavAction.Markdown("Setup for playback", "content/setup.md")),
            NavItem("request", "Request", NavAction.Markdown("Request downloads", "content/request.md")),
            NavItem("motivation", "Why", NavAction.Markdown("Why we’re doing this", "content/motivation.md")),
            NavItem("submit", "Submit", NavAction.Markdown("Submissions", "content/submit.md")),
            NavItem("board", "Report a bug", NavAction.Board),
            NavItem("chat", "Chat", NavAction.Chat),
            NavItem("legal", "Legal", NavAction.Markdown("Ownership policy", "content/legal.md")),
            NavItem("terms", "Terms", NavAction.Markdown("Terms", "content/terms.md")),
            NavItem("theme", "Theme: $themeLabel", NavAction.CycleTheaterTheme),
            NavItem("seeding", "Downloads & seeding", NavAction.SeedingManage),
            NavItem("android-lab", "Android lab", NavAction.PlaybackLab),
            NavItem("updates", "Check for updates", NavAction.CheckUpdates),
        )
    }

    /** Bundled markdown paths (APK assets + cache keys). */
    val bundledMarkdownPaths: List<String> = listOf(
        "content/app.md",
        "content/setup.md",
        "content/legal.md",
        "content/motivation.md",
        "content/terms.md",
        "content/request.md",
        "content/submit.md",
        "content/android-lab.md",
        "content/home.md",
    )

    /**
     * Prefer live site text; on success refresh files-dir cache.
     * Fall back to cache, then APK assets under the same relative path.
     */
    fun loadMarkdown(context: Context, origin: String, path: String): String? {
        val rel = normalizeAssetPath(path)
        val remote = FlashbackCatalog.fetchText(origin, rel)
        if (!remote.isNullOrBlank()) {
            writeCache(context, rel, remote)
            return remote
        }
        readCache(context, rel)?.let { if (it.isNotBlank()) return it }
        return readAsset(context, rel)
    }

    fun readAsset(context: Context, path: String): String? {
        val rel = normalizeAssetPath(path)
        return try {
            context.assets.open(rel).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAssetPath(path: String): String =
        path.trim().trimStart('/').replace('\\', '/')

    private fun cacheFile(context: Context, rel: String): File {
        val safe = rel.replace("..", "_")
        return File(context.filesDir, "site-content/$safe")
    }

    private fun writeCache(context: Context, rel: String, text: String) {
        try {
            val file = cacheFile(context, rel)
            file.parentFile?.mkdirs()
            file.writeText(text, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            /* cache is best-effort */
        }
    }

    private fun readCache(context: Context, rel: String): String? {
        return try {
            val file = cacheFile(context, rel)
            if (!file.isFile) null else file.readText(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
