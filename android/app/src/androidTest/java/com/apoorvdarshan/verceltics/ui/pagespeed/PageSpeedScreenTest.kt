package com.apoorvdarshan.verceltics.ui.pagespeed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.apoorvdarshan.verceltics.data.pagespeed.PageSpeedMetricUnit
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import org.junit.Rule
import org.junit.Test

class PageSpeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnectedScreenExposesBrandedCredentialFlow() {
        composeRule.setContent {
            VercelticsTheme {
                PageSpeedScreen(
                    state = PageSpeedUiState(
                        status = PageSpeedConnectionStatus.DISCONNECTED,
                        operation = null,
                    ),
                    onBack = {},
                    onConnect = { _, _ -> },
                    onRefresh = {},
                    onCancel = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("pagespeed.connectionForm").assertIsDisplayed()
        composeRule.onNodeWithTag("pagespeed.siteUrl").assertIsDisplayed()
        composeRule.onNodeWithTag("pagespeed.apiKey").assertIsDisplayed()
        composeRule.onNodeWithTag("pagespeed.connect").assertIsDisplayed()
        composeRule.onNode(hasText("Lab speed meets real-user experience", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun partialCachedDashboardShowsSourceTruthWarningsAndMetricGroups() {
        composeRule.setContent {
            VercelticsTheme {
                PageSpeedScreen(
                    state = PageSpeedUiState(
                        status = PageSpeedConnectionStatus.CONNECTED,
                        dashboard = PARTIAL_DASHBOARD,
                        savedSiteUrl = PARTIAL_DASHBOARD.siteUrl,
                        operation = null,
                        canDisconnect = true,
                    ),
                    onBack = {},
                    onConnect = { _, _ -> },
                    onRefresh = {},
                    onCancel = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("pagespeed.dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("pagespeed.sources").assertIsDisplayed()
        composeRule.onNodeWithText("PARTIAL AUDIT").assertIsDisplayed()
        composeRule.onNodeWithTag("pagespeed.metrics.pagespeed.mobile.").assertIsDisplayed()
        composeRule.onNodeWithText("Showing a saved audit older than 30 minutes.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun searchRequestFocusesUrlWhenDisconnected() {
        var searchRequestId by mutableIntStateOf(0)
        composeRule.setContent {
            VercelticsTheme {
                PageSpeedScreen(
                    state = PageSpeedUiState(
                        status = PageSpeedConnectionStatus.DISCONNECTED,
                        operation = null,
                    ),
                    onBack = {},
                    onConnect = { _, _ -> },
                    onRefresh = {},
                    onCancel = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                    searchFocusRequestId = searchRequestId,
                )
            }
        }

        composeRule.runOnIdle { searchRequestId += 1 }
        composeRule.onNodeWithTag("pagespeed.siteUrl").assertIsFocused()
    }

    @Test
    fun searchRequestExplainsConnectedSingleSiteWorkspace() {
        var searchRequestId by mutableIntStateOf(0)
        composeRule.setContent {
            VercelticsTheme {
                PageSpeedScreen(
                    state = PageSpeedUiState(
                        status = PageSpeedConnectionStatus.CONNECTED,
                        dashboard = PARTIAL_DASHBOARD,
                        savedSiteUrl = PARTIAL_DASHBOARD.siteUrl,
                        operation = null,
                        canDisconnect = true,
                    ),
                    onBack = {},
                    onConnect = { _, _ -> },
                    onRefresh = {},
                    onCancel = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                    searchFocusRequestId = searchRequestId,
                )
            }
        }

        composeRule.runOnIdle { searchRequestId += 1 }
        composeRule.onNodeWithTag("pagespeed.searchNotice").assertIsDisplayed()
        composeRule.onNodeWithText(
            "PageSpeed is a single-site workspace. Disconnect to audit a different HTTPS URL.",
        ).assertIsDisplayed()
    }

    private companion object {
        val PARTIAL_DASHBOARD = PageSpeedDashboardUi(
            siteUrl = "https://example.com",
            siteName = "example.com",
            status = "Good",
            metrics = listOf(
                PageSpeedMetricUi(
                    key = "pagespeed.mobile.performance",
                    label = "Mobile Performance",
                    value = 96.0,
                    unit = PageSpeedMetricUnit.SCORE,
                    formattedValue = "96",
                ),
            ),
            fetchedAtMillis = 42L,
            sources = PageSpeedSourcesUi(
                mobile = PageSpeedSourceUiState.AVAILABLE,
                desktop = PageSpeedSourceUiState.UNAVAILABLE,
                crux = PageSpeedSourceUiState.UNAVAILABLE,
            ),
            warnings = listOf("Desktop PageSpeed data is unavailable: provider temporarily unavailable."),
            cacheState = PageSpeedCacheState.CACHED_STALE,
        )
    }
}
