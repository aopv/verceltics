package com.apoorvdarshan.verceltics.ui.screens

import com.apoorvdarshan.verceltics.ui.VercelConnectionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VercelWorkspaceStateTest {
    @Test
    fun coldRestoreKeepsSavedProjectRouteUntilDashboardIsKnown() {
        assertFalse(
            shouldClearSavedProjectSelection(
                status = VercelConnectionStatus.RESTORING,
                projectStillExists = null,
            ),
        )
        assertFalse(
            shouldClearSavedProjectSelection(
                status = VercelConnectionStatus.CONNECTED,
                projectStillExists = true,
            ),
        )
        assertTrue(
            shouldClearSavedProjectSelection(
                status = VercelConnectionStatus.CONNECTED,
                projectStillExists = false,
            ),
        )
    }
}
