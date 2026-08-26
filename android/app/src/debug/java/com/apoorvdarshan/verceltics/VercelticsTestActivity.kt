package com.apoorvdarshan.verceltics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.apoorvdarshan.verceltics.ui.DebugVercelGatewayController
import com.apoorvdarshan.verceltics.ui.DebugVercelScenario
import com.apoorvdarshan.verceltics.ui.DebugVercelUiGateway
import com.apoorvdarshan.verceltics.ui.VercelConnectionViewModel
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme

/** Isolated debug-only host used by instrumented UI tests. */
class VercelticsTestActivity : ComponentActivity() {
    private val vercelConnectionViewModel by viewModels<VercelConnectionViewModel> {
        VercelConnectionViewModel.Factory(DebugVercelUiGateway())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.getStringExtra(EXTRA_VERCEL_SCENARIO)
            ?.let { runCatching { DebugVercelScenario.valueOf(it) }.getOrNull() }
            ?.let { DebugVercelGatewayController.configure(it) }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VercelticsTheme {
                VercelticsApp(vercelConnectionViewModel = vercelConnectionViewModel)
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

    companion object {
        const val EXTRA_VERCEL_SCENARIO = "vercelScenario"
    }
}
