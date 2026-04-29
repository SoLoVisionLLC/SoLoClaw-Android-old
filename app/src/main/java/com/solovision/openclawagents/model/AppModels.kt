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
    val lemonfoxSpeed: String = "1.0",
    val speechLocale: String = "",
    val silenceTimeoutMs: Int = 700,
    val interruptOnSpeech: Boolean = true
)

enum class TalkPhase {
    Idle,
    Connecting,
    Listening,
    Thinking,
    Speaking,
    Error
}

data class TalkModeState(
    val talkEnabled: Boolean = false,
    val manualMicActive: Boolean = false,
    val phase: TalkPhase = TalkPhase.Idle,
    val lastTranscript: String = "",
    val statusMessage: String = "Talk is idle.",
    val errorMessage: String? = null,
    val providerStatus: String = "Gateway Talk provider preferred; Android system TTS fallback is ready."
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
    val messageKey: String = id
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

data class CronSchedule(
    val kind: String = "cron",
    val expr: String = "",
    val timezone: String? = null
)

data class CronDelivery(
    val mode: String = "none",
    val channel: String? = null,
    val target: String? = null
)

data class CronPayload(
    val kind: String = "systemEvent",
    val text: String = "",
    val model: String? = null
)

data class CronJob(
    val id: String,
    val name: String,
    val schedule: CronSchedule = CronSchedule(),
    val enabled: Boolean = true,
    val agentId: String? = null,
    val sessionTarget: String = "main",
    val payload: CronPayload = CronPayload(),
    val delivery: CronDelivery? = null,
    val lastRunAt: String? = null,
    val nextRunAt: String? = null,
    val lastStatus: String? = null,
    val consecutiveErrors: Int = 0,
    val lastError: String? = null
)

data class CronJobRun(
    val id: String,
    val status: String,
    val success: Boolean,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val output: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
    val sessionKey: String? = null
)

data class CronDraft(
    val id: String? = null,
    val name: String = "",
    val scheduleExpr: String = "0 9 * * *",
    val command: String = "",
    val agentId: String? = null,
    val sessionTarget: String = "main",
    val enabled: Boolean = true,
    val model: String? = null,
    val deliveryMode: String = "none",
    val deliveryChannel: String? = null,
    val deliveryTarget: String? = null
)

data class CronUiState(
    val jobs: List<CronJob> = emptyList(),
    val selectedJobId: String? = null,
    val runsByJobId: Map<String, List<CronJobRun>> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingRuns: Boolean = false,
    val runningJobId: String? = null,
    val supportsDelete: Boolean = false,
    val actionMessage: String? = null
)

data class SkillInstallOption(
    val id: String,
    val label: String
)

data class SkillMissingState(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList()
)

data class SkillSummary(
    val name: String,
    val skillKey: String = name,
    val description: String = "",
    val category: String = "General",
    val path: String = "",
    val source: String = "",
    val bundled: Boolean = false,
    val canUninstall: Boolean = false,
    val enabled: Boolean = true,
    val installed: Boolean = false,
    val eligible: Boolean? = null,
    val blockedByAllowlist: Boolean = false,
    val primaryEnv: String? = null,
    val assignedAgent: String? = null,
    val installOptions: List<SkillInstallOption> = emptyList(),
    val missing: SkillMissingState = SkillMissingState()
)

data class SkillFileEntry(
    val name: String,
    val relativePath: String,
    val size: Long,
    val modifiedAt: String
)

data class SkillHubEntry(
    val name: String,
    val description: String = "",
    val source: String = "",
    val identifier: String,
    val trustLevel: String = "",
    val repo: String? = null,
    val path: String? = null,
    val tags: List<String> = emptyList()
)

data class SkillsUiState(
    val skills: List<SkillSummary> = emptyList(),
    val hiddenSkillKeys: Set<String> = emptySet(),
    val selectedSkillKey: String? = null,
    val skillFiles: List<SkillFileEntry> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedFileContent: String = "",
    val isLoading: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val isSavingFile: Boolean = false,
    val isLoadingHub: Boolean = false,
    val supportsHttpActions: Boolean = false,
    val supportsHub: Boolean = false,
    val actingSkillKey: String? = null,
    val hubActingIdentifier: String? = null,
    val hubResults: List<SkillHubEntry> = emptyList(),
    val lastHubQuery: String = "",
    val actionLog: String? = null
)

data class MissionControlCapabilities(
    val supportsCronDelete: Boolean = false,
    val supportsSkillHttpActions: Boolean = false,
    val supportsSkillHub: Boolean = false
)

data class NotificationSettingsState(
    val enabled: Boolean = true,
    val messageNotificationsEnabled: Boolean = true,
    val cronNotificationsEnabled: Boolean = true,
    val backgroundSyncEnabled: Boolean = false,
    val permissionGranted: Boolean = true,
    val disabledRoomIds: Set<String> = emptySet(),
    val disabledCronJobIds: Set<String> = emptySet()
) {
    fun isRoomEnabled(roomId: String): Boolean = roomId !in disabledRoomIds
    fun isCronJobEnabled(jobId: String): Boolean = jobId !in disabledCronJobIds
}

data class AppUiState(
    val agents: List<Agent> = emptyList(),
    val rooms: List<CollaborationRoom> = emptyList(),
    val selectedRoomId: String? = null,
    val draftMessage: String = "",
    val roomMessages: Map<String, List<RoomMessage>> = emptyMap(),
    val voiceSettings: VoiceSettings = VoiceSettings(),
    val ttsState: TtsState = TtsState(),
    val talkMode: TalkModeState = TalkModeState(),
    val agentVoiceConfigs: Map<String, AgentVoiceConfig> = emptyMap(),
    val creatingRoom: Boolean = false,
    val managingAgents: Boolean = false,
    val newRoomTitle: String = "",
    val newRoomPurpose: String = "",
    val selectedAgentIds: Set<String> = emptySet(),
    val hiddenAgentIds: Set<String> = emptySet(),
    val showInternalMessages: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.Midnight,
    val cron: CronUiState = CronUiState(),
    val skills: SkillsUiState = SkillsUiState(),
    val notifications: NotificationSettingsState = NotificationSettingsState(),
    val selectedRoomUnreadAnchorKey: String? = null,
    val isWorking: Boolean = false,
    val errorMessage: String? = null
)
