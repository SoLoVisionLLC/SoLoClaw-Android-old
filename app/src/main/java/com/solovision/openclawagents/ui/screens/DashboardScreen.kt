package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.R
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.CollaborationRoom

@Composable
fun DashboardScreen(
    uiState: AppUiState,
    onCreateRoom: () -> Unit,
    onDismissCreateRoom: () -> Unit,
    onManageAgents: () -> Unit,
    onDismissManageAgents: () -> Unit,
    onUpdateRoomTitle: (String) -> Unit,
    onUpdateRoomPurpose: (String) -> Unit,
    onToggleAgent: (String) -> Unit,
    onSetAgentHidden: (String, Boolean) -> Unit,
    onConfirmCreateRoom: () -> Unit,
    onDeleteRoom: (String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var roomPendingDelete by remember { mutableStateOf<CollaborationRoom?>(null) }
    val visibleAgents = uiState.agents.filterNot { uiState.hiddenAgentIds.contains(it.id) }
    val visibleRooms = uiState.rooms.filterNot { room ->
        val hiddenDirectRoom = room.id.startsWith("agent:") && room.members.any(uiState.hiddenAgentIds::contains)
        val directAgentRoom = room.id.startsWith("agent:") && room.members.size <= 1
        hiddenDirectRoom || directAgentRoom
    }
    val unreadRooms = visibleRooms.count { it.unreadCount > 0 }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            DashboardHero(
                roomCount = visibleRooms.size,
                agentCount = visibleAgents.size,
                onCreateRoom = onCreateRoom,
                onOpenSettings = onOpenSettings
            )
        }
        if (uiState.isWorking || uiState.errorMessage != null) {
            item {
                DashboardStatusBanner(
                    message = uiState.errorMessage ?: "Contacting the OpenClaw gateway...",
                    isError = uiState.errorMessage != null
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Visible agents",
                    value = visibleAgents.size.toString(),
                    tone = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Group rooms",
                    value = visibleRooms.size.toString(),
                    tone = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Unread rooms",
                    value = unreadRooms.toString(),
                    tone = Color(0xFFFB7185)
                )
            }
        }
        item {
            QuickActionRow(
                onCreateRoom = onCreateRoom,
                onManageAgents = onManageAgents,
                onOpenSettings = onOpenSettings
            )
        }
        item {
            SectionHeading(
                title = "Launch Agent Chat",
                subtitle = "Directly jump into a one-on-one room without changing any chat behavior."
            )
        }
        if (visibleAgents.isEmpty()) {
            item {
                MissionEmptyCard("No visible agents. Use Manage Agents to show who should appear on this device.")
            }
        } else {
            items(visibleAgents.take(4), key = { it.id }) { agent ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(agent.accent))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(agent.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    agent.role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(onClick = { onOpenAgent(agent.id) }) {
                            Text("Open")
                        }
                    }
                }
            }
        }
        item {
            SectionHeading(
                title = "Group Rooms",
                subtitle = "Mission-control style overview of your shared rooms."
            )
        }
        if (visibleRooms.isEmpty()) {
            item {
                MissionEmptyCard("No group rooms yet. Create one when you want multiple agents collaborating together.")
            }
        } else {
            items(visibleRooms, key = { it.id }) { room ->
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
    }
}

@Composable
private fun DashboardHero(
    roomCount: Int,
    agentCount: Int,
    onCreateRoom: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0E1527), Color(0xFF1C2542), Color(0xFF0A7EA4))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_source),
                                contentDescription = "OpenClaw app icon",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text("OpenClaw", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("Mission Control", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD8E6F3))
                        }
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Settings")
                    }
                }
                Text(
                    "A Hermes-style command surface for mobile orchestration, with your current chat engine kept intact.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    "$agentCount active agents across $roomCount multi-agent rooms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD8E6F3)
                )
                Button(onClick = onCreateRoom) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Create Room")
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    onCreateRoom: () -> Unit,
    onManageAgents: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "Create Room",
            detail = "Launch a new multi-agent conversation.",
            onClick = onCreateRoom
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "Manage Agents",
            detail = "Control which agents appear locally.",
            onClick = onManageAgents
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "Settings",
            detail = "Voice, providers, and shared defaults.",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Text("Open")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tone: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = tone
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MissionEmptyCard(message: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardStatusBanner(message: String, isError: Boolean) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFF4C1D24) else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) Color(0xFFFFD7DE) else MaterialTheme.colorScheme.onSurface
        )
    }
}
