package com.solovision.openclawagents.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.solovision.openclawagents.OpenClawViewModel
import com.solovision.openclawagents.ui.screens.AgentsScreen
import com.solovision.openclawagents.ui.screens.CronScreen
import com.solovision.openclawagents.ui.screens.DashboardScreen
import com.solovision.openclawagents.ui.screens.RoomScreen
import com.solovision.openclawagents.ui.screens.SettingsScreen
import com.solovision.openclawagents.ui.screens.SkillsScreen
import com.solovision.openclawagents.ui.theme.OpenClawAgentsTheme

@Composable
fun OpenClawAgentsApp() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val viewModel: OpenClawViewModel = viewModel(factory = OpenClawViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    fun navigateToShellDestination(destination: AppDestination) {
        val options = navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
        }
        navController.navigate(destination.route, options)
    }

    fun openChat(roomId: String? = uiState.selectedRoomId ?: uiState.rooms.firstOrNull()?.id) {
        roomId?.let(viewModel::selectRoom)
        navigateToShellDestination(AppDestination.Chat)
    }

    OpenClawAgentsTheme(themeMode = uiState.themeMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    primaryDestinations.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination == AppDestination.Chat) {
                                    openChat()
                                } else {
                                    navigateToShellDestination(destination)
                                }
                            },
                            icon = {
                                androidx.compose.material3.Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppDestination.Dashboard.route) {
                    DashboardScreen(
                        uiState = uiState,
                        onCreateRoom = { viewModel.toggleCreateRoom(true) },
                        onDismissCreateRoom = { viewModel.toggleCreateRoom(false) },
                        onManageAgents = { viewModel.toggleManageAgents(true) },
                        onDismissManageAgents = { viewModel.toggleManageAgents(false) },
                        onUpdateRoomTitle = viewModel::updateNewRoomTitle,
                        onUpdateRoomPurpose = viewModel::updateNewRoomPurpose,
                        onToggleAgent = viewModel::toggleAgentSelection,
                        onSetAgentHidden = viewModel::setAgentHidden,
                        onConfirmCreateRoom = {
                            viewModel.createRoom { roomId -> openChat(roomId) }
                        },
                        onDeleteRoom = viewModel::deleteRoom,
                        onOpenAgent = { agentId ->
                            val roomId = viewModel.openAgentRoom(agentId)
                            openChat(roomId)
                        },
                        onOpenRoom = { roomId -> openChat(roomId) },
                        onOpenSettings = { navigateToShellDestination(AppDestination.Settings) }
                    )
                }
                composable(AppDestination.Agents.route) {
                    AgentsScreen(
                        uiState = uiState,
                        onOpenAgent = { agentId ->
                            val roomId = viewModel.openAgentRoom(agentId)
                            openChat(roomId)
                        },
                        onOpenDashboard = { navigateToShellDestination(AppDestination.Dashboard) },
                        onManageAgents = { viewModel.toggleManageAgents(true) },
                        onDismissManageAgents = { viewModel.toggleManageAgents(false) },
                        onSetAgentHidden = viewModel::setAgentHidden,
                        onMoveAgent = viewModel::moveAgent,
                        onSetAgentVoiceProvider = viewModel::setAgentVoiceProvider,
                        onSelectAgentVoice = viewModel::selectAgentVoice,
                        onLoadVoiceOptionsForProvider = viewModel::refreshVoiceOptionsForProvider
                    )
                }
                // Chat behavior is intentionally preserved here; only the surrounding shell changes.
                composable(AppDestination.Chat.route) {
                    RoomScreen(
                        uiState = uiState,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navigateToShellDestination(AppDestination.Dashboard)
                            }
                        },
                        onDraftChange = viewModel::updateDraft,
                        onSend = viewModel::sendCurrentMessage,
                        onSelectSession = viewModel::selectRoom,
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
                composable(AppDestination.Cron.route) {
                    CronScreen(
                        uiState = uiState,
                        onRefresh = {
                            viewModel.refreshMissionControlCapabilities()
                            viewModel.refreshCronJobs()
                        },
                        onSelectJob = viewModel::selectCronJob,
                        onRefreshRuns = viewModel::refreshCronRuns,
                        onToggleEnabled = viewModel::setCronEnabled,
                        onRunJob = viewModel::runCronJob,
                        onDeleteJob = viewModel::deleteCronJob,
                        onCreateJob = viewModel::createCronJob,
                        onUpdateJob = viewModel::updateCronJob,
                        onClearActionMessage = viewModel::clearCronActionMessage
                    )
                }
                composable(AppDestination.Skills.route) {
                    SkillsScreen(
                        uiState = uiState,
                        onRefresh = {
                            viewModel.refreshMissionControlCapabilities()
                            viewModel.refreshSkills()
                        },
                        onSelectSkill = viewModel::selectSkill,
                        onToggleHidden = viewModel::toggleSkillHidden,
                        onLoadSkillFiles = viewModel::loadSkillFiles,
                        onOpenSkillFile = viewModel::openSkillFile,
                        onUpdateSkillFileContent = viewModel::updateSkillFileContent,
                        onSaveSelectedSkillFile = viewModel::saveSelectedSkillFile,
                        onInstallSkill = viewModel::installSkill,
                        onSetSkillEnabled = viewModel::setSkillEnabled,
                        onUninstallSkill = viewModel::uninstallSkill,
                        onCheckSkill = viewModel::checkSkill,
                        onUpdateSkillFromSource = viewModel::updateSkillFromSource,
                        onBrowseSkillsHub = viewModel::browseSkillsHub,
                        onSearchSkillsHub = viewModel::searchSkillsHub,
                        onInspectHubSkill = viewModel::inspectHubSkill,
                        onInstallHubSkill = viewModel::installHubSkill
                    )
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen(
                        uiState = uiState,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navigateToShellDestination(AppDestination.Dashboard)
                            }
                        },
                        onSetThemeMode = viewModel::setThemeMode,
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
    }
}
