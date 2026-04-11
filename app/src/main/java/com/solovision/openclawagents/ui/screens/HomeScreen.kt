package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.CollaborationRoom

@Composable
fun HomeScreen(
    uiState: AppUiState,
    onCreateRoom: () -> Unit,
    onDismissCreateRoom: () -> Unit,
    onUpdateRoomTitle: (String) -> Unit,
    onUpdateRoomPurpose: (String) -> Unit,
    onToggleAgent: (String) -> Unit,
    onConfirmCreateRoom: () -> Unit,
    onOpenRoom: (String) -> Unit
) {
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
            item { HeroCard() }
            if (uiState.isWorking || uiState.errorMessage != null) {
                item {
                    StatusBanner(
                        message = uiState.errorMessage ?: "Contacting the OpenClaw gateway…",
                        isError = uiState.errorMessage != null
                    )
                }
            }
            item { SectionTitle("Agents") }
            items(uiState.agents) { agent -> AgentCard(agent) }
            item { SectionTitle("Collaboration Rooms") }
            items(uiState.rooms) { room -> RoomCard(room) { onOpenRoom(room.id) } }
            item { VoicePreviewCard() }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create collaboration room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.newRoomTitle,
                    onValueChange = onUpdateRoomTitle,
                    label = { Text("Room name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.newRoomPurpose,
                    onValueChange = onUpdateRoomPurpose,
                    label = { Text("Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select agents", style = MaterialTheme.typography.titleLarge)
                uiState.agents.forEach { agent ->
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
                            Text(agent.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
private fun HeroCard() {
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
private fun AgentCard(agent: Agent) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .background(Color(agent.accent)),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(agent.name, style = MaterialTheme.typography.titleLarge)
                        Text(agent.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(agent.status, color = MaterialTheme.colorScheme.secondary)
            }
            Text(agent.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoomCard(room: CollaborationRoom, onOpenRoom: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenRoom,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(room.title, style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Default.NorthEast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(room.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text("${room.members.size} agents", color = MaterialTheme.colorScheme.onSurface)
                if (room.unreadCount > 0) Text("• ${room.unreadCount} new", color = Color(0xFFFB7185))
                Text("• ${room.lastActivity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
