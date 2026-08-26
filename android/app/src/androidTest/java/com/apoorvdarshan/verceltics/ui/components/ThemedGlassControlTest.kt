package com.apoorvdarshan.verceltics.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemedGlassControlTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun disabledControlDoesNotInvokeClick() {
        var clickCount = 0
        compose.setContent {
            MaterialTheme {
                ThemedGlassControl(
                    onClick = { clickCount += 1 },
                    enabled = false,
                    modifier = Modifier.size(56.dp),
                    testTag = "disabledGlass",
                ) {
                    Text("Disabled")
                }
            }
        }

        compose.onNodeWithTag("disabledGlass")
            .assertIsNotEnabled()
            .performClick()

        compose.runOnIdle { assertEquals(0, clickCount) }
    }
}
