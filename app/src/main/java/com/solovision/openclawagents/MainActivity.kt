package com.solovision.openclawagents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solovision.openclawagents.ui.OpenClawAgentsApp
import com.solovision.openclawagents.ui.theme.OpenClawAgentsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenClawAgentsTheme {
                OpenClawAgentsApp()
            }
        }
    }
}
