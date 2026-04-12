package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.AgentVoiceConfig
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProvider

@Composable
fun HomeScreenContent(
    uiState: AppUiState,
    onCreateRoom: () -> Unit,
    onDismissCreateRoom: () -> Unit,
    onManageAgents: () -> Unit,
    onDismissManageAgents: () -> Unit,
    onUpdateRoomTitle: (String) -> Unit,
    onUpdateRoomPurpose: (String) -> Unit,
    onToggleAgent: (String) -> Unit,
    onSetAgentHidden: (String, Boolean) -> Unit,
    onMoveAgent: (String, String) -> Unit,
    onSetAgentVoiceProvider: (String, VoiceProvider) -> Unit,
    onSelectAgentVoice: (String, VoiceOption) -> Unit,
    onLoadVoiceOptionsForProvider: (VoiceProvider) -> Unit,
    onConfirmCreateRoom: () -> Unit,
    onDeleteRoom: (String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var roomPendingDelete by remember { mutableStateOf<CollaborationRoom?>(null) }
    var agentConfigTarget by remember { mutableStateOf<Agent?>(null) }
    val visibleAgents = uiState.agents.filterNot { uiState.hiddenAgentIds.contains(it.id) }
    val visibleRooms = uiState.rooms.filterNot { room ->
        val hiddenDirectRoom = room.id.startsWith("agent:") && room.members.any(uiState.hiddenAgentIds::contains)
        val directAgentRoom = room.id.startsWith("agent:") && room.members.size <= 1
        hiddenDirectRoom || directAgentRoom
    }

    if (uiState.creatingRoom) {
        CreateRoomDialog(
            uiState = uiState,
            onDismiss = onDismissCreateRoom,
            onUpdateRoomTitle = onUpdateRoomTitle,
            onUpdateRoomPurpose = onUpdateRoomPurpose,
            onToggleAgent = onToggleAgent,
            onConfirm = onConfirmCreateRoom
        )
    }

    if (uiState.managingAgents) {
        ManageAgentsDialog(
            uiState = uiState,
            onDismiss = onDismissManageAgents,
            onSetAgentHidden = onSetAgentHidden
        )
    }

    agentConfigTarget?.let { agent ->
        AgentVoiceConfigDialog(
            agent = agent,
            config = uiState.agentVoiceConfigs[agent.id] ?: AgentVoiceConfig(),
            availableVoices = uiState.ttsState.availableVoices,
            onDismiss = { agentConfigTarget = null },
            onSetProvider = { provider ->
                onSetAgentVoiceProvider(agent.id, provider)
                onLoadVoiceOptionsForProvider(provider)
            },
            onSelectVoice = { option -> onSelectAgentVoice(agent.id, option) }
        )
    }

    roomPendingDelete?.let { room ->
        DeleteRoomDialog(
            room = room,
            onDismiss = { roomPendingDelete = null },
            onConfirmDelete = {
                roomPendingDelete = null
                onDeleteRoom(room.id)
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoom) {
                Icon(Icons.Default.Add, contentDescription = "Create room")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { HeroCard(onOpenSettings = onOpenSettings) }
            if (uiState.isWorking || uiState.errorMessage != null) {
                item {
                    StatusBanner(
                        message = uiState.errorMessage ?: "Contacting the OpenClaw gateway...",
                        isError = uiState.errorMessage != null
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Agents",
                    actionLabel = "Manage",
                    onAction = onManageAgents
                )
            }
            if (visibleAgents.isEmpty()) {
                item {
                    EmptyStateCard("No visible agents. Use Manage to show the agents you want on this device.")
                }
            } else {
                item {
                    Text(
                        "Long-press and drag to reorder your visible agents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    ReorderableAgentList(
                        agents = visibleAgents,
                        voiceConfigs = uiState.agentVoiceConfigs,
                        onMoveAgent = onMoveAgent,
                        onOpenAgent = onOpenAgent,
                        onConfigureVoice = { agent -> agentConfigTarget = agent }
                    )
                }
            }
            item { SectionTitle("Group Rooms") }
            if (visibleRooms.isEmpty()) {
                item {
                    EmptyStateCard("No group rooms yet. Create one when you want multiple agents in the same conversation.")
                }
            } else {
                items(visibleRooms) { room ->
                    RoomCard(
                        room = room,
                        onOpenRoom = { onOpenRoom(room.id) },
                        onDeleteRoom = if (room.id.startsWith("local-room-")) {
                            { roomPendingDelete = room }
                        } else {
                            null
                        }
                    )
                }
            }
            item { VoicePreviewCard() }
        }
    }
}

@Composable
private fun ReorderableAgentList(
    agents: List<Agent>,
    voiceConfigs: Map<String, AgentVoiceConfig>,
    onMoveAgent: (String, String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onConfigureVoice: (Agent) -> Unit
) {
    val itemBounds = remember { mutableStateMapOf<String, ItemBounds>() }
    var draggingAgentId by remember(agents) { mutableStateOf<String?>(null) }
    var dragOffsetY by remember(agents) { mutableStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        agents.forEach { agent ->
            val isDragging = draggingAgentId == agent.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .onGloballyPositioned { coordinates ->
                        val top = coordinates.positionInParent().y
                        itemBounds[agent.id] = ItemBounds(
                            top = top,
                            bottom = top + coordinates.size.height
                        )
                    }
                    .pointerInput(agents, draggingAgentId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingAgentId = agent.id
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingAgentId = null
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                draggingAgentId = null
                                dragOffsetY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val activeAgentId = draggingAgentId ?: return@detectDragGesturesAfterLongPress
                            val currentBounds = itemBounds[activeAgentId] ?: return@detectDragGesturesAfterLongPress
                            dragOffsetY += dragAmount.y
                            val draggedCenter = currentBounds.centerY + dragOffsetY
                            val targetAgentId = agents.firstOrNull { candidate ->
                                candidate.id != activeAgentId && itemBounds[candidate.id]?.contains(draggedCenter) == true
                            }?.id ?: return@detectDragGesturesAfterLongPress
                            val targetBounds = itemBounds[targetAgentId] ?: return@detectDragGesturesAfterLongPress
                            onMoveAgent(activeAgentId, targetAgentId)
                            dragOffsetY += currentBounds.top - targetBounds.top
                        }
                    }
            ) {
                AgentCard(
                    agent = agent,
                    voiceConfig = voiceConfigs[agent.id],
                    onOpen = { onOpenAgent(agent.id) },
                    onConfigureVoice = { onConfigureVoice(agent) }
                )
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    uiState: AppUiState,
    onDismiss: () -> Unit,
    onUpdateRoomTitle: (String) -> Unit,
    onUpdateRoomPurpose: (String) -> Unit,
    onToggleAgent: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val selectableAgents = uiState.agents.filter {
        it.availableForRooms && !uiState.hiddenAgentIds.contains(it.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create collaboration room") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.newRoomTitle,
                        onValueChange = onUpdateRoomTitle,
                        label = { Text("Room name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = uiState.newRoomPurpose,
                        onValueChange = onUpdateRoomPurpose,
                        label = { Text("Purpose") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Select agents", style = MaterialTheme.typography.titleLarge)
                }
                if (selectableAgents.isEmpty()) {
                    item {
                        Text(
                            "No visible agents are available for new rooms. Use Manage on the home screen to show more agents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(selectableAgents, key = { it.id }) { agent ->
                        val selected = uiState.selectedAgentIds.contains(agent.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onToggleAgent(agent.id) }
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(agent.name)
                                Text(
                                    agent.role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageAgentsDialog(
    uiState: AppUiState,
    onDismiss: () -> Unit,
    onSetAgentHidden: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage visible agents") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "This is a local filter for this device. Hidden agents stay available in OpenClaw and can be shown again anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.agents, key = { it.id }) { agent ->
                    val hidden = uiState.hiddenAgentIds.contains(agent.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSetAgentHidden(agent.id, !hidden) }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(agent.name)
                            Text(
                                agent.role,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onSetAgentHidden(agent.id, !hidden) }) {
                            Icon(
                                imageVector = if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(if (hidden) "Show" else "Hide")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun HeroCard(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF171F33), Color(0xFF21113A), Color(0xFF0E7490))
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("OpenClaw Agents", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenSettings)
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Open settings", tint = Color.White)
                    }
                }
                Text(
                    "Direct agent chat and multi-agent rooms, designed for mobile-first command and collaboration.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    "Fresh concept, dark-first, and built around readability, orchestration, and voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD7E2F2)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(title)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun AgentCard(
    agent: Agent,
    voiceConfig: AgentVoiceConfig?,
    onOpen: () -> Unit,
    onConfigureVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(agent.accent))
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(agent.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            agent.role,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.status, color = MaterialTheme.colorScheme.secondary)
                    IconButton(onClick = onConfigureVoice) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Configure agent voice"
                        )
                    }
                }
            }
            Text(
                agent.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            voiceConfig?.let { config ->
                Text(
                    "Voice: ${config.provider.label()}${config.voiceLabel.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ItemBounds(
    val top: Float,
    val bottom: Float
) {
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(y: Float): Boolean = y in top..bottom
}

@Composable
private fun AgentVoiceConfigDialog(
    agent: Agent,
    config: AgentVoiceConfig,
    availableVoices: List<VoiceOption>,
    onDismiss: () -> Unit,
    onSetProvider: (VoiceProvider) -> Unit,
    onSelectVoice: (VoiceOption) -> Unit
) {
    var providersExpanded by remember { mutableStateOf(false) }
    var voicesExpanded by remember { mutableStateOf(false) }
    val voiceOptions = when (config.provider) {
        VoiceProvider.System -> listOf(VoiceOption("system", "System"))
        VoiceProvider.Kokoro -> listOf(
            VoiceOption("af_heart", "af_heart"),
            VoiceOption("af_bella", "af_bella"),
            VoiceOption("af_nicole", "af_nicole"),
            VoiceOption("am_adam", "am_adam"),
            VoiceOption("am_michael", "am_michael")
        )
        VoiceProvider.Cartesia, VoiceProvider.Lemonfox -> availableVoices
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent voice for ${agent.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose how ${agent.name}'s messages should sound when played back in the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    TextButton(onClick = { providersExpanded = true }) {
                        Text("Provider: ${config.provider.label()}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = providersExpanded,
                        onDismissRequest = { providersExpanded = false }
                    ) {
                        VoiceProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.label()) },
                                onClick = {
                                    providersExpanded = false
                                    onSetProvider(provider)
                                }
                            )
                        }
                    }
                }
                Box {
                    TextButton(onClick = { voicesExpanded = true }) {
                        Text(
                            "Voice: ${
                                config.voiceLabel.takeIf { it.isNotBlank() }
                                    ?: config.voiceId.takeIf { it.isNotBlank() }
                                    ?: "Select"
                            }"
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = voicesExpanded,
                        onDismissRequest = { voicesExpanded = false }
                    ) {
                        voiceOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    voicesExpanded = false
                                    onSelectVoice(option)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun RoomCard(
    room: CollaborationRoom,
    onOpenRoom: () -> Unit,
    onDeleteRoom: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenRoom,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(room.title, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onDeleteRoom?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete room",
                                tint = Color(0xFFFB7185)
                            )
                        }
                    }
                    Icon(Icons.Default.NorthEast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(room.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Campaign,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text("${room.members.size} agents", color = MaterialTheme.colorScheme.onSurface)
                if (room.unreadCount > 0) {
                    Text("• ${room.unreadCount} new", color = Color(0xFFFB7185))
                }
                Text("• ${room.lastActivity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeleteRoomDialog(
    room: CollaborationRoom,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete group room") },
        text = {
            Text(
                "Delete \"${room.title}\" and remove every relay session created for this group? This only affects the app-managed group room and its room-specific sessions.",
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
private fun EmptyStateCard(message: String) {
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
private fun VoicePreviewCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Voice Layer")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.KeyboardVoice, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Push to talk, room playback, and message-level TTS are built into the architecture.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("The app is structured for per-agent voice identities and a shared spoken queue.")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
}

private fun VoiceProvider.label(): String = when (this) {
    VoiceProvider.System -> "System"
    VoiceProvider.Cartesia -> "Cartesia"
    VoiceProvider.Kokoro -> "Kokoro"
    VoiceProvider.Lemonfox -> "Lemonfox"
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
