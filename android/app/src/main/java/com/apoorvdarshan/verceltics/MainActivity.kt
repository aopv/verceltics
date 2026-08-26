package com.apoorvdarshan.verceltics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.rememberCoroutineScope
import com.apoorvdarshan.verceltics.ui.VercelConnectionViewModel
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenAction
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenController
import com.apoorvdarshan.verceltics.ui.screens.about.SharedPreferencesAppearancePreferenceStore
import com.apoorvdarshan.verceltics.ui.screens.about.UnconfiguredAboutUpdateChecker
import com.apoorvdarshan.verceltics.ui.screens.about.currentAndroidAppVersion
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vercelGateway
        get() = (application as VercelticsApplication).vercelGateway
    private val vercelConnectionViewModel by viewModels<VercelConnectionViewModel> {
        VercelConnectionViewModel.Factory(vercelGateway)
    }
    private val aboutController by lazy(LazyThreadSafetyMode.NONE) {
        AboutScreenController(
            appearanceStore = SharedPreferencesAppearancePreferenceStore(this),
            updateChecker = UnconfiguredAboutUpdateChecker,
            version = currentAndroidAppVersion(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val aboutState = aboutController.state
            val aboutScope = rememberCoroutineScope()
            VercelticsTheme(appearance = aboutState.appearance) {
                VercelticsApp(
                    vercelConnectionViewModel = vercelConnectionViewModel,
                    aboutState = aboutState,
                    onAboutAction = { dispatchAboutAction(it, aboutScope) },
                )
            }
        }
    }

    private fun dispatchAboutAction(action: AboutScreenAction, scope: CoroutineScope) {
        when (action) {
            is AboutScreenAction.SelectAppearance -> aboutController.selectAppearance(action.appearance)
            AboutScreenAction.CheckForUpdates -> scope.launch { aboutController.checkForUpdates() }
            is AboutScreenAction.OpenDestination -> openAboutUri(action.destination.uri)
            is AboutScreenAction.OpenExternalUri -> openAboutUri(action.uri)
            AboutScreenAction.ShareApp -> shareApp()
        }
    }

    private fun openAboutUri(uri: String) {
        val parsedUri = Uri.parse(uri)
        if (parsedUri.scheme !in SUPPORTED_ABOUT_URI_SCHEMES) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, parsedUri)) }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_message))
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.about_share)))
        }
    }

    private companion object {
        val SUPPORTED_ABOUT_URI_SCHEMES = setOf("https", "mailto", "market")
    }
}
