package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPlayMessage: (String) -> Unit,
    onStopPlayback: () -> Unit
) {
    val room = uiState.rooms.firstOrNull { it.id == uiState.selectedRoomId } ?: uiState.rooms.firstOrNull()
    val messages = uiState.roomMessages[room?.id].orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(room?.title ?: "Room")
                        Text(
                            room?.members?.joinToString(" • ")?.replaceFirstChar { it.uppercase() } ?: "No members",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            ComposerBar(
                value = uiState.draftMessage,
                onValueChange = onDraftChange,
                onSend = onSend
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TtsControlCard(
                    queueCount = uiState.ttsState.queueCount,
                    isPlaying = uiState.ttsState.isPlaying,
                    voiceLabel = uiState.ttsState.activeVoiceLabel,
                    onStopPlayback = onStopPlayback
                )
            }
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    onPlayMessage = { onPlayMessage(message.body) }
                )
            }
        }
    }
}

@Composable
private fun TtsControlCard(
    queueCount: Int,
    isPlaying: Boolean,
    voiceLabel: String,
    onStopPlayback: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Room Voice", style = MaterialTheme.typography.titleLarge)
                Text("Voice: $voiceLabel • Queue: $queueCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceActionChip(Icons.Default.PlayArrow, if (isPlaying) "Playing" else "Preview")
                VoiceActionChip(Icons.Default.Stop, "Stop", onClick = onStopPlayback)
            }
        }
    }
}

@Composable
private fun VoiceActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label)
        }
    }
}

@Composable
private fun MessageBubble(
    message: RoomMessage,
    onPlayMessage: () -> Unit
) {
    val bubbleColor = when (message.senderType) {
        MessageSenderType.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        MessageSenderType.AGENT -> MaterialTheme.colorScheme.surface
        MessageSenderType.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(message.senderName, style = MaterialTheme.typography.titleLarge)
                    Text(message.senderRole, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (message.spoken) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Spoken", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = onPlayMessage) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Speak message")
                    }
                }
            }
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            Text(message.timestampLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the room to collaborate...") },
                shape = RoundedCornerShape(18.dp)
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFF7C5CFF), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}
