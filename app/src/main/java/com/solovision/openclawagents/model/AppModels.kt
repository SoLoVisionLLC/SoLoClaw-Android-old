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
    val lastActivity: String = "Now",
    val sessionLabel: String? = null
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

enum class VoiceProvider {
    System,
    Cartesia,
    Kokoro,
    Lemonfox
}

enum class AppThemeMode {
    Midnight,
    Obsidian,
    Nord,
    Dracula,
    TokyoNight,
    Monokai,
    Catppuccin,
    Snow,
    Latte,
    RosePine,
    Solarized,
    Paper,
    SoLoVision,
    Cyberpunk,
    Ocean
}

data class VoiceOption(
    val id: String,
    val label: String
)

data class VoiceSettings(
    val provider: VoiceProvider = VoiceProvider.System,
    val cartesiaApiKey: String = "",
    val cartesiaVoiceId: String = "",
    val cartesiaVoiceLabel: String = "",
    val cartesiaModelId: String = "sonic-3",
    val kokoroEndpoint: String = "",
    val kokoroApiKey: String = "",
    val kokoroModel: String = "kokoro",
    val kokoroVoice: String = "af_heart",
    val lemonfoxApiKey: String = "",
    val lemonfoxVoice: String = "sarah",
    val lemonfoxLanguage: String = "en-us",
    val lemonfoxSpeed: String = "1.0"
)

data class VoiceProfile(
    val id: String,
    val name: String,
    val settings: VoiceSettings
)

data class AgentVoiceConfig(
    val provider: VoiceProvider = VoiceProvider.System,
    val voiceId: String = "",
    val voiceLabel: String = ""
)

data class RoomMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val senderType: MessageSenderType,
    val body: String,
    val timestampLabel: String,
    val spoken: Boolean = false,
    val internal: Boolean = false,
    val messageKey: String = id,
    val timestampMs: Long? = null
)

data class TtsState(
    val enabled: Boolean = true,
    val provider: VoiceProvider = VoiceProvider.System,
    val activeVoiceLabel: String = "System",
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentMessageId: String? = null,
    val queueCount: Int = 0,
    val settingsExpanded: Boolean = false,
    val isLoadingVoices: Boolean = false,
    val availableVoices: List<VoiceOption> = emptyList(),
    val savedProfiles: List<VoiceProfile> = emptyList(),
    val activeProfileId: String? = null,
    val errorMessage: String? = null
)

data class AppUiState(
    val agents: List<Agent> = emptyList(),
    val rooms: List<CollaborationRoom> = emptyList(),
    val selectedRoomId: String? = null,
    val draftMessage: String = "",
    val roomMessages: Map<String, List<RoomMessage>> = emptyMap(),
    val voiceSettings: VoiceSettings = VoiceSettings(),
    val ttsState: TtsState = TtsState(),
    val agentVoiceConfigs: Map<String, AgentVoiceConfig> = emptyMap(),
    val creatingRoom: Boolean = false,
    val managingAgents: Boolean = false,
    val newRoomTitle: String = "",
    val newRoomPurpose: String = "",
    val selectedAgentIds: Set<String> = emptySet(),
    val hiddenAgentIds: Set<String> = emptySet(),
    val showInternalMessages: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.Midnight,
    val selectedRoomUnreadAnchorKey: String? = null,
    val isWorking: Boolean = false,
    val errorMessage: String? = null
)
