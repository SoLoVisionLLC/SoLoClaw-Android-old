package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.AppThemeMode
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.ui.theme.AppThemeCategory
import com.solovision.openclawagents.ui.theme.AppThemePalette
import com.solovision.openclawagents.ui.theme.appThemes
import com.solovision.openclawagents.ui.theme.getAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetMessageNotificationsEnabled: (Boolean) -> Unit,
    onSetCronNotificationsEnabled: (Boolean) -> Unit,
    onSetBackgroundSyncEnabled: (Boolean) -> Unit,
    onSetRoomNotificationsEnabled: (String, Boolean) -> Unit,
    onSetCronJobNotificationsEnabled: (String, Boolean) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onUpdateCartesiaApiKey: (String) -> Unit,
    onUpdateCartesiaModelId: (String) -> Unit,
    onUpdateKokoroEndpoint: (String) -> Unit,
    onUpdateKokoroApiKey: (String) -> Unit,
    onUpdateKokoroModel: (String) -> Unit,
    onUpdateLemonfoxApiKey: (String) -> Unit,
    onUpdateLemonfoxLanguage: (String) -> Unit,
    onUpdateLemonfoxSpeed: (String) -> Unit
) {
    val activeTheme = getAppTheme(uiState.themeMode)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsHeroCard()
            }
            item {
                SettingsCard(
                    title = "Notifications",
                    body = "Local Android notifications for new chat replies and cron activity, with per-conversation and per-job control.",
                    accent = Color(0xFF22C55E),
                    icon = Icons.Default.Notifications
                ) {
                    SettingsInfoRow(
                        icon = Icons.Default.Notifications,
                        label = "Android permission",
                        value = if (uiState.notifications.permissionGranted) "Granted" else "Not granted"
                    )
                    if (!uiState.notifications.permissionGranted) {
                        OutlinedButton(onClick = onRequestNotificationPermission) {
                            Text("Allow notifications")
                        }
                    }
                    NotificationToggleRow(
                        label = "Enable all notifications",
                        description = "Master switch for all local Android alerts.",
                        checked = uiState.notifications.enabled,
                        onCheckedChange = onSetNotificationsEnabled
                    )
                    NotificationToggleRow(
                        label = "Message notifications",
                        description = "New agent and group replies from rooms you leave enabled.",
                        checked = uiState.notifications.messageNotificationsEnabled,
                        onCheckedChange = onSetMessageNotificationsEnabled
                    )
                    NotificationToggleRow(
                        label = "Cron notifications",
                        description = "Cron run updates and failures from jobs you leave enabled.",
                        checked = uiState.notifications.cronNotificationsEnabled,
                        onCheckedChange = onSetCronNotificationsEnabled
                    )
                    NotificationToggleRow(
                        label = "Always-on background sync",
                        description = "Keeps a persistent Android background service alive when you leave the app so message and cron alerts can continue.",
                        checked = uiState.notifications.backgroundSyncEnabled,
                        onCheckedChange = onSetBackgroundSyncEnabled
                    )
                    NotificationSectionTitle("Conversations")
                    if (uiState.rooms.isEmpty()) {
                        Text(
                            "Conversation toggles will appear here after rooms load.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.rooms.forEach { room ->
                            NotificationToggleRow(
                                label = room.title,
                                description = listOfNotNull(
                                    room.sessionLabel,
                                    if (room.members.size > 1) "Group room" else "Direct room"
                                ).joinToString(" • "),
                                checked = uiState.notifications.isRoomEnabled(room.id),
                                onCheckedChange = { enabled ->
                                    onSetRoomNotificationsEnabled(room.id, enabled)
                                }
                            )
                        }
                    }
                    NotificationSectionTitle("Cron Jobs")
                    if (uiState.cron.jobs.isEmpty()) {
                        Text(
                            "Cron job toggles will appear here after the gateway sync completes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.cron.jobs.forEach { job ->
                            NotificationToggleRow(
                                label = job.name,
                                description = job.lastStatus ?: cronNotificationLabel(job),
                                checked = uiState.notifications.isCronJobEnabled(job.id),
                                onCheckedChange = { enabled ->
                                    onSetCronJobNotificationsEnabled(job.id, enabled)
                                }
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard(
                    title = "Appearance",
                    body = "Choose from the full Hermes mission-control theme catalog.",
                    accent = Color(0xFF3B82F6),
                    icon = Icons.Default.Contrast
                ) {
                    SettingsInfoRow(
                        icon = Icons.Default.Contrast,
                        label = "Active theme",
                        value = activeTheme.name
                    )
                    ThemeCategorySection(
                        title = "Dark Themes",
                        themes = appThemes.filter { it.category == AppThemeCategory.Dark },
                        selectedMode = uiState.themeMode,
                        onSelectMode = onSetThemeMode
                    )
                    ThemeCategorySection(
                        title = "Light Themes",
                        themes = appThemes.filter { it.category == AppThemeCategory.Light },
                        selectedMode = uiState.themeMode,
                        onSelectMode = onSetThemeMode
                    )
                    ThemeCategorySection(
                        title = "Special Themes",
                        themes = appThemes.filter { it.category == AppThemeCategory.Special },
                        selectedMode = uiState.themeMode,
                        onSelectMode = onSetThemeMode
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Cartesia",
                    body = "Shared Cartesia credentials and defaults for room playback and agent voices.",
                    accent = Color(0xFF7C5CFF),
                    icon = Icons.Default.VoiceChat
                ) {
                    SettingsInfoRow(
                        icon = Icons.Default.Key,
                        label = "Cloud voice authentication",
                        value = if (uiState.voiceSettings.cartesiaApiKey.isBlank()) "Not configured" else "Configured"
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.cartesiaApiKey,
                        onValueChange = onUpdateCartesiaApiKey,
                        label = { Text("Cartesia API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.cartesiaModelId,
                        onValueChange = onUpdateCartesiaModelId,
                        label = { Text("Default Cartesia model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Kokoro",
                    body = "Self-hosted Kokoro endpoint and model settings shared across the app.",
                    accent = Color(0xFF2DD4BF),
                    icon = Icons.Default.SettingsEthernet
                ) {
                    SettingsInfoRow(
                        icon = Icons.Default.SettingsEthernet,
                        label = "Endpoint status",
                        value = if (uiState.voiceSettings.kokoroEndpoint.isBlank()) "Not configured" else "Configured"
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroEndpoint,
                        onValueChange = onUpdateKokoroEndpoint,
                        label = { Text("Kokoro endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroApiKey,
                        onValueChange = onUpdateKokoroApiKey,
                        label = { Text("Kokoro API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroModel,
                        onValueChange = onUpdateKokoroModel,
                        label = { Text("Default Kokoro model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Lemonfox",
                    body = "Shared Lemonfox credentials and defaults for app voice playback.",
                    accent = Color(0xFFF59E0B),
                    icon = Icons.Default.Tune
                ) {
                    SettingsInfoRow(
                        icon = Icons.Default.Tune,
                        label = "Playback defaults",
                        value = "Language ${uiState.voiceSettings.lemonfoxLanguage.ifBlank { "en-us" }}"
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxApiKey,
                        onValueChange = onUpdateLemonfoxApiKey,
                        label = { Text("Lemonfox API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxLanguage,
                        onValueChange = onUpdateLemonfoxLanguage,
                        label = { Text("Default Lemonfox language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxSpeed,
                        onValueChange = onUpdateLemonfoxSpeed,
                        label = { Text("Default Lemonfox speed") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                content()
            }
        )
    }
}

@Composable
private fun ThemeCategorySection(
    title: String,
    themes: List<AppThemePalette>,
    selectedMode: AppThemeMode,
    onSelectMode: (AppThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        themes.forEach { theme ->
            ThemePreviewCard(
                theme = theme,
                selected = selectedMode == theme.id,
                onClick = { onSelectMode(theme.id) }
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: AppThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = theme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .background(colors.bgCard)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.bgPrimary,
                                colors.bgSecondary,
                                colors.bgTertiary,
                                colors.accent,
                                colors.success
                            )
                        )
                    )
            ) {
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .fillMaxWidth(0.72f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.textPrimary.copy(alpha = 0.20f))
                )
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .size(width = 42.dp, height = 18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accent)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(theme.name, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                Text(theme.description, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsHeroCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF10182A), Color(0xFF1B2440), Color(0xFF0B7A9A))
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    "Mission-control defaults for voice providers, keys, and playback behavior across the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD8E6F3)
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NotificationToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun cronNotificationLabel(job: com.solovision.openclawagents.model.CronJob): String {
    return when {
        !job.lastError.isNullOrBlank() -> "Latest run reported an error"
        !job.lastRunAt.isNullOrBlank() -> "Last run ${job.lastRunAt}"
        else -> "No run activity yet"
    }
}
