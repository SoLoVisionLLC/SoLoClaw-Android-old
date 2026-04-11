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
                onUpdateRoomTitle = viewModel::updateNewRoomTitle,
                onUpdateRoomPurpose = viewModel::updateNewRoomPurpose,
                onToggleAgent = viewModel::toggleAgentSelection,
                onConfirmCreateRoom = {
                    viewModel.createRoom()
                    navController.navigate("room")
                },
                onOpenRoom = { roomId ->
                    viewModel.selectRoom(roomId)
                    navController.navigate("room")
                }
            )
        }
        composable("room") {
            RoomScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onDraftChange = viewModel::updateDraft,
                onSend = viewModel::sendCurrentMessage,
                onPlayMessage = viewModel::playMessage,
                onStopPlayback = viewModel::stopPlayback
            )
        }
    }
}
