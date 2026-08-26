package com.apoorvdarshan.verceltics

import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.apoorvdarshan.verceltics.ui.DebugVercelGatewayController
import com.apoorvdarshan.verceltics.ui.DebugVercelScenario
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VercelticsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<VercelticsTestActivity>()

    @Before
    fun returnToDisconnectedHostingRoot() {
        closeSoftKeyboard()
        compose.activityRule.scenario.onActivity { activity ->
            activity.configureGateway(DebugVercelScenario.DISCONNECTED)
        }
        waitForTag("mainNavigation.hosting")
        compose.onNodeWithTag("mainNavigation.hosting").performClick()
        waitForTag("workspace.hosting.empty")
    }

    @Test
    fun connectedSearchFallsBackToHostingAndFocusesExactlyOnce() {
        configureGateway(DebugVercelScenario.CONNECTED)
        waitForTag("workspace.hosting.connected")

        compose.onNodeWithTag("mainNavigation.registrars").performClick()
        compose.onNodeWithTag("workspace.registrars.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.search").performClick()

        waitForTag("workspace.hosting.searchField")
        compose.onNodeWithTag("mainNavigation.hosting").assertIsSelected()
        compose.onNodeWithTag("workspace.hosting.searchField").assertIsFocused()

        compose.activityRule.scenario.recreate()

        waitForTag("workspace.hosting.connected")
        compose.onNodeWithTag("workspace.hosting.searchField")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, false))
    }

    @Test
    fun connectedProjectDetailAndSystemBackPreserveWorkspace() {
        configureGateway(DebugVercelScenario.CONNECTED)
        waitForTag("workspace.hosting.connected")

        compose.onNodeWithTag("workspace.hosting.project.test-project").performClick()
        compose.onNodeWithTag("workspace.hosting.projectDetail").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForTag("workspace.hosting.connected")
        compose.onNodeWithTag("workspace.hosting.project.test-project").assertIsDisplayed()
    }

    @Test
    fun offlineSavedAccountNeverShowsDisconnectedConnectState() {
        configureGateway(DebugVercelScenario.OFFLINE_SAVED)

        waitForTag("workspace.hosting.savedUnavailable")
        compose.onAllNodesWithTag("workspace.hosting.empty").assertCountEquals(0)
        compose.onAllNodesWithTag("workspace.hosting.connect").assertCountEquals(0)
    }

    @Test
    fun connectMutationSurvivesActivityRecreation() {
        configureGateway(
            scenario = DebugVercelScenario.DISCONNECTED,
            blockConnect = true,
        )
        openVercelDetail()
        compose.onNodeWithTag("vercel.token").performTextInput("temporary-token")
        compose.onNodeWithTag("vercel.connect").performClick()
        compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            DebugVercelGatewayController.isConnectStarted()
        }

        compose.activityRule.scenario.recreate()
        waitForTag("providerDetail.vercel")
        compose.activityRule.scenario.onActivity(VercelticsTestActivity::releaseConnect)

        waitForTag("vercel.disconnect")
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("workspace.hosting.connected")
    }

    @Test
    fun workspaceTabsShowTheirDisconnectedNativeRoots() {
        compose.onNodeWithTag("workspace.hosting.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.hosting").assertIsSelected()

        compose.onNodeWithTag("mainNavigation.registrars").performClick().assertIsSelected()
        compose.onNodeWithTag("workspace.registrars.empty").assertIsDisplayed()
        compose.onAllNodesWithTag("workspace.hosting.empty").assertCountEquals(0)

        compose.onNodeWithTag("mainNavigation.sites").performClick().assertIsSelected()
        compose.onNodeWithTag("workspace.sites.empty").assertIsDisplayed()
        compose.onAllNodesWithTag("workspace.registrars.empty").assertCountEquals(0)
    }

    @Test
    fun searchIsAContextualButtonAndNeverASelectedDestination() {
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val noSelectedState = SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)

        primaryNavigationTags.forEach { tag ->
            compose.onNodeWithTag(tag).assert(tabRole)
        }
        compose.onNodeWithTag("mainNavigation.search")
            .assert(buttonRole)
            .assert(noSelectedState)

        compose.onNodeWithTag("mainNavigation.sites").performClick()
        compose.onNodeWithTag("workspace.sites.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.about").performClick().assertIsSelected()
        compose.onNodeWithTag("about").assertIsDisplayed()

        compose.onNodeWithTag("mainNavigation.search").performClick()

        compose.onNodeWithTag("workspace.sites.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.sites").assertIsSelected()
        compose.onNodeWithTag("mainNavigation.about").assertIsNotSelected()
        compose.onNodeWithTag("mainNavigation.search").assert(noSelectedState)
        compose.onAllNodesWithTag("globalSearch").assertCountEquals(0)
    }

    @Test
    fun connectionCatalogOpensVercelDetailAndBackRestoresDock() {
        openVercelDetail()

        compose.onNodeWithTag("providerDetail.vercel").assertIsDisplayed()
        compose.onAllNodesWithTag("mainNavigation.dock").assertCountEquals(0)
        compose.activityRule.scenario.onActivity { activity ->
            check(
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
            )
        }

        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForTag("workspace.hosting.empty")
        compose.onNodeWithTag("mainNavigation.dock").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.hosting").assertIsSelected()
        compose.activityRule.scenario.onActivity { activity ->
            check(
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0,
            )
        }
    }

    @Test
    fun topLevelSystemBackDoesNotReplayTabSelections() {
        compose.onNodeWithTag("mainNavigation.sites").performClick()
        compose.onNodeWithTag("workspace.sites.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.registrars").performClick()
        compose.onNodeWithTag("workspace.registrars.empty").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            compose.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun tokenIsNotRestoredWithActivityState() {
        openVercelDetail()
        compose.onNodeWithTag("vercel.token").performTextInput("temporary-token")

        compose.activityRule.scenario.recreate()

        waitForTag("providerDetail.vercel")
        compose.onAllNodesWithTag("mainNavigation.dock").assertCountEquals(0)
        val editableText = compose.onNodeWithTag("vercel.token")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
        assertEquals("", editableText.text)
    }

    @Test
    fun dockTargetsDoNotOverlapAndDeadSpaceDoesNotClickThrough() {
        val primaryBounds = primaryNavigationTags.map { tag ->
            compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        }
        val searchBounds = compose.onNodeWithTag("mainNavigation.search")
            .fetchSemanticsNode()
            .boundsInRoot

        primaryBounds.zipWithNext().forEach { (left, right) ->
            check(left.right <= right.left) {
                "Primary dock touch targets overlap: $left and $right"
            }
        }
        check(primaryBounds.last().right <= searchBounds.left) {
            "Search overlaps the primary dock: ${primaryBounds.last()} and $searchBounds"
        }

        val dockNode = compose.onNodeWithTag("mainNavigation.dock")
        val dockBounds = dockNode.fetchSemanticsNode().boundsInRoot
        val contentBounds = compose.onNodeWithTag("workspace.hosting.empty")
            .fetchSemanticsNode()
            .boundsInRoot
        check(contentBounds.bottom <= dockBounds.top + 1f) {
            "Workspace content is laid out under the dock: $contentBounds and $dockBounds"
        }

        val primarySurfaceBounds = compose.onNodeWithTag("mainNavigation.primary")
            .fetchSemanticsNode()
            .boundsInRoot
        val gapCenterInRoot = Offset(
            x = (primarySurfaceBounds.right + searchBounds.left) / 2f,
            y = (primarySurfaceBounds.top + primarySurfaceBounds.bottom) / 2f,
        )
        dockNode.performTouchInput {
            click(
                Offset(
                    x = gapCenterInRoot.x - dockBounds.left,
                    y = gapCenterInRoot.y - dockBounds.top,
                ),
            )
        }

        compose.onNodeWithTag("workspace.hosting.empty").assertIsDisplayed()
        compose.onNodeWithTag("mainNavigation.hosting").assertIsSelected()
    }

    private fun openVercelDetail() {
        compose.onNodeWithTag("workspace.hosting.connect").performClick()
        waitForTag("connection.catalog.hosting")
        compose.onNodeWithTag("connection.category.hosting").assertIsSelected()
        compose.onNodeWithTag("provider.vercel").performClick()
        waitForTag("providerDetail.vercel")
    }

    private fun configureGateway(
        scenario: DebugVercelScenario,
        blockConnect: Boolean = false,
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.configureGateway(
                scenario = scenario,
                blockConnect = blockConnect,
            )
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000L
        val primaryNavigationTags = listOf(
            "mainNavigation.hosting",
            "mainNavigation.registrars",
            "mainNavigation.sites",
            "mainNavigation.about",
        )
    }
}
