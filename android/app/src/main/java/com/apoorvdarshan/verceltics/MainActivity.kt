package com.apoorvdarshan.verceltics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.apoorvdarshan.verceltics.ui.VercelConnectionViewModel
import com.apoorvdarshan.verceltics.ui.VercelticsApp
import com.apoorvdarshan.verceltics.ui.theme.VercelticsTheme

class MainActivity : ComponentActivity() {
    private val vercelGateway
        get() = (application as VercelticsApplication).vercelGateway
    private val vercelConnectionViewModel by viewModels<VercelConnectionViewModel> {
        VercelConnectionViewModel.Factory(vercelGateway)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VercelticsTheme {
                VercelticsApp(vercelConnectionViewModel = vercelConnectionViewModel)
            }
        }
    }

}
