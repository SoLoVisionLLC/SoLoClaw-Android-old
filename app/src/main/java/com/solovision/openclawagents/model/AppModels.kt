package com.solovision.openclawagents.model

data class Agent(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val accent: Long,
    val summary: String,
    val availableForRooms: Boolean = true
)

data class CollaborationRoom(
    val id: String,
    val title: String,
    val purpose: String,
    val members: List<String>,
    val unreadCount: Int,
    val active: Boolean,
    val voiceMode: VoiceMode = VoiceMode.Auto,
    val lastActivity: String = "Now"
)

enum class MessageSenderType {
    USER,
    AGENT,
    SYSTEM
}

enum class VoiceMode {
    Off,
    Auto,
    OnDemand
}

data class RoomMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val senderType: MessageSenderType,
    val body: String,
    val timestampLabel: String,
    val spoken: Boolean = false
)

data class TtsState(
    val enabled: Boolean = true,
    val activeVoiceLabel: String = "Adaptive",
    val isPlaying: Boolean = false,
    val queueCount: Int = 2
)

data class AppUiState(
    val agents: List<Agent> = emptyList(),
    val rooms: List<CollaborationRoom> = emptyList(),
    val selectedRoomId: String? = null,
    val draftMessage: String = "",
    val roomMessages: Map<String, List<RoomMessage>> = emptyMap(),
    val ttsState: TtsState = TtsState(),
    val creatingRoom: Boolean = false,
    val newRoomTitle: String = "",
    val newRoomPurpose: String = "",
    val selectedAgentIds: Set<String> = emptySet()
)
