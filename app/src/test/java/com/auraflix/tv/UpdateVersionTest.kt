package com.auraflix.tv

import com.auraflix.tv.core.update.AppVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `detects newer patch version`() {
        assertTrue(AppVersion.isNewer("1.1.1", "1.1.0"))
        assertFalse(AppVersion.isNewer("1.1.0", "1.1.0"))
        assertFalse(AppVersion.isNewer("1.0.9", "1.1.0"))
    }

    @Test
    fun `detects newer major and minor versions`() {
        assertTrue(AppVersion.isNewer("2.0.0", "1.9.9"))
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.0"))
    }

    @Test
    fun `accepts v prefix and prerelease suffix`() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.1.9"))
        assertTrue(AppVersion.isNewer("1.2.0-beta", "1.1.0"))
    }

    @Test
    fun `handles missing or malformed segments`() {
        assertTrue(AppVersion.isNewer("1.1", "1.0.9"))
        assertFalse(AppVersion.isNewer("1.0", "1.0.1"))
        assertFalse(AppVersion.isNewer("", "1.0.0"))
    }
}
