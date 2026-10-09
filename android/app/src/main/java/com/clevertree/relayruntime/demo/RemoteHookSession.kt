package com.clevertree.relayruntime.demo

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches a live client-hook import closure and keeps the last good copy on disk.
 * Prefers a complete `Foo.android.js` over `Foo.js` under `hooks/client/native/`.
 * A null stub, or an android file missing named exports that `Foo.js` has, is skipped
 * so `get.jsx` imports keep working. Never loads `*.host.js`.
 */
class RemoteHookSession(context: Context) {
    private val root = File(context.filesDir, "relay-hook-cache")

    data class Loaded(
        val modules: JSONObject,
        val themeYaml: String,
        val fetchedAt: Long,
        val fromCache: Boolean,
    )

    /** Client GET path from OPTIONS (`client.hooks.get.path`), else [fallback]. */
    fun discoverEntry(origin: String, fallback: String): String {
        return entryFromOptions(fetchText(origin.trim().trimEnd('/'), "OPTIONS"), fallback)
    }

    fun load(origin: String, hookPath: String): Loaded {
        val base = origin.trim().trimEnd('/')
        return try {
            val fresh = fetchClosure(base, hookPath)
            writeCache(base, fresh)
            fresh
        } catch (e: Exception) {
            readCache(base) ?: throw e
        }
    }

    private fun fetchClosure(base: String, hookPath: String): Loaded {
        val modules = JSONObject()
        val queue = ArrayDeque<String>()
        queue.add(normalize(hookPath))
        var guard = 0
        while (queue.isNotEmpty() && guard < 256) {
            guard += 1
            val rel = queue.removeFirst()
            if (rel.isEmpty() || modules.has(rel)) continue
            if (skipSpec(rel)) continue
            val body = chooseBody(base, rel) ?: continue
            modules.put(rel, body.text)
            if (body.servedAs != rel) modules.put(body.servedAs, body.text)
            for (spec in importSpecs(body.text)) {
                val next = resolveImport(rel, spec) ?: continue
                if (!modules.has(next)) queue.add(next)
            }
        }
        val entry = normalize(hookPath)
        if (modules.optString(entry).isEmpty()) {
            throw IllegalStateException("Remote entry $entry was not loaded")
        }
        val theme = fetchText("$base/hooks/client/theme.yaml").orEmpty()
        return Loaded(modules, theme, System.currentTimeMillis(), false)
    }

    private data class Body(val text: String, val servedAs: String)

    private fun chooseBody(base: String, rel: String): Body? {
        val paths = candidates(rel)
        if (paths.size < 2) return fetchFirst(base, paths)
        val androidText = fetchSource(base, paths[0])
        val jsText = fetchSource(base, paths[1])
        if (androidText != null && jsText != null && androidReplacesJs(androidText, jsText)) {
            return Body(androidText, paths[0])
        }
        if (jsText != null) return Body(jsText, paths[1])
        if (androidText != null) return Body(androidText, paths[0])
        return null
    }

    private fun fetchSource(base: String, path: String): String? {
        if (path.endsWith(".host.js") || path.contains(".web.")) return null
        val text = fetchText("$base/$path") ?: return null
        return if (looksLikeSource(text)) text else null
    }

    private fun fetchFirst(base: String, paths: List<String>): Body? {
        for (path in paths) {
            if (path.endsWith(".host.js") || path.contains(".web.")) continue
            val text = fetchText("$base/$path") ?: continue
            if (!looksLikeSource(text)) continue
            return Body(text, path)
        }
        return null
    }

    private fun fetchText(urlString: String, method: String = "GET"): String? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        return try {
            val code = conn.responseCode
            if (code !in 200..299) null
            else conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun writeCache(origin: String, loaded: Loaded) {
        val dir = cacheDir(origin)
        dir.mkdirs()
        val payload = JSONObject()
        payload.put("fetchedAt", loaded.fetchedAt)
        payload.put("themeYaml", loaded.themeYaml)
        payload.put("modules", loaded.modules)
        File(dir, "bundle.json").writeText(payload.toString())
    }

    private fun readCache(origin: String): Loaded? {
        val file = File(cacheDir(origin), "bundle.json")
        if (!file.isFile) return null
        return try {
            val payload = JSONObject(file.readText())
            Loaded(
                modules = payload.getJSONObject("modules"),
                themeYaml = payload.optString("themeYaml"),
                fetchedAt = payload.optLong("fetchedAt"),
                fromCache = true,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheDir(origin: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        return File(root, hex)
    }

    companion object {
        private val importRe = Regex("""(?:from\s+|import\s+)['\"]([^'\"]+)['\"]""")

        fun androidReplacesJs(androidSrc: String, jsSrc: String): Boolean {
            if (!namedExportFns(androidSrc).containsAll(namedExportFns(jsSrc))) return false
            if (isNullStub(androidSrc) && !isNullStub(jsSrc)) return false
            return true
        }

        fun namedExportFns(src: String): Set<String> {
            return Regex("""export function (\w+)""")
                .findAll(src)
                .map { it.groupValues[1] }
                .toSet()
        }

        fun isNullStub(src: String): Boolean {
            val code = src
                .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                .replace(Regex("""(?m)//.*"""), "")
            if (code.contains("<")) return false
            return Regex("""return\s+null""").containsMatchIn(code)
        }

        fun candidates(rel: String): List<String> {
            val underNative = rel.contains("/native/") || rel.startsWith("native/")
            if (!underNative) return listOf(rel)
            val dot = rel.lastIndexOf('.')
            if (dot < 0) return listOf(rel)
            val ext = rel.substring(dot)
            val base = rel.substring(0, dot)
            if (base.endsWith(".android") || base.endsWith(".web") || base.endsWith(".host")) {
                return listOf(rel)
            }
            return listOf("$base.android$ext", rel)
        }

        fun entryFromOptions(text: String?, fallback: String): String {
            if (text.isNullOrBlank()) return fallback
            return try {
                val root = com.google.gson.JsonParser.parseString(text)
                if (!root.isJsonObject) return fallback
                val path = root.asJsonObject
                    .getAsJsonObject("client")
                    ?.getAsJsonObject("hooks")
                    ?.getAsJsonObject("get")
                    ?.get("path")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    .orEmpty()
                if (path.isEmpty()) fallback else path.removePrefix("/")
            } catch (_: Exception) {
                fallback
            }
        }

        fun importSpecs(source: String): List<String> {
            return importRe.findAll(source).map { it.groupValues[1] }.toList()
        }

        fun normalize(path: String): String {
            return path.trim().removePrefix("./").removePrefix("/")
        }

        fun skipSpec(spec: String): Boolean {
            if (spec.isEmpty()) return true
            if (spec.startsWith("@") || spec.startsWith("http://") || spec.startsWith("https://")) return true
            if (spec == "react" || spec == "react.js") return true
            if (spec.contains(".host.") || spec.contains(".web.")) return true
            val ext = spec.substringAfterLast('.', "")
            return ext.isNotEmpty() && ext !in setOf("js", "jsx", "mjs", "cjs")
        }

        fun resolveImport(importer: String, spec: String): String? {
            if (skipSpec(spec)) return null
            if (spec.startsWith("@") || spec.startsWith("http")) return null
            if (!spec.startsWith(".") && !spec.startsWith("/")) {
                return normalize(spec)
            }
            val dir = importer.substringBeforeLast('/', "")
            val joined = if (dir.isEmpty()) spec else "$dir/$spec"
            val parts = ArrayDeque<String>()
            for (part in joined.split('/')) {
                when (part) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeLast()
                    else -> parts.add(part)
                }
            }
            return parts.joinToString("/")
        }

        fun looksLikeSource(text: String): Boolean {
            val head = text.trimStart().take(64).lowercase()
            if (head.isEmpty()) return false
            if (head.startsWith("<!doctype") || head.startsWith("<html")) return false
            return true
        }
    }
}
