package com.apoorvdarshan.verceltics.ui.netlify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetlifyLayoutTest {
    @Test
    fun `normal phone content width keeps summary metrics side by side`() {
        assertFalse(shouldStackNetlifySummary(availableWidthDp = 357f, fontScale = 1f))
    }

    @Test
    fun `narrow content or accessibility text stacks summary metrics`() {
        assertTrue(shouldStackNetlifySummary(availableWidthDp = 339f, fontScale = 1f))
        assertTrue(shouldStackNetlifySummary(availableWidthDp = 357f, fontScale = 1.3f))
    }

    @Test
    fun `connection card stacks only at accessibility font scale`() {
        assertFalse(shouldStackNetlifyConnectionCard(fontScale = 1.29f))
        assertTrue(shouldStackNetlifyConnectionCard(fontScale = 1.3f))
        assertTrue(shouldStackNetlifyConnectionCard(fontScale = 2f))
    }
}
