package com.apoorvdarshan.verceltics.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCategoryLayoutTest {
    @Test
    fun `accessibility font scale stacks full width categories`() {
        assertFalse(usesStackedConnectionCategoryLayout(1.29f))
        assertTrue(usesStackedConnectionCategoryLayout(1.30f))
        assertTrue(usesStackedConnectionCategoryLayout(2.00f))
    }
}
