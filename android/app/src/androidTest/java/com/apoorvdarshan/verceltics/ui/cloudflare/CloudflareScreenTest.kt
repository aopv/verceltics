package com.apoorvdarshan.verceltics.ui.cloudflare

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CloudflareScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardSearchSectionsAndAccountPickerExposeRealInventory() {
        var selectedAccount: String? = null
        compose.setContent {
            VercelticsTheme {
                CloudflareScreen(
                    state = connectedState(),
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onSelectAccount = { selectedAccount = it },
                    onOpenResource = { _, _ -> },
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        compose.onNodeWithTag("cloudflare.dashboard").assertIsDisplayed()
        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasTestTag("cloudflare.search"))
        compose.onNodeWithTag("cloudflare.search").performTextInput("missing")
        compose.onAllNodesWithTag("cloudflare.zone.zone-one").assertCountEquals(0)
        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasText("No zones match “missing”."))
        compose.onNodeWithText("No zones match “missing”.").assertIsDisplayed()

        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasTestTag("cloudflare.search.clear"))
        compose.onNodeWithTag("cloudflare.search.clear").performClick()
        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasTestTag("cloudflare.section.workers"))
        compose.onNodeWithTag("cloudflare.section.workers").performClick()
        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasTestTag("cloudflare.worker.worker-one"))
        compose.onNodeWithTag("cloudflare.worker.worker-one").assertIsDisplayed()

        compose.onNodeWithTag("cloudflare.dashboard").performScrollToNode(hasTestTag("cloudflare.accountPicker"))
        compose.onNodeWithTag("cloudflare.accountPicker").performClick()
        compose.onNodeWithTag("cloudflare.accountSheet").assertIsDisplayed()
        compose.onNodeWithTag("cloudflare.account.account-two").performClick()
        compose.runOnIdle { assertEquals("account-two", selectedAccount) }
    }

    @Test
    fun detailShowsReadOnlyZoneFieldsAndBackCallback() {
        var backed = false
        compose.setContent {
            VercelticsTheme {
                CloudflareScreen(
                    state = connectedState().copy(
                        selectedResource = CloudflareResourceSelection(
                            CloudflareResourceKind.ZONE,
                            "zone-one",
                        ),
                    ),
                    onBack = { backed = true },
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onSelectAccount = {},
                    onOpenResource = { _, _ -> },
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        compose.onNodeWithTag("cloudflare.resourceDetail").assertIsDisplayed()
        compose.onNodeWithText("verceltics.app").assertIsDisplayed()
        compose.onNodeWithText("Pro").assertIsDisplayed()
        compose.onNodeWithTag("cloudflare.resourceDetail").performScrollToNode(
            hasText("Read-only Cloudflare data fetched for Production. No mutation controls are available."),
        )
        compose.onNodeWithText("Read-only Cloudflare data fetched for Production. No mutation controls are available.")
            .assertIsDisplayed()
        compose.onNodeWithTag("cloudflare.back").performClick()
        compose.runOnIdle { assertEquals(true, backed) }
    }

    @Test
    fun connectionFormKeepsTokenOutOfStateAndPassesSecretOnlyOnSubmit() {
        var connectedToken: SecretValue? = null
        compose.setContent {
            VercelticsTheme {
                CloudflareScreen(
                    state = CloudflareUiState(
                        status = CloudflareConnectionStatus.DISCONNECTED,
                        operation = null,
                    ),
                    onBack = {},
                    onConnect = { connectedToken = it },
                    onRefresh = {},
                    onCancel = {},
                    onSelectAccount = {},
                    onOpenResource = { _, _ -> },
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        compose.onNodeWithTag("cloudflare.connectionForm").assertIsDisplayed()
        compose.runOnIdle { assertNull(connectedToken) }
        compose.onNodeWithTag("cloudflare.token").performTextInput("scoped-token")
        compose.onNodeWithTag("cloudflare.connect").performClick()
        compose.runOnIdle { assertEquals("scoped-token", connectedToken?.use { it }) }
    }
}

private fun connectedState(): CloudflareUiState = CloudflareUiState(
    status = CloudflareConnectionStatus.CONNECTED,
    dashboard = CloudflareDashboardUi(
        profile = CloudflareProfileUi("profile", "Apoorv Cloudflare", "active"),
        accounts = listOf(
            CloudflareAccountUi("account-one", "Production", "standard"),
            CloudflareAccountUi("account-two", "Labs", "standard"),
        ),
        loadedAccountCount = 2,
        accountsComplete = true,
        accountsTruncatedForDisplay = false,
        selectedAccountId = "account-one",
        inventory = CloudflareInventoryUi(
            accountId = "account-one",
            zones = listOf(
                CloudflareZoneUi(
                    id = "zone-one",
                    name = "verceltics.app",
                    status = "active",
                    type = "full",
                    paused = false,
                    accountName = "Production",
                    planName = "Pro",
                ),
            ),
            pagesProjects = listOf(
                CloudflarePagesProjectUi(
                    id = "pages-one",
                    name = "docs",
                    subdomain = "docs.pages.dev",
                    domains = listOf("docs.verceltics.app"),
                    productionBranch = "main",
                    latestDeploymentStatus = "success",
                ),
            ),
            workers = listOf(
                CloudflareWorkerUi(
                    id = "worker-one",
                    modifiedOn = "2026-08-26T12:00:00Z",
                    compatibilityDate = "2026-08-01",
                    handlers = listOf("fetch"),
                    hasAssets = false,
                    hasModules = true,
                ),
            ),
            loadedZoneCount = 1,
            loadedPagesProjectCount = 1,
            loadedWorkerCount = 1,
            zonesComplete = true,
            pagesComplete = true,
            workersComplete = true,
            zonesTruncatedForDisplay = false,
            pagesTruncatedForDisplay = false,
            workersTruncatedForDisplay = false,
            warnings = emptyList(),
        ),
        warnings = emptyList(),
        fetchedAtMillis = 1_700_000_000_000,
        cacheState = CloudflareCacheState.LIVE,
    ),
    savedProfile = CloudflareProfileUi("profile", "Apoorv Cloudflare", "active"),
    operation = null,
)
