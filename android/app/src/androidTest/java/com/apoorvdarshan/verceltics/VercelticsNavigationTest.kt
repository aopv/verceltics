package com.apoorvdarshan.verceltics

import android.view.WindowManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VercelticsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<VercelticsTestActivity>()

    @Before
    fun returnToHosting() {
        closeSoftKeyboard()
        compose.onNodeWithTag("nav.hosting").performClick()
        compose.onNodeWithTag("catalog.hosting").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationSwitchesExactlyOneWorkspace() {
        compose.onNodeWithTag("catalog.hosting").assertIsDisplayed()

        compose.onNodeWithTag("nav.sites").performClick().assertIsSelected()

        compose.onNodeWithTag("catalog.sites").assertIsDisplayed()
        compose.onAllNodesWithTag("catalog.hosting").assertCountEquals(0)
    }

    @Test
    fun searchDestinationOwnsTheForeground() {
        compose.onNodeWithTag("nav.search").performClick().assertIsSelected()

        compose.onNodeWithTag("globalSearch").assertIsDisplayed()
        compose.onNodeWithTag("globalSearch.field").assertIsDisplayed()
    }

    @Test
    fun providerDetailsReturnToCatalog() {
        compose.onNodeWithTag("provider.vercel").performClick()
        compose.onNodeWithTag("providerDetail.vercel").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }

        compose.onNodeWithTag("providerDetail.back").performClick()
        compose.onNodeWithTag("catalog.hosting").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
        }
    }

    @Test
    fun providerBackPreservesGlobalSearchQuery() {
        compose.onNodeWithTag("nav.search").performClick()
        compose.onNodeWithTag("globalSearch.field").performTextReplacement("cloud")
        compose.onNodeWithTag("provider.cloudflare").performClick()

        compose.onNodeWithTag("providerDetail.back").performClick()

        compose.onNodeWithTag("globalSearch.field").assertTextEquals("cloud")
    }

    @Test
    fun systemBackReturnsToPreviousTopLevelDestination() {
        compose.onNodeWithTag("nav.sites").performClick()
        compose.onNodeWithTag("nav.registrars").performClick()

        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        compose.onNodeWithTag("catalog.sites").assertIsDisplayed()
        compose.onNodeWithTag("nav.sites").assertIsSelected()
    }

    @Test
    fun longNavigationHistoryStillReturnsToHostingRoot() {
        repeat(7) {
            compose.onNodeWithTag("nav.sites").performClick()
            compose.onNodeWithTag("nav.hosting").performClick()
            compose.onNodeWithTag("nav.registrars").performClick()
        }

        repeat(10) {
            compose.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        compose.onNodeWithTag("catalog.hosting").assertIsDisplayed()
        compose.onNodeWithTag("nav.hosting").assertIsSelected()
    }

    @Test
    fun dockItemsExposeTabSemanticsAndDoNotOverlap() {
        val tags = listOf("hosting", "registrars", "sites", "about", "search")
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val bounds = tags.map { id ->
            compose.onNodeWithTag("nav.$id")
                .assert(tabRole)
                .fetchSemanticsNode()
                .boundsInRoot
        }

        bounds.zipWithNext().forEach { (left, right) ->
            check(left.right <= right.left) { "Dock touch targets overlap: $left and $right" }
        }
    }

    @Test
    fun tokenIsNotRestoredWithActivityState() {
        compose.onNodeWithTag("provider.vercel").performClick()
        compose.onNodeWithTag("vercel.token").performTextInput("temporary-token")

        compose.activityRule.scenario.recreate()

        val editableText = compose.onNodeWithTag("vercel.token")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
        assertEquals("", editableText.text)
    }
}
