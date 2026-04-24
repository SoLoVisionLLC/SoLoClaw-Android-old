package com.solovision.openclawagents.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Dashboard(route = "dashboard", label = "Home", icon = Icons.Default.Home),
    Agents(route = "agents", label = "Agents", icon = Icons.Default.SmartToy),
    Chat(route = "chat", label = "Chat", icon = Icons.Default.ChatBubbleOutline),
    Cron(route = "cron", label = "Cron", icon = Icons.Default.Schedule),
    Skills(route = "skills", label = "Skills", icon = Icons.Default.Build),
    Settings(route = "settings", label = "Settings", icon = Icons.Default.Settings)
}

val primaryDestinations = listOf(
    AppDestination.Dashboard,
    AppDestination.Agents,
    AppDestination.Chat,
    AppDestination.Cron,
    AppDestination.Skills,
    AppDestination.Settings
)
