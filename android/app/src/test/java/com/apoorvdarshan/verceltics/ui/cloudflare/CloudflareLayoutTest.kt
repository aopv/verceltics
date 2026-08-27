package com.apoorvdarshan.verceltics.ui.cloudflare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareLayoutTest {
    @Test
    fun `connection card stacks only at accessibility font scale`() {
        assertFalse(shouldStackCloudflareConnectionCard(fontScale = 1.29f))
        assertTrue(shouldStackCloudflareConnectionCard(fontScale = 1.3f))
        assertTrue(shouldStackCloudflareConnectionCard(fontScale = 2f))
    }
}
