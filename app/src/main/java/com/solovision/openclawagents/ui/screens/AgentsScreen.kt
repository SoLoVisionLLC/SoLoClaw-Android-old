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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.AgentVoiceConfig
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProvider

@Composable
fun AgentsScreen(
    uiState: AppUiState,
    onOpenAgent: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onManageAgents: () -> Unit,
    onDismissManageAgents: () -> Unit,
    onSetAgentHidden: (String, Boolean) -> Unit,
    onMoveAgent: (String, String) -> Unit,
    onSetAgentVoiceProvider: (String, VoiceProvider) -> Unit,
    onSelectAgentVoice: (String, VoiceOption) -> Unit,
    onLoadVoiceOptionsForProvider: (VoiceProvider) -> Unit
) {
    var agentConfigTarget by remember { mutableStateOf<Agent?>(null) }
    val visibleAgents = uiState.agents.filterNot { uiState.hiddenAgentIds.contains(it.id) }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agents", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "A dedicated agent roster gives the app a more Hermes-like mission-control layout while chat behavior stays unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${visibleAgents.size} visible agents • ${uiState.hiddenAgentIds.size} hidden",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Advanced visibility management, room creation, and drag reordering still live on the Home screen during this migration phase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onManageAgents) {
                            Text("Visibility")
                        }
                        OutlinedButton(onClick = onOpenDashboard) {
                            Text("Open Home Controls")
                        }
                    }
                }
            }
        }
        if (visibleAgents.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No visible agents right now. Use Home Controls to show the agents you want on this device.",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Text(
                    "Long-press and drag to reorder the agents shown in this mission-control view.",
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
    }
}
