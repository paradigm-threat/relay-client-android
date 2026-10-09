package com.clevertree.relayruntime.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHookSessionTest {
    @Test
    fun nullStubDoesNotReplaceNamedExports() {
        val stub = "export default function AffirmationAndroidStub() { return null }\n"
        val js = """
            export function ensureOwnershipAckFromBrowser() { return false }
            export default function Affirmation() { return null }
        """.trimIndent()
        assertFalse(RemoteHookSession.androidReplacesJs(stub, js))
    }

    @Test
    fun completeAndroidFileReplacesJs() {
        val android = """
            export function ensureOwnershipAckFromBrowser() { return false }
            export default function Affirmation() { return <div /> }
        """.trimIndent()
        val js = "export function ensureOwnershipAckFromBrowser() { return false }\n"
        assertTrue(RemoteHookSession.androidReplacesJs(android, js))
    }

    @Test
    fun nullStubDoesNotReplaceRealComponent() {
        val stub = "export default function NowShowingAndroidStub() { return null }\n"
        val js = "export default function NowShowing() { return <div /> }\n"
        assertFalse(RemoteHookSession.androidReplacesJs(stub, js))
    }

    @Test
    fun nativeModulePrefersAndroidFile() {
        assertEquals(
            listOf(
                "hooks/client/native/Affirmation.android.js",
                "hooks/client/native/Affirmation.js",
            ),
            RemoteHookSession.candidates("hooks/client/native/Affirmation.js"),
        )
    }

    @Test
    fun alreadyPlatformSpecificIsNotRewritten() {
        assertEquals(
            listOf("hooks/client/native/Affirmation.android.js"),
            RemoteHookSession.candidates("hooks/client/native/Affirmation.android.js"),
        )
        assertEquals(
            listOf("hooks/client/native/Affirmation.host.js"),
            RemoteHookSession.candidates("hooks/client/native/Affirmation.host.js"),
        )
    }

    @Test
    fun nonNativePathIsUnchanged() {
        assertEquals(
            listOf("hooks/client/get.jsx"),
            RemoteHookSession.candidates("hooks/client/get.jsx"),
        )
    }

    @Test
    fun skipSpecDropsWebHostsAndPackages() {
        assertTrue(RemoteHookSession.skipSpec("hooks/client/native/Affirmation.host.js"))
        assertTrue(RemoteHookSession.skipSpec("hooks/client/native/Affirmation.web.js"))
        assertTrue(RemoteHookSession.skipSpec("@clevertree/video"))
        assertTrue(RemoteHookSession.skipSpec("react"))
        assertFalse(RemoteHookSession.skipSpec("hooks/client/native/Affirmation.js"))
    }

    @Test
    fun resolveImportFromGetJsx() {
        assertEquals(
            "hooks/client/native/Affirmation.js",
            RemoteHookSession.resolveImport(
                "hooks/client/get.jsx",
                "./native/Affirmation.js",
            ),
        )
        assertNull(RemoteHookSession.resolveImport("hooks/client/get.jsx", "@hooks"))
    }

    @Test
    fun optionsPathOverridesDefault() {
        val json = """{"client":{"hooks":{"get":{"path":"/hooks/client/get.jsx"}}}}"""
        assertEquals(
            "hooks/client/get.jsx",
            RemoteHookSession.entryFromOptions(json, "hooks/client/app.jsx"),
        )
        assertEquals(
            "hooks/client/get.jsx",
            RemoteHookSession.entryFromOptions("not-json", "hooks/client/get.jsx"),
        )
        assertEquals(
            "hooks/client/get.jsx",
            RemoteHookSession.entryFromOptions(null, "hooks/client/get.jsx"),
        )
    }

    @Test
    fun htmlIsNotHookSource() {
        assertFalse(RemoteHookSession.looksLikeSource("<!DOCTYPE html><html></html>"))
        assertTrue(RemoteHookSession.looksLikeSource("export default function Affirmation() {}"))
    }
}
