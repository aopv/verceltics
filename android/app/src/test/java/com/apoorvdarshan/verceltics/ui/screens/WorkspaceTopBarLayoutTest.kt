package com.apoorvdarshan.verceltics.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTopBarLayoutTest {
    @Test
    fun `normal phone width and font scale preserve centered title layout`() {
        assertFalse(
            usesAdaptiveWorkspaceTopBar(
                availableWidthDp = 390f,
                fontScale = 1f,
            ),
        )
    }

    @Test
    fun `narrow window allows title to wrap beside account control`() {
        assertTrue(
            usesAdaptiveWorkspaceTopBar(
                availableWidthDp = 339.99f,
                fontScale = 1f,
            ),
        )
    }

    @Test
    fun `accessibility font scale allows title to wrap without a fixed line cap`() {
        assertTrue(
            usesAdaptiveWorkspaceTopBar(
                availableWidthDp = 390f,
                fontScale = 1.3f,
            ),
        )
    }
}
