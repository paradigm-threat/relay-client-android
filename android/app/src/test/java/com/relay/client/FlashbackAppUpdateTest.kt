package com.relay.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashbackAppUpdateTest {
    @Test
    fun parseManifestReadsCodeNameAndUrl() {
        val remote = FlashbackAppUpdate.parseManifest(
            """
            {
              "versionCode": 14,
              "versionName": "1.0.14",
              "apkUrl": "https://flashbackrepository.org/downloads/flashback.apk"
            }
            """.trimIndent(),
        )
        requireNotNull(remote)
        assertEquals(14, remote.versionCode)
        assertEquals("1.0.14", remote.versionName)
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback.apk",
            remote.apkUrl,
        )
    }

    @Test
    fun parseManifestDefaultsApkPathAndRejectsBadCode() {
        val relative = FlashbackAppUpdate.parseManifest("""{"versionCode":13}""")
        requireNotNull(relative)
        assertEquals("13", relative.versionName)
        assertEquals("/downloads/flashback.apk", relative.apkUrl)

        assertNull(FlashbackAppUpdate.parseManifest("""{"versionCode":0}"""))
        assertNull(FlashbackAppUpdate.parseManifest("not-json"))
        assertNull(FlashbackAppUpdate.parseManifest("{}"))
    }

    @Test
    fun isOutdatedComparesVersionCodes() {
        val remote = RemoteAppVersion(14, "1.0.14", "/downloads/flashback.apk")
        assertTrue(FlashbackAppUpdate.isOutdated(13, remote))
        assertFalse(FlashbackAppUpdate.isOutdated(14, remote))
        assertFalse(FlashbackAppUpdate.isOutdated(15, remote))
    }

    @Test
    fun resolveApkUrlHandlesAbsoluteAndRelative() {
        val origin = "https://flashbackrepository.org/"
        assertEquals(
            "https://cdn.example/a.apk",
            FlashbackAppUpdate.resolveApkUrl(
                origin,
                RemoteAppVersion(1, "1", "https://cdn.example/a.apk"),
            ),
        )
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback.apk",
            FlashbackAppUpdate.resolveApkUrl(
                origin,
                RemoteAppVersion(1, "1", "/downloads/flashback.apk"),
            ),
        )
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback.apk",
            FlashbackAppUpdate.resolveApkUrl(
                origin,
                RemoteAppVersion(1, "1", "downloads/flashback.apk"),
            ),
        )
    }

    @Test
    fun parseManifestReadsApkUrlsByAbi() {
        val remote = FlashbackAppUpdate.parseManifest(
            """
            {
              "versionCode": 20,
              "versionName": "1.0.20",
              "apkUrl": "https://flashbackrepository.org/downloads/flashback.apk",
              "apkUrls": {
                "armeabi-v7a": "/downloads/flashback-armeabi-v7a.apk",
                "arm64-v8a": "https://flashbackrepository.org/downloads/flashback-arm64-v8a.apk"
              }
            }
            """.trimIndent(),
        )
        requireNotNull(remote)
        assertEquals(2, remote.apkUrls.size)
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback-armeabi-v7a.apk",
            FlashbackAppUpdate.resolveApkUrl(
                "https://flashbackrepository.org",
                remote,
                listOf("armeabi-v7a", "armeabi"),
            ),
        )
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback-arm64-v8a.apk",
            FlashbackAppUpdate.resolveApkUrl(
                "https://flashbackrepository.org",
                remote,
                listOf("arm64-v8a", "armeabi-v7a"),
            ),
        )
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback.apk",
            FlashbackAppUpdate.resolveApkUrl(
                "https://flashbackrepository.org",
                remote,
                listOf("x86_64"),
            ),
        )
    }

    @Test
    fun manifestUrlJoinsOrigin() {
        assertEquals(
            "https://flashbackrepository.org/downloads/flashback.json",
            FlashbackAppUpdate.manifestUrl("https://flashbackrepository.org/"),
        )
    }

    @Test
    fun tryBeginInstallRejectsConcurrentAcquire() {
        FlashbackAppUpdate.resetInstallGateForTests()
        assertTrue(FlashbackAppUpdate.tryBeginInstall())
        assertTrue(FlashbackAppUpdate.isInstallInFlight())
        assertFalse(FlashbackAppUpdate.tryBeginInstall())
        FlashbackAppUpdate.resetInstallGateForTests()
        assertFalse(FlashbackAppUpdate.isInstallInFlight())
        assertTrue(FlashbackAppUpdate.tryBeginInstall())
        FlashbackAppUpdate.resetInstallGateForTests()
    }
}
