package com.solovision.openclawagents

import android.content.Context
import android.util.Log
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
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.TtsState
import com.solovision.openclawagents.tts.AndroidTtsEngine
import com.solovision.openclawagents.tts.TtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpenClawViewModel(
    private val repository: OpenClawRepository = FakeOpenClawRepository(),
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val logTag = "OpenClawViewModel"

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
        _uiState.value = _uiState.value.copy(
            selectedRoomId = roomId,
            errorMessage = null
        )
        refreshMessages(roomId)
    }

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(
            draftMessage = text,
            errorMessage = null
        )
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
            }.onFailure { error ->
                reportFailure("create room", error)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    errorMessage = humanReadableError("Unable to create room", error)
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
                timestampLabel = "Now"
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
                _uiState.value = _uiState.value.copy(
                    roomMessages = _uiState.value.roomMessages + (roomId to updatedMessages),
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
                }.onFailure { error ->
                    reportFailure("load rooms", error)
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        errorMessage = humanReadableError("Unable to load rooms", error)
                    )
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
                }.onFailure { error ->
                    reportFailure("load messages for $roomId", error)
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        errorMessage = humanReadableError("Unable to load messages", error)
                    )
                }
        }
    }

    private fun reportFailure(action: String, error: Throwable) {
        Log.e(logTag, "Failed to $action", error)
    }

    private fun humanReadableError(prefix: String, error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
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
