package com.apoorvdarshan.verceltics.ui.searchconsole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchConsoleConnectionCardStateTest {
    @Test
    fun `root status surfaces every degraded dashboard state`() {
        val healthy = SearchConsoleUiState(
            oauthReadiness = SearchConsoleOAuthReadinessUi.Ready,
            status = SearchConsoleConnectionStatus.CONNECTED,
            dashboard = dashboard(),
            operation = null,
        )

        assertEquals("Connected", searchConsoleConnectionCardStatus(healthy))
        assertEquals(
            "Attention",
            searchConsoleConnectionCardStatus(healthy.copy(error = "Refresh failed.")),
        )
        assertEquals(
            "Attention",
            searchConsoleConnectionCardStatus(
                healthy.copy(dashboard = dashboard(providerInventoryComplete = false)),
            ),
        )
        assertEquals(
            "Attention",
            searchConsoleConnectionCardStatus(
                healthy.copy(dashboard = dashboard(warnings = listOf("Inventory is partial."))),
            ),
        )
        assertEquals(
            "Attention",
            searchConsoleConnectionCardStatus(
                healthy.copy(dashboard = dashboard(cacheState = SearchConsoleCacheState.CACHED_STALE)),
            ),
        )
        assertEquals(
            "Disconnected",
            searchConsoleConnectionCardStatus(
                healthy.copy(
                    status = SearchConsoleConnectionStatus.DISCONNECTED,
                    dashboard = null,
                ),
            ),
        )
    }

    @Test
    fun `root card stacks only at accessibility font scale`() {
        assertFalse(shouldStackSearchConsoleConnectionCard(1.29f))
        assertTrue(shouldStackSearchConsoleConnectionCard(1.3f))
        assertTrue(shouldStackSearchConsoleConnectionCard(2f))
    }

    private fun dashboard(
        providerInventoryComplete: Boolean = true,
        warnings: List<String> = emptyList(),
        cacheState: SearchConsoleCacheState = SearchConsoleCacheState.LIVE,
    ) = SearchConsoleDashboardUi(
        account = SearchConsoleAccountUi("subject", "owner@example.com"),
        properties = emptyList(),
        loadedPropertyCount = 0,
        providerInventoryComplete = providerInventoryComplete,
        inventoryTruncatedForDisplay = false,
        warnings = warnings,
        fetchedAtMillis = 42,
        cacheState = cacheState,
    )
}
