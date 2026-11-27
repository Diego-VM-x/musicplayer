package com.larkplayer.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The root-APK fallback must not mistake the last digit of a dotted version for a code. */
class ApkVersionNameTest {

    @Test
    fun `simple numeric suffix is a version code`() {
        assertEquals(14, AppUpdate.versionCodeFromApkName("musicplayer-14.apk"))
    }

    @Test
    fun `v prefixed suffix is a version code`() {
        assertEquals(51, AppUpdate.versionCodeFromApkName("player_v51.apk"))
    }

    @Test
    fun `dotted semantic version is not silently truncated`() {
        assertNull(AppUpdate.versionCodeFromApkName("larkplayer-2027.1.8.apk"))
    }

    @Test
    fun `debug build has no version code`() {
        assertNull(AppUpdate.versionCodeFromApkName("app-debug.apk"))
    }
}
