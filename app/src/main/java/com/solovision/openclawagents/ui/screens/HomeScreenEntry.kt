package com.solovision.openclawagents.ui.screens

import androidx.compose.runtime.Composable
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProvider

@Composable
fun HomeScreen(
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
    HomeScreenContent(
        uiState = uiState,
        onCreateRoom = onCreateRoom,
        onDismissCreateRoom = onDismissCreateRoom,
        onManageAgents = onManageAgents,
        onDismissManageAgents = onDismissManageAgents,
        onUpdateRoomTitle = onUpdateRoomTitle,
        onUpdateRoomPurpose = onUpdateRoomPurpose,
        onToggleAgent = onToggleAgent,
        onSetAgentHidden = onSetAgentHidden,
        onMoveAgent = onMoveAgent,
        onSetAgentVoiceProvider = onSetAgentVoiceProvider,
        onSelectAgentVoice = onSelectAgentVoice,
        onLoadVoiceOptionsForProvider = onLoadVoiceOptionsForProvider,
        onConfirmCreateRoom = onConfirmCreateRoom,
        onDeleteRoom = onDeleteRoom,
        onOpenAgent = onOpenAgent,
        onOpenRoom = onOpenRoom,
        onOpenSettings = onOpenSettings
    )
}
