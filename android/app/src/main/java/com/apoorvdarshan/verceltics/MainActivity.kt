package com.apoorvdarshan.verceltics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apoorvdarshan.verceltics.ui.VercelConnectionViewModel
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.cloudflare.CloudflareViewModel
import com.apoorvdarshan.verceltics.ui.netlify.NetlifyViewModel
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedViewModel
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenAction
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenController
import com.apoorvdarshan.verceltics.ui.screens.about.SharedPreferencesAppearancePreferenceStore
import com.apoorvdarshan.verceltics.ui.screens.about.UnconfiguredAboutUpdateChecker
import com.apoorvdarshan.verceltics.ui.screens.about.currentAndroidAppVersion
import com.apoorvdarshan.verceltics.ui.searchconsole.SearchConsoleViewModel
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vercelGateway
        get() = (application as VercelticsApplication).vercelGateway
    private val vercelConnectionViewModel by viewModels<VercelConnectionViewModel> {
        VercelConnectionViewModel.Factory(vercelGateway)
    }
    private val pageSpeedGateway
        get() = (application as VercelticsApplication).pageSpeedGateway
    private val pageSpeedViewModel by viewModels<PageSpeedViewModel> {
        PageSpeedViewModel.Factory(pageSpeedGateway)
    }
    private val netlifyGateway
        get() = (application as VercelticsApplication).netlifyGateway
    private val netlifyViewModel by viewModels<NetlifyViewModel> {
        NetlifyViewModel.Factory(netlifyGateway)
    }
    private val cloudflareGateway
        get() = (application as VercelticsApplication).cloudflareGateway
    private val cloudflareViewModel by viewModels<CloudflareViewModel> {
        CloudflareViewModel.Factory(cloudflareGateway)
    }
    private val searchConsoleGateway
        get() = (application as VercelticsApplication).searchConsoleGateway
    private val searchConsoleViewModel by viewModels<SearchConsoleViewModel> {
        SearchConsoleViewModel.Factory(searchConsoleGateway)
    }
    private var ownsProviderSecureFlag = false
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
        observeProviderCredentialProtection()
        setContent {
            val aboutState = aboutController.state
            val aboutScope = rememberCoroutineScope()
            VercelticsTheme(appearance = aboutState.appearance) {
                VercelticsApp(
                    vercelConnectionViewModel = vercelConnectionViewModel,
                    pageSpeedViewModel = pageSpeedViewModel,
                    netlifyViewModel = netlifyViewModel,
                    cloudflareViewModel = cloudflareViewModel,
                    searchConsoleViewModel = searchConsoleViewModel,
                    aboutState = aboutState,
                    onAboutAction = { dispatchAboutAction(it, aboutScope) },
                )
            }
        }
    }

    private fun observeProviderCredentialProtection() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    netlifyViewModel.uiState.map { it.requiresSecureWindow },
                    cloudflareViewModel.uiState.map { it.requiresSecureWindow },
                    searchConsoleViewModel.uiState.map { it.requiresSecureWindow },
                ) { netlifyRequired, cloudflareRequired, searchConsoleRequired ->
                    netlifyRequired || cloudflareRequired || searchConsoleRequired
                }
                    .distinctUntilChanged()
                    .collect(::setProviderCredentialProtection)
            }
        }
    }

    private fun setProviderCredentialProtection(required: Boolean) {
        val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
        if (required) {
            if (window.attributes.flags and secureFlag == 0) {
                window.addFlags(secureFlag)
                ownsProviderSecureFlag = true
            }
        } else if (ownsProviderSecureFlag) {
            window.clearFlags(secureFlag)
            ownsProviderSecureFlag = false
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
