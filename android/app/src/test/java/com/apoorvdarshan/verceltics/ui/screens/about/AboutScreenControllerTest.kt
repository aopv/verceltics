package com.apoorvdarshan.verceltics.ui.screens.about

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AboutScreenControllerTest {
    @Test
    fun `stored appearance values are stable and unknown values fall back to system`() {
        assertEquals(AboutAppearance.SYSTEM, AboutAppearance.fromStoredValue(null))
        assertEquals(AboutAppearance.SYSTEM, AboutAppearance.fromStoredValue("future-mode"))
        assertEquals(AboutAppearance.LIGHT, AboutAppearance.fromStoredValue("light"))
        assertEquals(AboutAppearance.DARK, AboutAppearance.fromStoredValue("dark"))
    }

    @Test
    fun `controller loads and persists appearance through abstraction`() {
        val store = FakeAppearanceStore(AboutAppearance.DARK)
        val controller = controller(store = store)

        assertEquals(AboutAppearance.DARK, controller.state.appearance)

        controller.selectAppearance(AboutAppearance.LIGHT)

        assertEquals(AboutAppearance.LIGHT, controller.state.appearance)
        assertEquals(listOf(AboutAppearance.LIGHT), store.saved)

        controller.selectAppearance(AboutAppearance.LIGHT)
        assertEquals(listOf(AboutAppearance.LIGHT), store.saved)
    }

    @Test
    fun `a new root controller restores the persisted appearance`() {
        val store = FakeAppearanceStore(AboutAppearance.SYSTEM)
        controller(store = store).selectAppearance(AboutAppearance.DARK)

        val recreatedController = controller(store = store)

        assertEquals(AboutAppearance.DARK, recreatedController.state.appearance)
    }

    @Test
    fun `failed preference write does not claim that appearance changed`() {
        val store = FakeAppearanceStore(AboutAppearance.SYSTEM, failOnSave = true)
        val controller = controller(store = store)

        controller.selectAppearance(AboutAppearance.DARK)

        assertEquals(AboutAppearance.SYSTEM, controller.state.appearance)
    }

    @Test
    fun `unconfigured update checker stays truthful and is never called`() = runTest {
        val checker = FakeUpdateChecker(isConfigured = false, result = AboutUpdateResult.Current)
        val controller = controller(checker = checker)

        assertEquals(AboutUpdateState.NotConfigured, controller.state.update)
        controller.checkForUpdates()

        assertEquals(AboutUpdateState.NotConfigured, controller.state.update)
        assertEquals(0, checker.callCount)
    }

    @Test
    fun `configured checker maps current and available results`() = runTest {
        val currentController = controller(
            checker = FakeUpdateChecker(isConfigured = true, result = AboutUpdateResult.Current),
        )
        currentController.checkForUpdates()
        assertEquals(AboutUpdateState.Current("3.0"), currentController.state.update)

        val availableController = controller(
            checker = FakeUpdateChecker(
                isConfigured = true,
                result = AboutUpdateResult.Available(
                    latestVersion = "3.1",
                    destinationUri = "https://play.google.com/store/apps/details?id=com.apoorvdarshan.verceltics",
                ),
            ),
        )
        availableController.checkForUpdates()
        assertEquals(
            AboutUpdateState.Available(
                latestVersion = "3.1",
                destinationUri = "https://play.google.com/store/apps/details?id=com.apoorvdarshan.verceltics",
            ),
            availableController.state.update,
        )
    }

    @Test
    fun `checker exceptions become retryable failure state without leaking details`() = runTest {
        val controller = controller(checker = ThrowingUpdateChecker)

        controller.checkForUpdates()

        assertEquals(
            AboutUpdateState.Failed("Unable to check right now"),
            controller.state.update,
        )
    }

    @Test
    fun `version metadata rejects invalid values`() {
        assertThrows(IllegalArgumentException::class.java) { AboutAppVersion("", 42) }
        assertThrows(IllegalArgumentException::class.java) { AboutAppVersion("3.0", -1) }
    }

    private fun controller(
        store: AppearancePreferenceStore = FakeAppearanceStore(AboutAppearance.SYSTEM),
        checker: AboutUpdateChecker = FakeUpdateChecker(
            isConfigured = false,
            result = AboutUpdateResult.Current,
        ),
    ) = AboutScreenController(
        appearanceStore = store,
        updateChecker = checker,
        version = AboutAppVersion(name = "3.0", code = 42),
    )

    private class FakeAppearanceStore(
        initial: AboutAppearance,
        private val failOnSave: Boolean = false,
    ) : AppearancePreferenceStore {
        val saved = mutableListOf<AboutAppearance>()
        private var current = initial

        override fun load(): AboutAppearance = current

        override fun save(appearance: AboutAppearance) {
            if (failOnSave) error("disk unavailable")
            saved += appearance
            current = appearance
        }
    }

    private class FakeUpdateChecker(
        override val isConfigured: Boolean,
        private val result: AboutUpdateResult,
    ) : AboutUpdateChecker {
        var callCount = 0

        override suspend fun check(currentVersion: AboutAppVersion): AboutUpdateResult {
            callCount += 1
            return result
        }
    }

    private object ThrowingUpdateChecker : AboutUpdateChecker {
        override val isConfigured: Boolean = true

        override suspend fun check(currentVersion: AboutAppVersion): AboutUpdateResult {
            error("sensitive backend detail")
        }
    }
}
