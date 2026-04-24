package com.solovision.openclawagents

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.solovision.openclawagents.ui.OpenClawAgentsApp

class MainActivity : ComponentActivity() {
    private val notificationTarget = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget.value = intent?.getStringExtra(EXTRA_NOTIFICATION_TARGET)
        enableEdgeToEdge()
        setContent {
            OpenClawAgentsApp(
                notificationTarget = notificationTarget.value,
                onNotificationTargetConsumed = { notificationTarget.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = intent.getStringExtra(EXTRA_NOTIFICATION_TARGET)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TARGET = "notification_target"
    }
}
