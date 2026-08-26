package com.apoorvdarshan.verceltics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apoorvdarshan.verceltics.ui.UnconfiguredVercelUiGateway
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme

/** Isolated debug-only host used by instrumented UI tests. */
class VercelticsTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VercelticsTheme {
                VercelticsApp(vercelGateway = UnconfiguredVercelUiGateway)
            }
        }
    }
}
