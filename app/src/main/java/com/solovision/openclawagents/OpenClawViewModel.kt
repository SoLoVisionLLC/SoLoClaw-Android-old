package com.solovision.openclawagents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solovision.openclawagents.data.AppSeedData
import com.solovision.openclawagents.data.FakeOpenClawRepository
import com.solovision.openclawagents.data.GatewayRpcOpenClawTransport
import com.solovision.openclawagents.data.OpenClawBackendConfig
import com.solovision.openclawagents.data.OpenClawRepository
import com.solovision.openclawagents.data.RealOpenClawRepository
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.TtsState
import com.solovision.openclawagents.tts.AndroidTtsEngine
import com.solovision.openclawagents.tts.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpenClawViewModel(
    private val repository: OpenClawRepository = FakeOpenClawRepository(),
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(
            agents = AppSeedData.agents,
            rooms = AppSeedData.rooms,
            selectedRoomId = AppSeedData.rooms.firstOrNull()?.id,
            roomMessages = AppSeedData.messagesByRoom,
            ttsState = TtsState()
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshRooms()
    }

    fun selectRoom(roomId: String) {
        _uiState.value = _uiState.value.copy(selectedRoomId = roomId)
        refreshMessages(roomId)
    }

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(draftMessage = text)
    }

    fun toggleCreateRoom(open: Boolean) {
        _uiState.value = _uiState.value.copy(creatingRoom = open)
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

    fun createRoom() {
        val state = _uiState.value
        val title = state.newRoomTitle.trim()
        val purpose = state.newRoomPurpose.trim()
        if (title.isBlank() || state.selectedAgentIds.isEmpty()) return

        viewModelScope.launch {
            val room = repository.createRoom(title, purpose.ifBlank { "New collaboration room" }, state.selectedAgentIds.toList())
            val rooms = repository.getRooms()
            val messages = repository.getRoomMessages(room.id)
            _uiState.value = state.copy(
                rooms = rooms,
                selectedRoomId = room.id,
                roomMessages = state.roomMessages + (room.id to messages),
                creatingRoom = false,
                newRoomTitle = "",
                newRoomPurpose = "",
                selectedAgentIds = emptySet()
            )
        }
    }

    fun sendCurrentMessage() {
        val state = _uiState.value
        val roomId = state.selectedRoomId ?: return
        val text = state.draftMessage.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(roomId, text)
            val updatedMessages = repository.getRoomMessages(roomId)
            _uiState.value = _uiState.value.copy(
                draftMessage = "",
                roomMessages = _uiState.value.roomMessages + (roomId to updatedMessages)
            )
        }
    }

    fun playMessage(text: String) {
        ttsEngine.speak(text)
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(isPlaying = true)
        )
    }

    fun stopPlayback() {
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(
            ttsState = _uiState.value.ttsState.copy(isPlaying = false)
        )
    }

    private fun refreshRooms() {
        viewModelScope.launch {
            runCatching { repository.getRooms() }
                .onSuccess { rooms ->
                    val selected = _uiState.value.selectedRoomId ?: rooms.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        rooms = rooms,
                        selectedRoomId = selected
                    )
                    selected?.let(::refreshMessages)
                }
        }
    }

    private fun refreshMessages(roomId: String) {
        viewModelScope.launch {
            runCatching { repository.getRoomMessages(roomId) }
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(
                        roomMessages = _uiState.value.roomMessages + (roomId to messages)
                    )
                }
        }
    }

    override fun onCleared() {
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
                    ttsEngine = AndroidTtsEngine(context)
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
