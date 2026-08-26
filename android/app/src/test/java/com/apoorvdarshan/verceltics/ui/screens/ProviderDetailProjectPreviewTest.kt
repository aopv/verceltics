package com.apoorvdarshan.verceltics.ui.screens

import com.apoorvdarshan.verceltics.ui.VercelProjectUi
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDetailProjectPreviewTest {
    @Test
    fun connectionPanelCapsLargeProjectAccountsWhileWorkspaceRemainsComplete() {
        val projects = (1..150).map { index ->
            VercelProjectUi(
                id = "project-$index",
                name = "Project $index",
                framework = null,
                updatedAtMillis = null,
            )
        }

        val preview = providerDetailProjectPreview(projects)

        assertEquals(12, preview.size)
        assertEquals("project-1", preview.first().id)
        assertEquals("project-12", preview.last().id)
        assertEquals(150, projects.size)
    }
}
