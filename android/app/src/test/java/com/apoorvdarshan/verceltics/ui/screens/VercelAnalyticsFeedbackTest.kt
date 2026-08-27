package com.apoorvdarshan.verceltics.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VercelAnalyticsFeedbackTest {
    @Test
    fun `refresh error takes precedence over retained unavailable feedback`() {
        assertEquals(
            AnalyticsFeedbackPresentation.ERROR,
            analyticsFeedbackPresentation(
                error = "Refresh failed",
                unavailableMessage = "Analytics is not enabled",
            ),
        )
    }

    @Test
    fun `unavailable feedback renders when no error exists`() {
        assertEquals(
            AnalyticsFeedbackPresentation.UNAVAILABLE,
            analyticsFeedbackPresentation(
                error = null,
                unavailableMessage = "Analytics is not enabled",
            ),
        )
    }

    @Test
    fun `no feedback renders without an error or unavailable result`() {
        assertNull(
            analyticsFeedbackPresentation(
                error = null,
                unavailableMessage = null,
            ),
        )
    }
}
