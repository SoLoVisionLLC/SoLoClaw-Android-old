package com.solovision.openclawagents

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solovision.openclawagents.data.AgentVisibilityStore
import com.solovision.openclawagents.data.AgentVoiceConfigStore
import com.solovision.openclawagents.data.AppThemeStore
import com.solovision.openclawagents.data.ConversationDisplayStore
import com.solovision.openclawagents.data.FakeOpenClawRepository
import com.solovision.openclawagents.data.GatewayRpcOpenClawTransport
import com.solovision.openclawagents.data.OpenClawBackendConfig
import com.solovision.openclawagents.data.OpenClawRepository
import com.solovision.openclawagents.data.RealOpenClawRepository
import com.solovision.openclawagents.data.RoomReadStateStore
import com.solovision.openclawagents.data.VoiceSettingsStore
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.AppThemeMode
import com.solovision.openclawagents.model.AgentVoiceConfig
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.TtsState
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProfile
import com.solovision.openclawagents.model.VoiceProvider
import com.solovision.openclawagents.model.VoiceSettings
import com.solovision.openclawagents.tts.ProviderBackedTtsEngine
import com.solovision.openclawagents.tts.TtsPlaybackListener
import com.solovision.openclawagents.tts.TtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OpenClawViewModel(
    private val repository: OpenClawRepository = FakeOpenClawRepository(),
    private val agentVisibilityStore: AgentVisibilityStore = AgentVisibilityStore(),
    private val conversationDisplayStore: ConversationDisplayStore = ConversationDisplayStore(),
    private val roomReadStateStore: RoomReadStateStore = RoomReadStateStore(),
    private val voiceSettingsStore: VoiceSettingsStore = VoiceSettingsStore(),
    private val appThemeStore: AppThemeStore = AppThemeStore(),
    private val agentVoiceConfigStore: AgentVoiceConfigStore = AgentVoiceConfigStore(),
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val logTag = "OpenClawViewModel"
    private var roomPollingJob: Job? = null
    private var agentOrderIds = agentVisibilityStore.readAgentOrderIds()
    private val initialVoiceSettings = voiceSettingsStore.read()
    private val initialVoiceProfiles = voiceSettingsStore.readProfiles()
    private val initialAgentVoiceConfigs = agentVoiceConfigStore.read()
    private val initialThemeMode = appThemeStore.read()

    private val _uiState = MutableStateFlow(
        AppUiState(
            agents = emptyList(),
            rooms = emptyList(),
            selectedRoomId = null,
            roomMessages = emptyMap(),
            voiceSettings = initialVoiceSettings,
            ttsState = TtsState(
                provider = initialVoiceSettings.provider,
                activeVoiceLabel = voiceLabelFor(initialVoiceSettings),
                savedProfiles = initialVoiceProfiles,
                activeProfileId = matchingVoiceProfileId(initialVoiceSettings, initialVoiceProfiles)
            ),
            agentVoiceConfigs = initialAgentVoiceConfigs,
            hiddenAgentIds = agentVisibilityStore.readHiddenAgentIds(),
            showInternalMessages = conversationDisplayStore.readShowInternalMessages(),
            themeMode = initialThemeMode
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        ttsEngine.setPlaybackListener(
            object : TtsPlaybackListener {
                override fun onPlaybackStarted(provider: VoiceProvider, voiceLabel: String) {
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            provider = provider,
                            activeVoiceLabel = voiceLabel,
                            isPlaying = true,
                            isPaused = false,
                            queueCount = 1,
                            errorMessage = null
                        )
                    )
                }

                override fun onPlaybackFinished() {
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isPlaying = false,
                            isPaused = false,
                            currentMessageId = null,
                            queueCount = 0
                        )
                    )
                }

                override fun onPlaybackError(message: String) {
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isPlaying = false,
                            isPaused = false,
                            currentMessageId = null,
                            queueCount = 0,
                            errorMessage = message
                        ),
                        errorMessage = message
                    )
                }
            }
        )
        refreshAgents()
        refreshRooms()
        refreshVoiceOptionsIfNeeded(initialVoiceSettings)
    }

    fun openAgentRoom(agentId: String) {
        val mainRoomId = "agent:$agentId:main"
        val roomId = _uiState.value.rooms
            .firstOrNull { it.id.equals(mainRoomId, ignoreCase = true) }
            ?.id
            ?: _uiState.value.rooms
            .firstOrNull { isDirectSessionRoomForAgent(it.id, agentId) }
            ?.id
            ?: mainRoomId
        selectRoom(roomId)
    }

    fun selectRoom(roomId: String) {
        _uiState.value = _uiState.value.copy(
            selectedRoomId = roomId,
            selectedRoomUnreadAnchorKey = null,
            errorMessage = null
        )
        refreshMessages(roomId)
    }

    fun startSelectedRoomPolling() {
        val roomId = _uiState.value.selectedRoomId ?: return
        if (roomPollingJob?.isActive == true) return
        roomPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2_500)
                val activeRoomId = _uiState.value.selectedRoomId
                if (activeRoomId.isNullOrBlank()) continue
                runCatching { repository.getRoomMessages(activeRoomId) }
                    .onSuccess { messages ->
                        val updatedRoomMessages = _uiState.value.roomMessages + (activeRoomId to messages)
                        _uiState.value = _uiState.value.copy(
                            roomMessages = updatedRoomMessages,
                            rooms = applyUnreadCounts(_uiState.value.rooms, updatedRoomMessages),
                            errorMessage = null
                        )
                    }
                    .onFailure { error ->
                        Log.w(logTag, "Polling room failed roomId=$activeRoomId", error)
                    }
            }
        }
    }

    fun stopSelectedRoomPolling() {
        roomPollingJob?.cancel()
        roomPollingJob = null
    }

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(
            draftMessage = text,
            errorMessage = null
        )
    }

    fun setShowInternalMessages(show: Boolean) {
        conversationDisplayStore.writeShowInternalMessages(show)
        _uiState.value = _uiState.value.copy(showInternalMessages = show)
    }

    fun toggleCreateRoom(open: Boolean) {
        _uiState.value = _uiState.value.copy(creatingRoom = open)
    }

    fun toggleManageAgents(open: Boolean) {
        _uiState.value = _uiState.value.copy(managingAgents = open)
    }

    fun updateNewRoomTitle(text: String) {
        _uiState.value = _uiState.value.copy(newRoomTitle = text)
    }

    fun updateNewRoomPurpose(text: String) {
        _uiState.value = _uiState.value.copy(newRoomPurpose = text)
    }

    fun toggleAgentSelection(agentId: String) {
        val current = _uiState.value.selectedAgentIds.toMutableSet()
        if (!current.add(agentId)) current.remove(agentId)
        _uiState.value = _uiState.value.copy(selectedAgentIds = current)
    }

    fun setAgentHidden(agentId: String, hidden: Boolean) {
        val currentState = _uiState.value
        val hiddenAgentIds = currentState.hiddenAgentIds.toMutableSet().apply {
            if (hidden) add(agentId) else remove(agentId)
        }.toSet()
        agentVisibilityStore.writeHiddenAgentIds(hiddenAgentIds)

        val selectedRoomId = currentState.selectedRoomId
            ?.takeUnless { roomId -> isHiddenAgentRoom(roomId, hiddenAgentIds) }
            ?: firstVisibleRoomId(currentState.rooms, hiddenAgentIds)

        _uiState.value = currentState.copy(
            hiddenAgentIds = hiddenAgentIds,
            selectedAgentIds = currentState.selectedAgentIds - agentId,
            selectedRoomId = selectedRoomId
        )
    }

    fun moveAgent(sourceAgentId: String, targetAgentId: String) {
        if (sourceAgentId.equals(targetAgentId, ignoreCase = true)) return
        val currentAgents = _uiState.value.agents.toMutableList()
        val fromIndex = currentAgents.indexOfFirst { it.id.equals(sourceAgentId, ignoreCase = true) }
        val toIndex = currentAgents.indexOfFirst { it.id.equals(targetAgentId, ignoreCase = true) }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return

        val movedAgent = currentAgents.removeAt(fromIndex)
        currentAgents.add(toIndex, movedAgent)
        persistAgentOrder(currentAgents)
        _uiState.value = _uiState.value.copy(agents = currentAgents)
    }

    fun createRoom(onRoomReady: ((String) -> Unit)? = null) {
        val state = _uiState.value
        val title = state.newRoomTitle.trim()
        val purpose = state.newRoomPurpose.trim()
        if (title.isBlank() || state.selectedAgentIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
            Log.d(logTag, "Creating room title=$title agents=${state.selectedAgentIds.joinToString()}")
            runCatching {
                val room = repository.createRoom(
                    title,
                    purpose.ifBlank { "New collaboration room" },
                    state.selectedAgentIds.toList()
                )
                val rooms = repository.getRooms()
                val messages = repository.getRoomMessages(room.id)
                Triple(room, rooms, messages)
            }.onSuccess { (room, rooms, messages) ->
                _uiState.value = _uiState.value.copy(
                    rooms = rooms,
                    selectedRoomId = room.id,
                    roomMessages = _uiState.value.roomMessages + (room.id to messages),
                    creatingRoom = false,
                    newRoomTitle = "",
                    newRoomPurpose = "",
                    selectedAgentIds = emptySet(),
                    isWorking = false,
                    errorMessage = null
                )
                onRoomReady?.invoke(room.id)
            }.onFailure { error ->
                reportFailure("create room", error)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    errorMessage = humanReadableError("Unable to create room", error)
                )
            }
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
            Log.d(logTag, "Deleting room roomId=$roomId")
            runCatching {
                repository.deleteRoom(roomId)
                roomReadStateStore.writeLastReadMessageKey(roomId, null)
                repository.getRooms()
            }.onSuccess { rooms ->
                val updatedRoomMessages = _uiState.value.roomMessages - roomId
                val hiddenAgentIds = _uiState.value.hiddenAgentIds
                val selectedRoomId = _uiState.value.selectedRoomId
                    ?.takeUnless { it == roomId }
                    ?.takeIf { current -> rooms.any { it.id == current } && !isHiddenAgentRoom(current, hiddenAgentIds) }
                    ?: firstVisibleRoomId(rooms, hiddenAgentIds)
                _uiState.value = _uiState.value.copy(
                    rooms = applyUnreadCounts(rooms, updatedRoomMessages),
                    roomMessages = updatedRoomMessages,
                    selectedRoomId = selectedRoomId,
                    selectedRoomUnreadAnchorKey = null,
                    isWorking = false,
                    errorMessage = null
                )
            }.onFailure { error ->
                reportFailure("delete room", error)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    errorMessage = humanReadableError("Unable to delete room", error)
                )
            }
        }
    }

    fun sendCurrentMessage() {
        val state = _uiState.value
        val roomId = state.selectedRoomId ?: return
        val text = state.draftMessage.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
            Log.d(logTag, "Sending message roomId=$roomId length=${text.length}")
            val optimisticMessage = RoomMessage(
                id = "pending-${System.currentTimeMillis()}",
                senderId = "solo",
                senderName = "SoLo",
                senderRole = "Operator",
                senderType = MessageSenderType.USER,
                body = text,
                timestampLabel = "Now",
                messageKey = buildLocalMessageKey(
                    roomId = roomId,
                    senderType = MessageSenderType.USER,
                    body = text,
                    timestampMs = System.currentTimeMillis()
                ),
                timestampMs = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                draftMessage = "",
                roomMessages = _uiState.value.roomMessages + (
                    roomId to (_uiState.value.roomMessages[roomId].orEmpty() + optimisticMessage)
                )
            )
            runCatching {
                repository.sendMessage(roomId, text)
                loadMessagesAfterSend(roomId, text, optimisticMessage)
            }.onSuccess { updatedMessages ->
                markRoomRead(roomId, updatedMessages)
                val updatedRoomMessages = _uiState.value.roomMessages + (roomId to updatedMessages)
                _uiState.value = _uiState.value.copy(
                    roomMessages = updatedRoomMessages,
                    rooms = applyUnreadCounts(_uiState.value.rooms, updatedRoomMessages),
                    isWorking = false,
                    errorMessage = null
                )
            }.onFailure { error ->
                reportFailure("send message", error)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    draftMessage = text,
                    roomMessages = _uiState.value.roomMessages + (
                        roomId to _uiState.value.roomMessages[roomId].orEmpty().filterNot { it.id == optimisticMessage.id }
                    ),
                    errorMessage = humanReadableError("Unable to send message", error)
                )
            }
        }
    }

    fun toggleVoiceSettings(open: Boolean) {
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(settingsExpanded = open)
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        appThemeStore.write(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setVoiceProvider(provider: VoiceProvider) {
        val updated = _uiState.value.voiceSettings.copy(provider = provider)
        updateVoiceSettings(updated)
        refreshVoiceOptionsIfNeeded(updated, force = true)
    }

    fun updateCartesiaApiKey(value: String) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(cartesiaApiKey = value))
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(availableVoices = emptyList(), errorMessage = null)
        )
    }

    fun updateCartesiaModelId(value: String) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                cartesiaModelId = value.ifBlank { "sonic-3" }
            )
        )
    }

    fun refreshVoiceOptions() {
        refreshVoiceOptionsIfNeeded(_uiState.value.voiceSettings, force = true)
    }

    fun selectCartesiaVoice(option: VoiceOption) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                cartesiaVoiceId = option.id,
                cartesiaVoiceLabel = option.label
            )
        )
    }

    fun updateKokoroEndpoint(value: String) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(kokoroEndpoint = value))
    }

    fun updateKokoroApiKey(value: String) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(kokoroApiKey = value))
    }

    fun updateKokoroModel(value: String) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                kokoroModel = value.ifBlank { "kokoro" }
            )
        )
    }

    fun updateKokoroVoice(value: String) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                kokoroVoice = value.ifBlank { "af_heart" }
            )
        )
    }

    fun updateLemonfoxApiKey(value: String) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(lemonfoxApiKey = value))
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(availableVoices = emptyList(), errorMessage = null)
        )
    }

    fun updateLemonfoxLanguage(value: String) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                lemonfoxLanguage = value.ifBlank { "en-us" }
            )
        )
    }

    fun updateLemonfoxSpeed(value: String) {
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                lemonfoxSpeed = value.ifBlank { "1.0" }
            )
        )
    }

    fun selectLemonfoxVoice(option: VoiceOption) {
        val language = option.label.substringAfter('(').substringBefore(')').ifBlank { "en-us" }
        updateVoiceSettings(
            _uiState.value.voiceSettings.copy(
                lemonfoxVoice = option.id,
                lemonfoxLanguage = language
            )
        )
    }

    fun setAgentVoiceProvider(agentId: String, provider: VoiceProvider) {
        val current = _uiState.value.agentVoiceConfigs[agentId] ?: AgentVoiceConfig()
        persistAgentVoiceConfigs(
            _uiState.value.agentVoiceConfigs + (
                agentId to current.copy(
                    provider = provider,
                    voiceId = defaultVoiceIdForProvider(provider),
                    voiceLabel = defaultVoiceLabelForProvider(provider)
                )
            )
        )
        refreshVoiceOptionsForProvider(provider, force = true)
    }

    fun selectAgentVoice(agentId: String, option: VoiceOption) {
        val current = _uiState.value.agentVoiceConfigs[agentId] ?: AgentVoiceConfig()
        persistAgentVoiceConfigs(
            _uiState.value.agentVoiceConfigs + (
                agentId to current.copy(
                    voiceId = option.id,
                    voiceLabel = option.label
                )
            )
        )
    }

    fun refreshVoiceOptionsForProvider(provider: VoiceProvider, force: Boolean = true) {
        refreshVoiceOptionsIfNeeded(_uiState.value.voiceSettings.copy(provider = provider), force = force)
    }

    fun saveCurrentVoiceProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val currentSettings = _uiState.value.voiceSettings
        val profiles = _uiState.value.ttsState.savedProfiles.toMutableList()
        val existingIndex = profiles.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
        val profile = VoiceProfile(
            id = profiles.getOrNull(existingIndex)?.id ?: "voice-profile-${System.currentTimeMillis()}",
            name = trimmed,
            settings = currentSettings
        )
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(0, profile)
        }
        persistVoiceProfiles(profiles, activeProfileId = profile.id)
    }

    fun applyVoiceProfile(profileId: String) {
        val profile = _uiState.value.ttsState.savedProfiles.firstOrNull { it.id == profileId } ?: return
        updateVoiceSettings(profile.settings, activeProfileId = profile.id)
        refreshVoiceOptionsIfNeeded(profile.settings, force = true)
    }

    fun deleteVoiceProfile(profileId: String) {
        val profiles = _uiState.value.ttsState.savedProfiles.filterNot { it.id == profileId }
        val nextActive = _uiState.value.ttsState.activeProfileId.takeUnless { it == profileId }
        persistVoiceProfiles(profiles, activeProfileId = nextActive)
    }

    fun playLatestMessage() {
        val roomId = _uiState.value.selectedRoomId ?: return
        val messages = _uiState.value.roomMessages[roomId].orEmpty()
        val latest = messages.lastOrNull { it.senderType == MessageSenderType.AGENT }
            ?: messages.lastOrNull { it.senderType != MessageSenderType.SYSTEM }
            ?: return
        playMessage(latest)
    }

    fun testVoiceSample() {
        val settings = _uiState.value.voiceSettings
        val sample = when (settings.provider) {
            VoiceProvider.System -> "This is the current system voice on your device."
            VoiceProvider.Cartesia -> "This is a Cartesia voice test for your OpenClaw room."
            VoiceProvider.Kokoro -> "This is a Kokoro voice test for your OpenClaw room."
            VoiceProvider.Lemonfox -> "This is a Lemonfox voice test for your OpenClaw room."
        }
        playMessage(
            RoomMessage(
                id = "voice-test",
                senderId = "voice-test",
                senderName = "Voice Test",
                senderRole = "System",
                senderType = MessageSenderType.SYSTEM,
                body = sample,
                timestampLabel = "Now"
            )
        )
    }

    fun playMessage(message: RoomMessage) {
        if (message.body.isBlank()) return
        val currentPlayback = _uiState.value.ttsState
        if (currentPlayback.currentMessageId == message.id) {
            if (currentPlayback.isPaused) {
                if (ttsEngine.resume()) {
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isPlaying = true,
                            isPaused = false,
                            queueCount = 1,
                            errorMessage = null
                        ),
                        errorMessage = null
                    )
                    return
                }
            } else if (currentPlayback.isPlaying) {
                if (ttsEngine.pause()) {
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isPlaying = false,
                            isPaused = true,
                            queueCount = 1,
                            errorMessage = null
                        ),
                        errorMessage = null
                    )
                } else {
                    stopPlayback()
                }
                return
            }
        }
        val settings = effectiveVoiceSettingsForMessage(message)
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(
                provider = settings.provider,
                activeVoiceLabel = voiceLabelFor(settings),
                isPlaying = true,
                isPaused = false,
                currentMessageId = message.id,
                queueCount = 1,
                errorMessage = null
            ),
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching {
                ttsEngine.speak(message.body, settings)
            }.onFailure { error ->
                reportFailure("play voice", error)
                _uiState.value = _uiState.value.copy(
                    ttsState = _uiState.value.ttsState.copy(
                        isPlaying = false,
                        isPaused = false,
                        currentMessageId = null,
                        queueCount = 0,
                        errorMessage = humanReadableError("Unable to play voice", error)
                    ),
                    errorMessage = humanReadableError("Unable to play voice", error)
                )
            }
        }
    }

    fun stopPlayback() {
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(
                isPlaying = false,
                isPaused = false,
                currentMessageId = null,
                queueCount = 0
            )
        )
    }

    private fun refreshRooms() {
        viewModelScope.launch {
            runCatching { repository.getRooms() }
                .onSuccess { rooms ->
                    val hiddenAgentIds = _uiState.value.hiddenAgentIds
                    val selected = _uiState.value.selectedRoomId
                        ?.takeIf { current ->
                            rooms.any { it.id == current } && !isHiddenAgentRoom(current, hiddenAgentIds)
                        }
                        ?: firstVisibleRoomId(rooms, hiddenAgentIds)
                    _uiState.value = _uiState.value.copy(
                        rooms = applyUnreadCounts(rooms),
                        selectedRoomId = selected
                    )
                    selected?.let(::refreshMessages)
                }.onFailure { error ->
                    reportFailure("load rooms", error)
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        errorMessage = humanReadableError("Unable to load rooms", error)
                    )
                }
        }
    }

    private fun refreshAgents() {
        viewModelScope.launch {
            runCatching { repository.getAgents() }
                .onSuccess { agents ->
                    val orderedAgents = applyStoredAgentOrder(agents)
                    val hiddenAgentIds = _uiState.value.hiddenAgentIds
                    val selected = _uiState.value.selectedAgentIds
                        .intersect(orderedAgents.map { it.id }.toSet()) - hiddenAgentIds
                    _uiState.value = _uiState.value.copy(
                        agents = orderedAgents,
                        selectedAgentIds = selected
                    )
                }.onFailure { error ->
                    reportFailure("load agents", error)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = humanReadableError("Unable to load agents", error)
                    )
                }
        }
    }

    private fun refreshMessages(roomId: String) {
        viewModelScope.launch {
            runCatching { repository.getRoomMessages(roomId) }
                .onSuccess { messages ->
                    val unreadAnchorKey = unreadAnchorKey(messages, roomReadStateStore.readLastReadMessageKey(roomId))
                    markRoomRead(roomId, messages)
                    val updatedRoomMessages = _uiState.value.roomMessages + (roomId to messages)
                    _uiState.value = _uiState.value.copy(
                        roomMessages = updatedRoomMessages,
                        rooms = applyUnreadCounts(_uiState.value.rooms, updatedRoomMessages),
                        selectedRoomUnreadAnchorKey = if (_uiState.value.selectedRoomId == roomId) unreadAnchorKey else _uiState.value.selectedRoomUnreadAnchorKey
                    )
                }.onFailure { error ->
                    reportFailure("load messages for $roomId", error)
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        errorMessage = humanReadableError("Unable to load messages", error)
                    )
                }
        }
    }

    private fun updateVoiceSettings(
        settings: VoiceSettings,
        activeProfileId: String? = matchingVoiceProfileId(settings, _uiState.value.ttsState.savedProfiles)
    ) {
        voiceSettingsStore.write(settings)
        _uiState.value = _uiState.value.copy(
            voiceSettings = settings,
            ttsState = _uiState.value.ttsState.copy(
                provider = settings.provider,
                activeVoiceLabel = voiceLabelFor(settings),
                activeProfileId = activeProfileId,
                errorMessage = null
            )
        )
    }

    private fun persistVoiceProfiles(
        profiles: List<VoiceProfile>,
        activeProfileId: String? = _uiState.value.ttsState.activeProfileId
    ) {
        voiceSettingsStore.writeProfiles(profiles)
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(
                savedProfiles = profiles,
                activeProfileId = activeProfileId
            )
        )
    }

    private fun persistAgentVoiceConfigs(configs: Map<String, AgentVoiceConfig>) {
        agentVoiceConfigStore.write(configs)
        _uiState.value = _uiState.value.copy(agentVoiceConfigs = configs)
    }

    private fun refreshVoiceOptionsIfNeeded(settings: VoiceSettings, force: Boolean = false) {
        if (settings.provider == VoiceProvider.System || settings.provider == VoiceProvider.Kokoro) {
            _uiState.value = _uiState.value.copy(
                ttsState = _uiState.value.ttsState.copy(
                    availableVoices = emptyList(),
                    isLoadingVoices = false
                )
            )
            return
        }
        if (settings.provider == VoiceProvider.Cartesia && settings.cartesiaApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                ttsState = _uiState.value.ttsState.copy(
                    availableVoices = emptyList(),
                    isLoadingVoices = false
                )
            )
            return
        }
        if (settings.provider == VoiceProvider.Lemonfox && settings.lemonfoxApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                ttsState = _uiState.value.ttsState.copy(
                    availableVoices = emptyList(),
                    isLoadingVoices = false
                )
            )
            return
        }
        if (!force && _uiState.value.ttsState.availableVoices.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                ttsState = _uiState.value.ttsState.copy(
                    isLoadingVoices = true,
                    errorMessage = null
                )
            )
            runCatching { ttsEngine.fetchAvailableVoices(settings) }
                .onSuccess { options ->
                    if (settings.provider == VoiceProvider.Cartesia) {
                        val selected = settings.cartesiaVoiceId
                        val selectedOption = options.firstOrNull { it.id == selected }
                        if (selectedOption != null && selectedOption.label != settings.cartesiaVoiceLabel) {
                            updateVoiceSettings(
                                _uiState.value.voiceSettings.copy(
                                    cartesiaVoiceLabel = selectedOption.label
                                )
                            )
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isLoadingVoices = false,
                            availableVoices = options,
                            errorMessage = null
                        )
                    )
                }
                .onFailure { error ->
                    reportFailure("load ${settings.provider.name.lowercase()} voices", error)
                    _uiState.value = _uiState.value.copy(
                        ttsState = _uiState.value.ttsState.copy(
                            isLoadingVoices = false,
                            availableVoices = emptyList(),
                            errorMessage = humanReadableError(
                                "Unable to load ${settings.provider.name.lowercase().replaceFirstChar { it.uppercase() }} voices",
                                error
                            )
                        )
                    )
                }
        }
    }

    private fun reportFailure(action: String, error: Throwable) {
        Log.e(logTag, "Failed to $action", error)
    }

    private fun applyStoredAgentOrder(agents: List<com.solovision.openclawagents.model.Agent>): List<com.solovision.openclawagents.model.Agent> {
        if (agents.isEmpty()) {
            agentOrderIds = emptyList()
            agentVisibilityStore.writeAgentOrderIds(emptyList())
            return emptyList()
        }

        val orderLookup = agentOrderIds
            .mapIndexed { index, id -> id.lowercase() to index }
            .toMap()

        val sorted = agents.sortedWith(
            compareBy<com.solovision.openclawagents.model.Agent> { orderLookup[it.id.lowercase()] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
        persistAgentOrder(sorted)
        return sorted
    }

    private fun persistAgentOrder(agents: List<com.solovision.openclawagents.model.Agent>) {
        agentOrderIds = agents.map { it.id }
        agentVisibilityStore.writeAgentOrderIds(agentOrderIds)
    }

    private fun humanReadableError(prefix: String, error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
        if (detail.contains("operator.admin", ignoreCase = true)) {
            return "$prefix: this device is connected without operator.admin, and deleting sessions requires admin scope."
        }
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }

    private suspend fun loadMessagesAfterSend(
        roomId: String,
        expectedUserText: String,
        optimisticMessage: RoomMessage
    ): List<RoomMessage> {
        repeat(4) { attempt ->
            val messages = repository.getRoomMessages(roomId)
            if (messages.any { it.senderType == MessageSenderType.USER && it.body == expectedUserText }) {
                return messages
            }
            if (attempt < 3) {
                delay(500)
            }
        }
        val latest = repository.getRoomMessages(roomId)
        return if (latest.any { it.id == optimisticMessage.id || (it.senderType == MessageSenderType.USER && it.body == expectedUserText) }) {
            latest
        } else {
            latest + optimisticMessage
        }
    }

    private fun applyUnreadCounts(
        rooms: List<CollaborationRoom>,
        roomMessages: Map<String, List<RoomMessage>> = _uiState.value.roomMessages
    ): List<CollaborationRoom> {
        return rooms.map { room ->
            val messages = roomMessages[room.id].orEmpty()
            room.copy(unreadCount = unreadCount(room.id, messages))
        }
    }

    private fun unreadCount(roomId: String, messages: List<RoomMessage>): Int {
        if (messages.isEmpty()) return 0
        val lastReadKey = roomReadStateStore.readLastReadMessageKey(roomId) ?: return 0
        val lastReadIndex = messages.indexOfLast { it.messageKey == lastReadKey }
        if (lastReadIndex < 0 || lastReadIndex >= messages.lastIndex) return 0
        return messages.subList(lastReadIndex + 1, messages.size)
            .count { it.senderType != MessageSenderType.SYSTEM }
    }

    private fun unreadAnchorKey(messages: List<RoomMessage>, lastReadMessageKey: String?): String? {
        if (messages.isEmpty() || lastReadMessageKey.isNullOrBlank()) return null
        val lastReadIndex = messages.indexOfLast { it.messageKey == lastReadMessageKey }
        if (lastReadIndex < 0 || lastReadIndex >= messages.lastIndex) return null
        return messages.getOrNull(lastReadIndex + 1)?.messageKey
    }

    private fun markRoomRead(roomId: String, messages: List<RoomMessage>) {
        val latestMessageKey = messages.lastOrNull()?.messageKey
        roomReadStateStore.writeLastReadMessageKey(roomId, latestMessageKey)
    }

    private fun buildLocalMessageKey(
        roomId: String,
        senderType: MessageSenderType,
        body: String,
        timestampMs: Long
    ): String {
        return listOf(roomId, senderType.name.lowercase(), timestampMs.toString(), body.trim())
            .joinToString("|")
    }

    private fun firstVisibleRoomId(
        rooms: List<CollaborationRoom>,
        hiddenAgentIds: Set<String>
    ): String? {
        return rooms.firstOrNull { room -> !isHiddenAgentRoom(room.id, hiddenAgentIds) }?.id
    }

    private fun isHiddenAgentRoom(roomId: String, hiddenAgentIds: Set<String>): Boolean {
        val agentId = roomId.removePrefix("agent:").substringBefore(':')
        return roomId.startsWith("agent:") && hiddenAgentIds.contains(agentId)
    }

    private fun isDirectSessionRoomForAgent(roomId: String, agentId: String): Boolean {
        return roomId.startsWith("agent:") &&
            roomId.removePrefix("agent:").substringBefore(':').equals(agentId, ignoreCase = true)
    }

    private fun effectiveVoiceSettingsForMessage(message: RoomMessage): VoiceSettings {
        if (message.senderType != MessageSenderType.AGENT) return _uiState.value.voiceSettings
        val config = _uiState.value.agentVoiceConfigs[message.senderId] ?: return _uiState.value.voiceSettings
        return when (config.provider) {
            VoiceProvider.System -> _uiState.value.voiceSettings.copy(provider = VoiceProvider.System)
            VoiceProvider.Cartesia -> _uiState.value.voiceSettings.copy(
                provider = VoiceProvider.Cartesia,
                cartesiaVoiceId = config.voiceId.ifBlank { _uiState.value.voiceSettings.cartesiaVoiceId },
                cartesiaVoiceLabel = config.voiceLabel.ifBlank { _uiState.value.voiceSettings.cartesiaVoiceLabel }
            )
            VoiceProvider.Kokoro -> _uiState.value.voiceSettings.copy(
                provider = VoiceProvider.Kokoro,
                kokoroVoice = config.voiceId.ifBlank { _uiState.value.voiceSettings.kokoroVoice }
            )
            VoiceProvider.Lemonfox -> _uiState.value.voiceSettings.copy(
                provider = VoiceProvider.Lemonfox,
                lemonfoxVoice = config.voiceId.ifBlank { _uiState.value.voiceSettings.lemonfoxVoice }
            )
        }
    }

    private fun voiceLabelFor(settings: VoiceSettings): String {
        return when (settings.provider) {
            VoiceProvider.System -> "System"
            VoiceProvider.Cartesia -> settings.cartesiaVoiceLabel.ifBlank { "Katie" }
            VoiceProvider.Kokoro -> settings.kokoroVoice.ifBlank { "af_heart" }
            VoiceProvider.Lemonfox -> settings.lemonfoxVoice.ifBlank { "sarah" }
        }
    }

    private fun defaultVoiceIdForProvider(provider: VoiceProvider): String {
        return when (provider) {
            VoiceProvider.System -> "system"
            VoiceProvider.Cartesia -> _uiState.value.voiceSettings.cartesiaVoiceId.ifBlank { "f786b574-daa5-4673-aa0c-cbe3e8534c02" }
            VoiceProvider.Kokoro -> _uiState.value.voiceSettings.kokoroVoice.ifBlank { "af_heart" }
            VoiceProvider.Lemonfox -> _uiState.value.voiceSettings.lemonfoxVoice.ifBlank { "sarah" }
        }
    }

    private fun defaultVoiceLabelForProvider(provider: VoiceProvider): String {
        return when (provider) {
            VoiceProvider.System -> "System"
            VoiceProvider.Cartesia -> _uiState.value.voiceSettings.cartesiaVoiceLabel.ifBlank { "Katie" }
            VoiceProvider.Kokoro -> _uiState.value.voiceSettings.kokoroVoice.ifBlank { "af_heart" }
            VoiceProvider.Lemonfox -> _uiState.value.voiceSettings.lemonfoxVoice.ifBlank { "sarah" }
        }
    }

    private fun matchingVoiceProfileId(
        settings: VoiceSettings,
        profiles: List<VoiceProfile>
    ): String? {
        return profiles.firstOrNull { it.settings == settings }?.id
    }

    override fun onCleared() {
        roomPollingJob?.cancel()
        ttsEngine.shutdown()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = buildRepository()
                @Suppress("UNCHECKED_CAST")
                return OpenClawViewModel(
                    repository = repository,
                    agentVisibilityStore = AgentVisibilityStore(context),
                    conversationDisplayStore = ConversationDisplayStore(context),
                    roomReadStateStore = RoomReadStateStore(context),
                    voiceSettingsStore = VoiceSettingsStore(context),
                    appThemeStore = AppThemeStore(context),
                    agentVoiceConfigStore = AgentVoiceConfigStore(context),
                    ttsEngine = ProviderBackedTtsEngine(context)
                ) as T
            }

            private fun buildRepository(): OpenClawRepository {
                val gatewayUrl = "wss://gateway.solobot.cloud"
                val sessionKey = "agent:orion:main"
                return runCatching {
                    RealOpenClawRepository(
                        GatewayRpcOpenClawTransport(
                            context = context,
                            config = OpenClawBackendConfig(
                                gatewayUrl = gatewayUrl,
                                sessionKey = sessionKey,
                                apiKey = "19ca7975c4842989d999110a09569394b203ef14916a4f08187f3e1482197633"
                            )
                        )
                    )
                }.getOrElse {
                    FakeOpenClawRepository()
                }
            }
        }
    }
}
