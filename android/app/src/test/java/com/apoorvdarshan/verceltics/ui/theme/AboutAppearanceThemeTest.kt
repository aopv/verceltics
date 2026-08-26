package com.apoorvdarshan.verceltics.ui.theme

import com.apoorvdarshan.verceltics.ui.screens.about.AboutAppearance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutAppearanceThemeTest {
    @Test
    fun `system appearance follows the current Android system mode`() {
        assertFalse(AboutAppearance.SYSTEM.resolveDarkTheme(systemDarkTheme = false))
        assertTrue(AboutAppearance.SYSTEM.resolveDarkTheme(systemDarkTheme = true))
    }

    @Test
    fun `explicit appearance overrides Android system mode`() {
        assertFalse(AboutAppearance.LIGHT.resolveDarkTheme(systemDarkTheme = true))
        assertTrue(AboutAppearance.DARK.resolveDarkTheme(systemDarkTheme = false))
    }
}
