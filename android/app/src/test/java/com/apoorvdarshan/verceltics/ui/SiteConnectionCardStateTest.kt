package com.apoorvdarshan.verceltics.ui

import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedCacheState
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedConnectionStatus
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedDashboardUi
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedSourceUiState
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedSourcesUi
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteConnectionCardStateTest {
    @Test
    fun `pagespeed root status surfaces every degraded dashboard state`() {
        val healthy = PageSpeedUiState(
            status = PageSpeedConnectionStatus.CONNECTED,
            dashboard = dashboard(),
            operation = null,
        )

        assertEquals("Live", pageSpeedConnectionCardStatus(healthy))
        assertEquals(
            "Attention",
            pageSpeedConnectionCardStatus(healthy.copy(error = "Refresh failed.")),
        )
        assertEquals(
            "Attention",
            pageSpeedConnectionCardStatus(
                healthy.copy(
                    dashboard = dashboard(
                        sources = PageSpeedSourcesUi(
                            mobile = PageSpeedSourceUiState.AVAILABLE,
                            desktop = PageSpeedSourceUiState.UNAVAILABLE,
                            crux = PageSpeedSourceUiState.AVAILABLE,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            "Attention",
            pageSpeedConnectionCardStatus(
                healthy.copy(dashboard = dashboard(warnings = listOf("CrUX is delayed."))),
            ),
        )
        assertEquals(
            "Attention",
            pageSpeedConnectionCardStatus(
                healthy.copy(dashboard = dashboard(cacheState = PageSpeedCacheState.CACHED_STALE)),
            ),
        )
    }

    @Test
    fun `pagespeed root card stacks only at accessibility font scale`() {
        assertFalse(shouldStackPageSpeedConnectionCard(1.29f))
        assertTrue(shouldStackPageSpeedConnectionCard(1.3f))
        assertTrue(shouldStackPageSpeedConnectionCard(2f))
    }

    private fun dashboard(
        sources: PageSpeedSourcesUi = PageSpeedSourcesUi(
            mobile = PageSpeedSourceUiState.AVAILABLE,
            desktop = PageSpeedSourceUiState.AVAILABLE,
            crux = PageSpeedSourceUiState.AVAILABLE,
        ),
        warnings: List<String> = emptyList(),
        cacheState: PageSpeedCacheState = PageSpeedCacheState.LIVE,
    ) = PageSpeedDashboardUi(
        siteUrl = "https://example.com",
        siteName = "example.com",
        status = "Complete",
        metrics = emptyList(),
        fetchedAtMillis = 42,
        sources = sources,
        warnings = warnings,
        cacheState = cacheState,
    )
}
