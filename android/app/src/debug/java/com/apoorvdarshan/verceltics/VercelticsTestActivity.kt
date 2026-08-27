package com.apoorvdarshan.verceltics

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apoorvdarshan.verceltics.ui.DebugVercelGatewayController
import com.apoorvdarshan.verceltics.ui.DebugVercelScenario
import com.apoorvdarshan.verceltics.ui.DebugVercelUiGateway
import com.apoorvdarshan.verceltics.ui.VercelConnectionViewModel
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.netlify.DebugNetlifyGatewayController
import com.apoorvdarshan.verceltics.ui.netlify.DebugNetlifyScenario
import com.apoorvdarshan.verceltics.ui.netlify.DebugNetlifyUiGateway
import com.apoorvdarshan.verceltics.ui.netlify.NetlifyViewModel
import com.apoorvdarshan.verceltics.ui.pagespeed.DebugPageSpeedGatewayController
import com.apoorvdarshan.verceltics.ui.pagespeed.DebugPageSpeedScenario
import com.apoorvdarshan.verceltics.ui.pagespeed.DebugPageSpeedUiGateway
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedViewModel
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Isolated debug-only host used by instrumented UI tests. */
class VercelticsTestActivity : ComponentActivity() {
    private val vercelConnectionViewModel by viewModels<VercelConnectionViewModel> {
        VercelConnectionViewModel.Factory(DebugVercelUiGateway())
    }
    private val pageSpeedViewModel by viewModels<PageSpeedViewModel> {
        PageSpeedViewModel.Factory(DebugPageSpeedUiGateway())
    }
    private val netlifyViewModel by viewModels<NetlifyViewModel> {
        NetlifyViewModel.Factory(DebugNetlifyUiGateway())
    }
    private var ownsNetlifySecureFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.getStringExtra(EXTRA_VERCEL_SCENARIO)
            ?.let { runCatching { DebugVercelScenario.valueOf(it) }.getOrNull() }
            ?.let { DebugVercelGatewayController.configure(it) }
        intent.getStringExtra(EXTRA_NETLIFY_SCENARIO)
            ?.let { runCatching { DebugNetlifyScenario.valueOf(it) }.getOrNull() }
            ?.let { DebugNetlifyGatewayController.configure(it) }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                netlifyViewModel.uiState
                    .map { it.requiresSecureWindow }
                    .distinctUntilChanged()
                    .collect { required ->
                        val flag = WindowManager.LayoutParams.FLAG_SECURE
                        if (required && window.attributes.flags and flag == 0) {
                            window.addFlags(flag)
                            ownsNetlifySecureFlag = true
                        } else if (!required && ownsNetlifySecureFlag) {
                            window.clearFlags(flag)
                            ownsNetlifySecureFlag = false
                        }
                    }
            }
        }
        setContent {
            VercelticsTheme {
                VercelticsApp(
                    vercelConnectionViewModel = vercelConnectionViewModel,
                    pageSpeedViewModel = pageSpeedViewModel,
                    netlifyViewModel = netlifyViewModel,
                )
            }
        }
    }

    fun configureGateway(
        scenario: DebugVercelScenario,
        blockConnect: Boolean = false,
    ) {
        DebugVercelGatewayController.configure(
            scenario = scenario,
            blockConnect = blockConnect,
        )
        vercelConnectionViewModel.restore()
    }

    fun releaseConnect() {
        DebugVercelGatewayController.releaseConnect()
    }

    fun configurePageSpeedGateway(scenario: DebugPageSpeedScenario) {
        DebugPageSpeedGatewayController.configure(scenario)
        pageSpeedViewModel.restore()
    }

    fun configureNetlifyGateway(
        scenario: DebugNetlifyScenario,
        blockConnect: Boolean = false,
    ) {
        DebugNetlifyGatewayController.configure(scenario, blockConnect)
        netlifyViewModel.restore()
    }

    fun releaseNetlifyConnect() {
        DebugNetlifyGatewayController.releaseConnect()
    }

    companion object {
        const val EXTRA_VERCEL_SCENARIO = "vercelScenario"
        const val EXTRA_NETLIFY_SCENARIO = "netlifyScenario"
    }
}
