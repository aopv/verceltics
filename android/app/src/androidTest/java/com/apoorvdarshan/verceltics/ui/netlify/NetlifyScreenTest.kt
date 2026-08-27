package com.apoorvdarshan.verceltics.ui.netlify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import org.junit.Rule
import org.junit.Test

class NetlifyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnectedScreenExposesBrandedEphemeralCredentialFlow() {
        composeRule.setContent {
            VercelticsTheme {
                NetlifyScreen(
                    state = NetlifyUiState(
                        status = NetlifyConnectionStatus.DISCONNECTED,
                        operation = null,
                        routeVisible = true,
                    ),
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onOpenSite = {},
                    onRefreshSite = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("netlify.connectionForm").assertIsDisplayed()
        composeRule.onNodeWithTag("netlify.token").assertIsDisplayed()
        composeRule.onNodeWithTag("netlify.connect").assertIsDisplayed()
        composeRule.onNodeWithText("Sites, deploys and builds without risky controls")
            .assertIsDisplayed()
    }

    @Test
    fun partialSiteWorkspaceKeepsIndependentResourceWarningsVisible() {
        composeRule.setContent {
            VercelticsTheme {
                NetlifyScreen(
                    state = NetlifyUiState(
                        status = NetlifyConnectionStatus.CONNECTED,
                        dashboard = DASHBOARD,
                        savedAccount = DASHBOARD.account,
                        operation = null,
                        selectedSiteId = SITE.id,
                        selectedSiteWorkspace = PARTIAL_SITE_WORKSPACE,
                    ),
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onOpenSite = {},
                    onRefreshSite = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        val siteDetail = composeRule.onNodeWithTag("netlify.siteDetail").assertIsDisplayed()
        composeRule.onNodeWithText("Domains & build controls").assertIsDisplayed()
        siteDetail.performScrollToNode(hasText("Repository  https://github.com/example/project"))
        composeRule.onNodeWithText("Repository  https://github.com/example/project")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasText("Repository path  apps/site"))
        composeRule.onNodeWithText("Repository path  apps/site")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasText("Allowed branches  main, preview"))
        composeRule.onNodeWithText("Allowed branches  main, preview")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasTestTag("netlify.publishedDeployment.published-1"))
        composeRule.onNodeWithTag("netlify.publishedDeployment.published-1")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasText("Deploy history is incomplete."))
        composeRule.onNodeWithText("Deploy history is incomplete.")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasTestTag("netlify.deploy.deploy-1"))
        composeRule.onNodeWithTag("netlify.deploy.deploy-1")
            .assertIsDisplayed()
        siteDetail.performScrollToNode(hasText("Build history is unavailable."))
        composeRule.onNodeWithText("Build history is unavailable.")
            .assertIsDisplayed()
    }

    @Test
    fun searchRequestFocusesAndFiltersTheSiteList() {
        var searchRequestId by mutableIntStateOf(0)
        composeRule.setContent {
            VercelticsTheme {
                NetlifyScreen(
                    state = NetlifyUiState(
                        status = NetlifyConnectionStatus.CONNECTED,
                        dashboard = DASHBOARD,
                        savedAccount = DASHBOARD.account,
                        operation = null,
                    ),
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onOpenSite = {},
                    onRefreshSite = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                    searchFocusRequestId = searchRequestId,
                )
            }
        }

        composeRule.runOnIdle { searchRequestId += 1 }
        composeRule.onNodeWithTag("netlify.search").assertIsFocused()
        composeRule.onNodeWithTag("netlify.search").performTextInput("missing")
        composeRule.onNodeWithText("No Netlify sites match “missing”.").assertIsDisplayed()
    }

    @Test
    fun disconnectConfirmationUsesModalThemedDialog() {
        composeRule.setContent {
            VercelticsTheme {
                NetlifyScreen(
                    state = NetlifyUiState(
                        status = NetlifyConnectionStatus.CONNECTED,
                        dashboard = DASHBOARD,
                        savedAccount = DASHBOARD.account,
                        operation = null,
                        showDisconnectConfirmation = true,
                    ),
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onOpenSite = {},
                    onRefreshSite = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("netlify.disconnectDialog").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnect Netlify?").assertIsDisplayed()
    }

    @Test
    fun headerActionIsDisabledDuringSiteLoadWithoutRootOperation() {
        setConnectedScreen(
            NetlifyUiState(
                status = NetlifyConnectionStatus.CONNECTED,
                dashboard = DASHBOARD,
                savedAccount = DASHBOARD.account,
                operation = null,
                selectedSiteId = SITE.id,
                isLoadingSite = true,
            ),
        )

        composeRule.onNodeWithTag("netlify.refreshOrCancel").assertIsNotEnabled()
    }

    @Test
    fun headerActionIsDisabledDuringNonCancelableOperationWithoutSiteLoad() {
        setConnectedScreen(
            NetlifyUiState(
                status = NetlifyConnectionStatus.CONNECTED,
                dashboard = DASHBOARD,
                savedAccount = DASHBOARD.account,
                operation = NetlifyOperation.DISCONNECTING,
                isLoadingSite = false,
            ),
        )

        composeRule.onNodeWithTag("netlify.refreshOrCancel").assertIsNotEnabled()
    }

    @Test
    fun headerActionRemainsEnabledForCancelableOperation() {
        setConnectedScreen(
            NetlifyUiState(
                status = NetlifyConnectionStatus.CONNECTED,
                dashboard = DASHBOARD,
                savedAccount = DASHBOARD.account,
                operation = NetlifyOperation.REFRESHING,
                isLoadingSite = false,
            ),
        )

        composeRule.onNodeWithTag("netlify.refreshOrCancel").assertIsEnabled()
    }

    private fun setConnectedScreen(state: NetlifyUiState) {
        composeRule.setContent {
            VercelticsTheme {
                NetlifyScreen(
                    state = state,
                    onBack = {},
                    onConnect = {},
                    onRefresh = {},
                    onCancel = {},
                    onOpenSite = {},
                    onRefreshSite = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                )
            }
        }
    }

    private companion object {
        val SITE = NetlifySiteUi(
            id = "site-1",
            name = "Example",
            subtitle = "example.netlify.app",
            url = "https://example.netlify.app",
            status = "current",
            updatedAtMillis = 42L,
        )
        val DASHBOARD = NetlifyDashboardUi(
            account = NetlifyAccountUi("account-1", "Example Account", "owner@example.com"),
            sites = listOf(SITE),
            loadedSiteCount = 1,
            providerInventoryComplete = true,
            inventoryTruncatedForDisplay = false,
            warnings = emptyList(),
            fetchedAtMillis = 42L,
            cacheState = NetlifyCacheState.LIVE,
        )
        val PARTIAL_SITE_WORKSPACE = NetlifySiteWorkspaceUi(
            siteId = SITE.id,
            details = NetlifyResourceUi.Available(
                NetlifySiteDetailsUi(
                    site = SITE,
                    domains = listOf(NetlifyDomainUi("example.com", "CUSTOM")),
                    buildControls = NetlifyBuildControlsUi(
                        buildsStopped = false,
                        repositoryUrl = "https://github.com/example/project",
                        repositoryPath = "apps/site",
                        repositoryBranch = "main",
                        baseDirectory = null,
                        publishDirectory = "dist",
                        functionsDirectory = null,
                        buildCommand = "npm run build",
                        allowedBranches = listOf("main", "preview"),
                        provider = "github",
                    ),
                    publishedDeployment = NetlifyDeploymentUi(
                        id = "published-1",
                        title = "Published deploy",
                        status = "ready",
                        createdAtMillis = 42L,
                        url = "https://example.netlify.app",
                        branch = "main",
                        commitMessage = "Publish",
                    ),
                ),
            ),
            deployments = NetlifyCollectionUi(
                items = listOf(
                    NetlifyDeploymentUi(
                        id = "deploy-1",
                        title = "Production deploy",
                        status = "ready",
                        createdAtMillis = 42L,
                        url = null,
                        branch = "main",
                        commitMessage = null,
                    ),
                ),
                loadedItemCount = 1,
                providerCollectionComplete = false,
                truncatedForDisplay = false,
                warning = "Deploy history is incomplete.",
            ),
            builds = NetlifyCollectionUi(
                items = emptyList(),
                loadedItemCount = 0,
                providerCollectionComplete = false,
                truncatedForDisplay = false,
                warning = "Build history is unavailable.",
            ),
        )
    }
}
