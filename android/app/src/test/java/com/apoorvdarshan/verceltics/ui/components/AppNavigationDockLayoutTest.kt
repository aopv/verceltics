package com.apoorvdarshan.verceltics.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationDockLayoutTest {
    @Test
    fun `accessibility font scale switches dock to icon friendly layout`() {
        assertFalse(usesIconOnlyNavigationLayout(1.29f))
        assertTrue(usesIconOnlyNavigationLayout(1.30f))
        assertTrue(usesIconOnlyNavigationLayout(2.00f))
    }
}
