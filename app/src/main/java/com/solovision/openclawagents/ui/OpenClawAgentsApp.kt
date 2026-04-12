package com.solovision.openclawagents.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solovision.openclawagents.OpenClawViewModel
import com.solovision.openclawagents.ui.screens.HomeScreen
import com.solovision.openclawagents.ui.screens.RoomScreen
import com.solovision.openclawagents.ui.screens.SettingsScreen

@Composable
fun OpenClawAgentsApp() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val viewModel: OpenClawViewModel = viewModel(factory = OpenClawViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                uiState = uiState,
                onCreateRoom = { viewModel.toggleCreateRoom(true) },
                onDismissCreateRoom = { viewModel.toggleCreateRoom(false) },
                onManageAgents = { viewModel.toggleManageAgents(true) },
                onDismissManageAgents = { viewModel.toggleManageAgents(false) },
                onUpdateRoomTitle = viewModel::updateNewRoomTitle,
                onUpdateRoomPurpose = viewModel::updateNewRoomPurpose,
                onToggleAgent = viewModel::toggleAgentSelection,
                onSetAgentHidden = viewModel::setAgentHidden,
                onMoveAgent = viewModel::moveAgent,
                onSetAgentVoiceProvider = viewModel::setAgentVoiceProvider,
                onSelectAgentVoice = viewModel::selectAgentVoice,
                onLoadVoiceOptionsForProvider = viewModel::refreshVoiceOptionsForProvider,
                onConfirmCreateRoom = {
                    viewModel.createRoom { roomId ->
                        viewModel.selectRoom(roomId)
                        navController.navigate("room")
                    }
                },
                onDeleteRoom = viewModel::deleteRoom,
                onOpenAgent = { agentId ->
                    viewModel.openAgentRoom(agentId)
                    navController.navigate("room")
                },
                onOpenRoom = { roomId ->
                    viewModel.selectRoom(roomId)
                    navController.navigate("room")
                },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("room") {
            RoomScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onDraftChange = viewModel::updateDraft,
                onSend = viewModel::sendCurrentMessage,
                onSelectSession = { roomId -> viewModel.selectRoom(roomId) },
                onDeleteSession = viewModel::deleteRoom,
                onToggleVoiceSettings = viewModel::toggleVoiceSettings,
                onSetVoiceProvider = viewModel::setVoiceProvider,
                onUpdateCartesiaApiKey = viewModel::updateCartesiaApiKey,
                onUpdateCartesiaModelId = viewModel::updateCartesiaModelId,
                onRefreshVoiceOptions = viewModel::refreshVoiceOptions,
                onSelectCartesiaVoice = viewModel::selectCartesiaVoice,
                onUpdateKokoroEndpoint = viewModel::updateKokoroEndpoint,
                onUpdateKokoroApiKey = viewModel::updateKokoroApiKey,
                onUpdateKokoroModel = viewModel::updateKokoroModel,
                onUpdateKokoroVoice = viewModel::updateKokoroVoice,
                onUpdateLemonfoxApiKey = viewModel::updateLemonfoxApiKey,
                onUpdateLemonfoxLanguage = viewModel::updateLemonfoxLanguage,
                onUpdateLemonfoxSpeed = viewModel::updateLemonfoxSpeed,
                onSelectLemonfoxVoice = viewModel::selectLemonfoxVoice,
                onSaveVoiceProfile = viewModel::saveCurrentVoiceProfile,
                onApplyVoiceProfile = viewModel::applyVoiceProfile,
                onDeleteVoiceProfile = viewModel::deleteVoiceProfile,
                onTestVoice = viewModel::testVoiceSample,
                onPlayLatestMessage = viewModel::playLatestMessage,
                onPlayMessage = viewModel::playMessage,
                onStopPlayback = viewModel::stopPlayback,
                onToggleInternalMessages = viewModel::setShowInternalMessages,
                onStartPolling = viewModel::startSelectedRoomPolling,
                onStopPolling = viewModel::stopSelectedRoomPolling
            )
        }
        composable("settings") {
            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onUpdateCartesiaApiKey = viewModel::updateCartesiaApiKey,
                onUpdateCartesiaModelId = viewModel::updateCartesiaModelId,
                onUpdateKokoroEndpoint = viewModel::updateKokoroEndpoint,
                onUpdateKokoroApiKey = viewModel::updateKokoroApiKey,
                onUpdateKokoroModel = viewModel::updateKokoroModel,
                onUpdateLemonfoxApiKey = viewModel::updateLemonfoxApiKey,
                onUpdateLemonfoxLanguage = viewModel::updateLemonfoxLanguage,
                onUpdateLemonfoxSpeed = viewModel::updateLemonfoxSpeed
            )
        }
    }
}
