package com.solovision.openclawagents.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.selection.SelectionContainer
import com.solovision.openclawagents.data.isProtocolNoiseMessage
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProfile
import com.solovision.openclawagents.model.VoiceProvider
import com.solovision.openclawagents.model.VoiceSettings
import com.solovision.openclawagents.ui.components.AgentAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleVoiceSettings: (Boolean) -> Unit,
    onSetVoiceProvider: (VoiceProvider) -> Unit,
    onUpdateCartesiaApiKey: (String) -> Unit,
    onUpdateCartesiaModelId: (String) -> Unit,
    onRefreshVoiceOptions: () -> Unit,
    onSelectCartesiaVoice: (VoiceOption) -> Unit,
    onUpdateKokoroEndpoint: (String) -> Unit,
    onUpdateKokoroApiKey: (String) -> Unit,
    onUpdateKokoroModel: (String) -> Unit,
    onUpdateKokoroVoice: (String) -> Unit,
    onUpdateLemonfoxApiKey: (String) -> Unit,
    onUpdateLemonfoxLanguage: (String) -> Unit,
    onUpdateLemonfoxSpeed: (String) -> Unit,
    onSelectLemonfoxVoice: (VoiceOption) -> Unit,
    onSaveVoiceProfile: (String) -> Unit,
    onApplyVoiceProfile: (String) -> Unit,
    onDeleteVoiceProfile: (String) -> Unit,
    onTestVoice: () -> Unit,
    onPlayLatestMessage: () -> Unit,
    onPlayMessage: (RoomMessage) -> Unit,
    onStopPlayback: () -> Unit,
    onToggleInternalMessages: (Boolean) -> Unit,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit
) {
    val room = uiState.rooms.firstOrNull { it.id == uiState.selectedRoomId } ?: uiState.rooms.firstOrNull()
    val roomId = room?.id
    val allMessages = uiState.roomMessages[roomId].orEmpty()
    val visibleMessages = if (uiState.showInternalMessages) {
        allMessages
    } else {
        allMessages.filterNot { it.internal || it.senderType == MessageSenderType.SYSTEM }
    }
        .filterNot { isProtocolNoiseMessage(it.body) }
        .distinctBy { it.id.ifBlank { it.messageKey } }
    val listState = rememberLazyListState()
    var initialScrollPending by rememberSaveable(roomId, uiState.showInternalMessages) { mutableStateOf(true) }
    var sessionsExpanded by remember { mutableStateOf(false) }
    var confirmDeleteSession by remember(roomId) { mutableStateOf(false) }

    val directAgentId = room
        ?.members
        ?.singleOrNull()
        ?.takeIf { room.id.startsWith("agent:") }

    val directSessions = remember(uiState.rooms, directAgentId) {
        if (directAgentId == null) {
            emptyList()
        } else {
            uiState.rooms.filter { candidate ->
                candidate.id.startsWith("agent:") &&
                    candidate.members.singleOrNull()?.equals(directAgentId, ignoreCase = true) == true
            }
        }
    }
    val canDeleteCurrentSession = room?.id?.let(::isDeletableDirectSessionKey) == true

    if (confirmDeleteSession && room != null) {
        DeleteSessionDialog(
            sessionLabel = displaySessionLabel(room.sessionLabel),
            onDismiss = { confirmDeleteSession = false },
            onConfirmDelete = {
                confirmDeleteSession = false
                onDeleteSession(room.id)
            }
        )
    }

    if (uiState.ttsState.settingsExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onToggleVoiceSettings(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TtsControlCard(
                settings = uiState.voiceSettings,
                queueCount = uiState.ttsState.queueCount,
                isPlaying = uiState.ttsState.isPlaying,
                settingsExpanded = uiState.ttsState.settingsExpanded,
                isLoadingVoices = uiState.ttsState.isLoadingVoices,
                voiceOptions = uiState.ttsState.availableVoices,
                voiceLabel = uiState.ttsState.activeVoiceLabel,
                errorMessage = uiState.ttsState.errorMessage,
                onToggleSettings = onToggleVoiceSettings,
                onSetVoiceProvider = onSetVoiceProvider,
                onUpdateCartesiaApiKey = onUpdateCartesiaApiKey,
                onUpdateCartesiaModelId = onUpdateCartesiaModelId,
                onRefreshVoiceOptions = onRefreshVoiceOptions,
                onSelectCartesiaVoice = onSelectCartesiaVoice,
                onUpdateKokoroEndpoint = onUpdateKokoroEndpoint,
                onUpdateKokoroApiKey = onUpdateKokoroApiKey,
                onUpdateKokoroModel = onUpdateKokoroModel,
                onUpdateKokoroVoice = onUpdateKokoroVoice,
                onUpdateLemonfoxApiKey = onUpdateLemonfoxApiKey,
                onUpdateLemonfoxLanguage = onUpdateLemonfoxLanguage,
                onUpdateLemonfoxSpeed = onUpdateLemonfoxSpeed,
                onSelectLemonfoxVoice = onSelectLemonfoxVoice,
                savedProfiles = uiState.ttsState.savedProfiles,
                activeProfileId = uiState.ttsState.activeProfileId,
                onSaveVoiceProfile = onSaveVoiceProfile,
                onApplyVoiceProfile = onApplyVoiceProfile,
                onDeleteVoiceProfile = onDeleteVoiceProfile,
                onTestVoice = onTestVoice,
                onPlayLatestMessage = onPlayLatestMessage,
                onStopPlayback = onStopPlayback,
                embeddedInSheet = true
            )
        }
    }

    DisposableEffect(roomId) {
        if (roomId != null) {
            onStartPolling()
        }
        onDispose {
            onStopPolling()
        }
    }

    LaunchedEffect(roomId, visibleMessages.size, uiState.selectedRoomUnreadAnchorKey, uiState.showInternalMessages) {
        if (!initialScrollPending || visibleMessages.isEmpty()) return@LaunchedEffect
        val targetIndex = uiState.selectedRoomUnreadAnchorKey
            ?.let { anchorKey -> visibleMessages.indexOfFirst { it.messageKey == anchorKey } }
            ?.takeIf { it >= 0 }
            ?: visibleMessages.lastIndex
        listState.scrollToItem(targetIndex.coerceAtLeast(0))
        initialScrollPending = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(room?.title ?: "Room")
                        Text(
                            text = roomSubtitle(room, directSessions),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (room != null && directSessions.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { sessionsExpanded = true }) {
                                Text(displaySessionLabel(room.sessionLabel))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Change session"
                                )
                            }
                            DropdownMenu(
                                expanded = sessionsExpanded,
                                onDismissRequest = { sessionsExpanded = false }
                            ) {
                                directSessions.forEach { session ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(displaySessionLabel(session.sessionLabel))
                                                Text(
                                                    session.lastActivity,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            sessionsExpanded = false
                                            onSelectSession(session.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (canDeleteCurrentSession) {
                        IconButton(onClick = { confirmDeleteSession = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete session"
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleVoiceSettings(true) }
                    ) {
                        Icon(
                            imageVector = if (uiState.ttsState.isPlaying) {
                                Icons.Default.GraphicEq
                            } else {
                                Icons.Default.RecordVoiceOver
                            },
                            contentDescription = "Open voice controls"
                        )
                    }
                    IconButton(
                        onClick = { onToggleInternalMessages(!uiState.showInternalMessages) }
                    ) {
                        Icon(
                            imageVector = if (uiState.showInternalMessages) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (uiState.showInternalMessages) {
                                "Hide tool and thinking content"
                            } else {
                                "Show tool and thinking content"
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            ComposerBar(
                value = uiState.draftMessage,
                roomTitle = room?.title,
                onValueChange = onDraftChange,
                onSend = onSend
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            room?.let { activeRoom ->
                item {
                    RoomOverviewCard(
                        room = activeRoom,
                        visibleMessageCount = visibleMessages.size,
                        showingInternalMessages = uiState.showInternalMessages
                    )
                }
            }
            if (uiState.isWorking || uiState.errorMessage != null) {
                item {
                    StatusBanner(
                        message = uiState.errorMessage ?: "Talking to the OpenClaw gateway...",
                        isError = uiState.errorMessage != null
                    )
                }
            }
            if (visibleMessages.isEmpty()) {
                item {
                    EmptyRoomCard(
                        if (room == null) {
                            "Choose a room to start chatting."
                        } else {
                            "No visible messages yet. Send a message to get the conversation moving."
                        }
                    )
                }
            } else {
                items(
                    items = visibleMessages,
                    key = { message -> message.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        isActivePlayback = uiState.ttsState.currentMessageId == message.id,
                        isPausedPlayback = uiState.ttsState.currentMessageId == message.id && uiState.ttsState.isPaused,
                        onPlayMessage = { onPlayMessage(message) }
                    )
                }
            }
        }
    }
}

private fun isDeletableDirectSessionKey(roomId: String): Boolean {
    if (!roomId.startsWith("agent:")) return false
    return roomId.split(':').drop(2).firstOrNull()?.equals("main", ignoreCase = true) == false
}

private fun roomSubtitle(room: CollaborationRoom?, directSessions: List<CollaborationRoom>): String {
    if (room == null) return "No room selected"
    return when {
        room.id.startsWith("agent:") && directSessions.size > 1 -> "Session: ${displaySessionLabel(room.sessionLabel)}"
        room.id.startsWith("agent:") -> room.purpose
        room.members.isNotEmpty() -> room.members.joinToString(" | ")
        else -> room.purpose
    }
}

private fun displaySessionLabel(sessionLabel: String?): String {
    return when {
        sessionLabel.isNullOrBlank() -> "Halo"
        sessionLabel.equals("main", ignoreCase = true) -> "Halo"
        else -> sessionLabel
    }
}

private fun VoiceProvider.label(): String = when (this) {
    VoiceProvider.System -> "System"
    VoiceProvider.Cartesia -> "Cartesia"
    VoiceProvider.Kokoro -> "Kokoro"
    VoiceProvider.Lemonfox -> "Lemonfox"
}

@Composable
private fun DeleteSessionDialog(
    sessionLabel: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete session") },
        text = {
            Text(
                "Delete \"$sessionLabel\" from OpenClaw? This removes that remote session but keeps the agent's main chat.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TtsControlCard(
    settings: VoiceSettings,
    queueCount: Int,
    isPlaying: Boolean,
    settingsExpanded: Boolean,
    isLoadingVoices: Boolean,
    voiceOptions: List<VoiceOption>,
    voiceLabel: String,
    errorMessage: String?,
    onToggleSettings: (Boolean) -> Unit,
    onSetVoiceProvider: (VoiceProvider) -> Unit,
    onUpdateCartesiaApiKey: (String) -> Unit,
    onUpdateCartesiaModelId: (String) -> Unit,
    onRefreshVoiceOptions: () -> Unit,
    onSelectCartesiaVoice: (VoiceOption) -> Unit,
    onUpdateKokoroEndpoint: (String) -> Unit,
    onUpdateKokoroApiKey: (String) -> Unit,
    onUpdateKokoroModel: (String) -> Unit,
    onUpdateKokoroVoice: (String) -> Unit,
    onUpdateLemonfoxApiKey: (String) -> Unit,
    onUpdateLemonfoxLanguage: (String) -> Unit,
    onUpdateLemonfoxSpeed: (String) -> Unit,
    onSelectLemonfoxVoice: (VoiceOption) -> Unit,
    savedProfiles: List<VoiceProfile>,
    activeProfileId: String?,
    onSaveVoiceProfile: (String) -> Unit,
    onApplyVoiceProfile: (String) -> Unit,
    onDeleteVoiceProfile: (String) -> Unit,
    onTestVoice: () -> Unit,
    onPlayLatestMessage: () -> Unit,
    onStopPlayback: () -> Unit,
    embeddedInSheet: Boolean = false
) {
    var providersExpanded by remember { mutableStateOf(false) }
    var voicesExpanded by remember { mutableStateOf(false) }
    var profilesExpanded by remember { mutableStateOf(false) }
    var profileDraft by remember(settings.provider) { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (embeddedInSheet) "Voice Controls" else "Room Voice",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Provider: ${settings.provider.label()} | Voice: $voiceLabel | Queue: $queueCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceActionChip(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "Test",
                        onClick = onTestVoice
                    )
                    VoiceActionChip(
                        icon = Icons.Default.PlayArrow,
                        label = "Latest",
                        onClick = onPlayLatestMessage
                    )
                    VoiceActionChip(
                        icon = Icons.Default.Stop,
                        label = if (isPlaying) "Stop" else "Ready",
                        onClick = if (isPlaying) onStopPlayback else null
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    VoiceActionChip(
                        icon = Icons.Default.RecordVoiceOver,
                        label = settings.provider.label(),
                        onClick = { providersExpanded = true }
                    )
                    DropdownMenu(
                        expanded = providersExpanded,
                        onDismissRequest = { providersExpanded = false }
                    ) {
                        VoiceProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.label()) },
                                onClick = {
                                    providersExpanded = false
                                    onSetVoiceProvider(provider)
                                }
                            )
                        }
                    }
                }
                VoiceActionChip(
                    icon = if (settingsExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    label = if (settingsExpanded) "Hide" else "Configure",
                    onClick = { onToggleSettings(!settingsExpanded) }
                )
                if (savedProfiles.isNotEmpty()) {
                    Box {
                        VoiceActionChip(
                            icon = Icons.Default.ArrowDropDown,
                            label = savedProfiles.firstOrNull { it.id == activeProfileId }?.name ?: "Profiles",
                            onClick = { profilesExpanded = true }
                        )
                        DropdownMenu(
                            expanded = profilesExpanded,
                            onDismissRequest = { profilesExpanded = false }
                        ) {
                            savedProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(profile.name)
                                            Text(
                                                "${profile.settings.provider.label()} • ${profile.settings.cartesiaVoiceLabel.ifBlank { profile.settings.kokoroVoice.ifBlank { "System" } }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        profilesExpanded = false
                                        onApplyVoiceProfile(profile.id)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            profilesExpanded = false
                                            onDeleteVoiceProfile(profile.id)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete profile"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFD7DE)
                )
            }

            if (settingsExpanded) {
                OutlinedTextField(
                    value = profileDraft,
                    onValueChange = { profileDraft = it },
                    label = { Text("Save as profile") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val trimmed = profileDraft.trim()
                                if (trimmed.isNotBlank()) {
                                    onSaveVoiceProfile(trimmed)
                                    profileDraft = ""
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                )
                when (settings.provider) {
                    VoiceProvider.System -> {
                        Text(
                            "Uses Android's built-in text-to-speech engine on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VoiceProvider.Cartesia -> {
                        OutlinedTextField(
                            value = settings.cartesiaApiKey,
                            onValueChange = onUpdateCartesiaApiKey,
                            label = { Text("Cartesia API key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.cartesiaModelId,
                            onValueChange = onUpdateCartesiaModelId,
                            label = { Text("Cartesia model") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceActionChip(
                                icon = Icons.Default.PlayArrow,
                                label = if (isLoadingVoices) "Loading..." else "Load voices",
                                onClick = if (isLoadingVoices) null else onRefreshVoiceOptions
                            )
                            Box {
                                VoiceActionChip(
                                    icon = Icons.Default.ArrowDropDown,
                                    label = settings.cartesiaVoiceLabel.ifBlank { "Katie" },
                                    onClick = { voicesExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = voicesExpanded,
                                    onDismissRequest = { voicesExpanded = false }
                                ) {
                                    voiceOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                voicesExpanded = false
                                                onSelectCartesiaVoice(option)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Uses Cartesia Sonic 3 for natural cloud voice playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VoiceProvider.Kokoro -> {
                        OutlinedTextField(
                            value = settings.kokoroEndpoint,
                            onValueChange = onUpdateKokoroEndpoint,
                            label = { Text("Kokoro endpoint") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("https://your-host/v1/audio/speech") }
                        )
                        OutlinedTextField(
                            value = settings.kokoroApiKey,
                            onValueChange = onUpdateKokoroApiKey,
                            label = { Text("Kokoro API key (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.kokoroModel,
                            onValueChange = onUpdateKokoroModel,
                            label = { Text("Kokoro model") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.kokoroVoice,
                            onValueChange = onUpdateKokoroVoice,
                            label = { Text("Kokoro voice") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            "Targets an OpenAI-compatible speech endpoint backed by Kokoro, so you can use a self-hosted open-source voice server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VoiceProvider.Lemonfox -> {
                        OutlinedTextField(
                            value = settings.lemonfoxApiKey,
                            onValueChange = onUpdateLemonfoxApiKey,
                            label = { Text("Lemonfox API key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.lemonfoxLanguage,
                            onValueChange = onUpdateLemonfoxLanguage,
                            label = { Text("Lemonfox language") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("en-us") }
                        )
                        OutlinedTextField(
                            value = settings.lemonfoxSpeed,
                            onValueChange = onUpdateLemonfoxSpeed,
                            label = { Text("Lemonfox speed") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("1.0") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceActionChip(
                                icon = Icons.Default.PlayArrow,
                                label = if (isLoadingVoices) "Loading..." else "Load voices",
                                onClick = if (isLoadingVoices) null else onRefreshVoiceOptions
                            )
                            Box {
                                VoiceActionChip(
                                    icon = Icons.Default.ArrowDropDown,
                                    label = settings.lemonfoxVoice.ifBlank { "sarah" },
                                    onClick = { voicesExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = voicesExpanded,
                                    onDismissRequest = { voicesExpanded = false }
                                ) {
                                    voiceOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                voicesExpanded = false
                                                onSelectLemonfoxVoice(option)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Uses Lemonfox's OpenAI-compatible speech API for low-cost cloud voice playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceActionChip(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(label)
        }
    }
}

@Composable
private fun MessageBubble(
    message: RoomMessage,
    isActivePlayback: Boolean,
    isPausedPlayback: Boolean,
    onPlayMessage: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val bubbleColor = when (message.senderType) {
        MessageSenderType.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        MessageSenderType.AGENT -> MaterialTheme.colorScheme.surface
        MessageSenderType.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AgentAvatar(
                        key = message.senderId,
                        label = message.senderName,
                        size = 44.dp
                    )
                    Column {
                        Text(message.senderName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            message.senderRole,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeakerChip(message.senderType)
                    if (message.spoken) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Spoken",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.body))
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy full message"
                        )
                    }
                    IconButton(onClick = onPlayMessage) {
                        val playbackIcon = when {
                            isPausedPlayback -> Icons.Default.PlayArrow
                            isActivePlayback -> Icons.Default.GraphicEq
                            else -> Icons.Default.RecordVoiceOver
                        }
                        val playbackLabel = when {
                            isPausedPlayback -> "Resume voice playback"
                            isActivePlayback -> "Pause voice playback"
                            else -> "Speak message"
                        }
                        Icon(
                            imageVector = playbackIcon,
                            contentDescription = playbackLabel
                        )
                    }
                }
            }
            SelectionContainer {
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                message.timestampLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    roomTitle: String?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (roomTitle.isNullOrBlank()) {
                    "Mission channel"
                } else {
                    "Live in $roomTitle"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (roomTitle.isNullOrBlank()) {
                                "Send a message"
                            } else {
                                "Message $roomTitle"
                            }
                        )
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF7C5CFF), RoundedCornerShape(18.dp))
                        .padding(4.dp)
                ) {
                    IconButton(onClick = onSend) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRoomCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusBanner(message: String, isError: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFF4C1D24) else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) Color(0xFFFFD7DE) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RoomOverviewCard(
    room: CollaborationRoom,
    visibleMessageCount: Int,
    showingInternalMessages: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(room.title, style = MaterialTheme.typography.titleLarge)
            Text(
                room.purpose,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceActionChip(
                    icon = Icons.Default.Campaign,
                    label = "${room.members.size} agents"
                )
                VoiceActionChip(
                    icon = Icons.Default.PlayArrow,
                    label = "$visibleMessageCount visible"
                )
                VoiceActionChip(
                    icon = if (showingInternalMessages) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    label = if (showingInternalMessages) "Details on" else "Details off"
                )
            }
        }
    }
}

@Composable
private fun SpeakerChip(senderType: MessageSenderType) {
    val label = when (senderType) {
        MessageSenderType.USER -> "Operator"
        MessageSenderType.AGENT -> "Agent"
        MessageSenderType.SYSTEM -> "System"
    }
    val tone = when (senderType) {
        MessageSenderType.USER -> MaterialTheme.colorScheme.primary
        MessageSenderType.AGENT -> MaterialTheme.colorScheme.secondary
        MessageSenderType.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.16f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tone
        )
    }
}
