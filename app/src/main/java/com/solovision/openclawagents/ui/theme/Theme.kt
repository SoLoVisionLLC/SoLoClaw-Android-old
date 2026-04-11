package com.solovision.openclawagents.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White

private val OpenClawDarkColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = White,
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFF5F7FB),
    surface = Color(0xFF0F1724),
    onSurface = Color(0xFFF5F7FB),
    surfaceVariant = Color(0xFF182235),
    onSurfaceVariant = Color(0xFFA6B0C3),
    outline = Color(0xFF29354A)
)

@Composable
fun OpenClawAgentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OpenClawDarkColors,
        typography = Typography,
        content = content
    )
}
