package com.solovision.openclawagents.data

import android.content.Context
import android.util.Log
import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.VoiceSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val REQUESTED_OPERATOR_SCOPES = listOf(
    "operator.read",
    "operator.write",
    "operator.talk.secrets",
    "operator.admin"
)
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

interface OpenClawRepository {
    suspend fun getAgents(): List<Agent>
    suspend fun getRooms(): List<CollaborationRoom>
    suspend fun getRoomMessages(roomId: String): List<RoomMessage>
    suspend fun sendMessage(roomId: String, text: String)
    suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom
    suspend fun deleteRoom(roomId: String)
    suspend fun speakWithGatewayTalk(text: String, settings: VoiceSettings): TalkSpeechResult
    suspend fun createRealtimeTalkSession(sessionKey: String): TalkRealtimeSessionResult
    suspend fun openRealtimeEventClient(onEvent: (GatewayEvent) -> Unit): GatewayEventClient
}

data class TalkSpeechResult(
    val audioBase64: String,
    val provider: String,
    val outputFormat: String?,
    val mimeType: String?,
    val fileExtension: String?,
    val voiceCompatible: Boolean? = null
)

data class TalkRealtimeAudioConfig(
    val inputEncoding: String,
    val inputSampleRateHz: Int,
    val outputEncoding: String,
    val outputSampleRateHz: Int
)

data class TalkRealtimeSessionResult(
    val provider: String,
    val transport: String?,
    val relaySessionId: String?,
    val audio: TalkRealtimeAudioConfig?,
    val model: String? = null,
    val voice: String? = null
)

data class GatewayEvent(
    val event: String,
    val payload: Map<String, Any?>
)

interface GatewayEventClient {
    suspend fun request(method: String, params: Map<String, Any?> = emptyMap()): Map<String, Any?>
    fun close()
}

data class OpenClawBackendConfig(
    val gatewayUrl: String,
    val sessionKey: String,
    val apiKey: String? = null,
    val password: String? = null,
    val deviceLabel: String = "android"
)

data class SendRoomMessageRequest(
    val roomId: String,
    val text: String
)

data class CreateRoomRequest(
    val title: String,
    val purpose: String,
    val agentIds: List<String>
)

interface OpenClawTransport {
    suspend fun fetchAgents(): List<Agent>
    suspend fun fetchRooms(): List<CollaborationRoom>
    suspend fun fetchRoomMessages(roomId: String): List<RoomMessage>
    suspend fun sendRoomMessage(request: SendRoomMessageRequest)
    suspend fun createRoom(request: CreateRoomRequest): CollaborationRoom
    suspend fun deleteRoom(roomId: String)
    suspend fun speakWithGatewayTalk(text: String, settings: VoiceSettings): TalkSpeechResult
    suspend fun createRealtimeTalkSession(sessionKey: String): TalkRealtimeSessionResult
    suspend fun openRealtimeEventClient(onEvent: (GatewayEvent) -> Unit): GatewayEventClient
}

class FakeOpenClawRepository : OpenClawRepository {
    private val mutableRooms = AppSeedData.rooms.toMutableList()
    private val mutableMessages = AppSeedData.messagesByRoom.toMutableMap()

    override suspend fun getAgents(): List<Agent> = AppSeedData.agents

    override suspend fun getRooms(): List<CollaborationRoom> = mutableRooms

    override suspend fun getRoomMessages(roomId: String): List<RoomMessage> = mutableMessages[roomId].orEmpty()

    override suspend fun sendMessage(roomId: String, text: String) {
        val timestampMs = System.currentTimeMillis()
        val current = mutableMessages[roomId].orEmpty()
        mutableMessages[roomId] = current + RoomMessage(
            id = "user-$timestampMs",
            senderId = "solo",
            senderName = "SoLo",
            senderRole = "Operator",
            senderType = MessageSenderType.USER,
            body = text,
            timestampLabel = "Now",
            internal = false,
            messageKey = buildMessageKey("user", "solo", text)
        )
    }

    override suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom {
        val timestampMs = System.currentTimeMillis()
        val room = CollaborationRoom(
            id = title.lowercase().replace(" ", "-") + "-$timestampMs",
            title = title,
            purpose = purpose,
            members = agentIds,
            unreadCount = 0,
            active = true,
            lastActivity = "Now"
        )
        mutableRooms.add(0, room)
        mutableMessages[room.id] = listOf(
            RoomMessage(
                id = "system-$timestampMs",
                senderId = "system",
                senderName = "System",
                senderRole = "Room Created",
                senderType = MessageSenderType.SYSTEM,
                body = "Room created with ${agentIds.size} agents.",
                timestampLabel = "Now",
                internal = true,
                messageKey = buildMessageKey("system", "system", "Room created with ${agentIds.size} agents.")
            )
        )
        return room
    }

    override suspend fun deleteRoom(roomId: String) {
        mutableRooms.removeAll { it.id == roomId }
        mutableMessages.remove(roomId)
    }

    override suspend fun speakWithGatewayTalk(text: String, settings: VoiceSettings): TalkSpeechResult {
        throw UnsupportedOperationException("Gateway Talk playback is unavailable in fake mode")
    }

    override suspend fun createRealtimeTalkSession(sessionKey: String): TalkRealtimeSessionResult {
        throw UnsupportedOperationException("Realtime Gateway Talk is unavailable in fake mode")
    }

    override suspend fun openRealtimeEventClient(onEvent: (GatewayEvent) -> Unit): GatewayEventClient {
        throw UnsupportedOperationException("Realtime Gateway events are unavailable in fake mode")
    }
}

class RealOpenClawRepository(
    private val transport: OpenClawTransport
) : OpenClawRepository {
    override suspend fun getAgents(): List<Agent> = transport.fetchAgents()
    override suspend fun getRooms(): List<CollaborationRoom> = transport.fetchRooms()
    override suspend fun getRoomMessages(roomId: String): List<RoomMessage> = transport.fetchRoomMessages(roomId)
    override suspend fun sendMessage(roomId: String, text: String) = transport.sendRoomMessage(SendRoomMessageRequest(roomId, text))
    override suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom =
        transport.createRoom(CreateRoomRequest(title, purpose, agentIds))
    override suspend fun deleteRoom(roomId: String) = transport.deleteRoom(roomId)
    override suspend fun speakWithGatewayTalk(text: String, settings: VoiceSettings): TalkSpeechResult =
        transport.speakWithGatewayTalk(text, settings)
    override suspend fun createRealtimeTalkSession(sessionKey: String): TalkRealtimeSessionResult =
        transport.createRealtimeTalkSession(sessionKey)
    override suspend fun openRealtimeEventClient(onEvent: (GatewayEvent) -> Unit): GatewayEventClient =
        transport.openRealtimeEventClient(onEvent)
}

class GatewayRpcOpenClawTransport(
    context: Context,
    private val config: OpenClawBackendConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null
) : OpenClawTransport {

    private companion object {
        const val PRIMARY_SESSION_LABEL = "Halo"
    }

    private val deviceAuthStore = OpenClawDeviceAuthStore(context)
    private val deviceIdentity = OpenClawDeviceIdentity(deviceAuthStore)
    private val localRoomStore = LocalRoomStore(context)
    private val localRooms = linkedMapOf<String, CollaborationRoom>()
    private val localMessages = linkedMapOf<String, MutableList<RoomMessage>>()
    private val localRoomReplyCursor = linkedMapOf<String, MutableMap<String, String?>>()
    private val localRoomSessionKeys = linkedMapOf<String, Map<String, String>>()
    private val localRoomMemberNames = linkedMapOf<String, Map<String, String>>()
    @Volatile
    private var cachedAgents: List<Agent> = emptyList()

    private val client = okHttpClient ?: OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    init {
        restoreLocalRooms()
    }

    override suspend fun fetchAgents(): List<Agent> = withContext(dispatcher) {
        val agents = runCatching {
            val response = request(method = "agents.list", params = emptyMap())
            parseAgents(response)
        }.getOrElse { error ->
            Log.w("OpenClawGateway", "agents.list failed, deriving agents from sessions", error)
            deriveAgentsFromSessions()
        }
        cachedAgents = agents
        agents
    }

    override suspend fun fetchRooms(): List<CollaborationRoom> = withContext(dispatcher) {
        syncSharedRoomStateFromServer()
        val agents = cachedAgents.ifEmpty { fetchAgents() }
        val response = request(
            method = "sessions.list",
            params = mapOf(
                "limit" to 100,
                "includeGlobal" to true,
                "includeUnknown" to false
            )
        )

        val sessions = response["sessions"] as? List<*> ?: emptyList<Any>()
        val sessionMaps = sessions.mapNotNull { it as? Map<*, *> }

        val remoteRooms = if (agents.isEmpty()) {
            val matched = sessionMaps.filter { (it["key"] as? String) == config.sessionKey }
            if (matched.isEmpty()) {
                listOf(
                    CollaborationRoom(
                        id = config.sessionKey,
                        title = friendlySessionTitle(config.sessionKey),
                        purpose = "Live OpenClaw session",
                        members = listOf(agentIdFromSessionKey(config.sessionKey)),
                        unreadCount = 0,
                        active = true,
                        lastActivity = "Live",
                        sessionLabel = sessionLabelForKey(config.sessionKey)
                    )
                )
            } else {
                matched.map { session ->
                    val key = session["key"] as? String ?: config.sessionKey
                    CollaborationRoom(
                        id = key,
                        title = sessionDisplayName(session) ?: friendlySessionTitle(key),
                        purpose = "Live OpenClaw session",
                        members = listOf(agentIdFromSessionKey(key)),
                        unreadCount = 0,
                        active = true,
                        lastActivity = sessionUpdatedLabel(session),
                        sessionLabel = sessionDisplayLabel(session, key, friendlySessionTitle(key))
                    )
                }
            }
        } else {
            agents.flatMap { agent ->
                val agentSessions = sessionMaps
                    .filter { sessionBelongsToAgent(it, agent.id) }
                    .sortedByDescending { (it["updatedAt"] as? Number)?.toLong() ?: Long.MIN_VALUE }
                val mainSessionKey = agentMainSessionKey(agent.id)
                val hasMainSession = agentSessions.any { session ->
                    (session["key"] as? String)?.equals(mainSessionKey, ignoreCase = true) == true
                }

                if (agentSessions.isEmpty() || !hasMainSession) {
                    val mainRoom = CollaborationRoom(
                        id = mainSessionKey,
                        title = agent.name,
                        purpose = "Direct chat with ${agent.name}",
                        members = listOf(agent.id),
                        unreadCount = 0,
                        active = mainSessionKey == config.sessionKey,
                        lastActivity = if (agentSessions.isEmpty()) "Live" else "Available",
                        sessionLabel = PRIMARY_SESSION_LABEL
                    )
                    if (agentSessions.isEmpty()) {
                        listOf(mainRoom)
                    } else {
                        listOf(mainRoom) + agentSessions.map { session ->
                            val roomId = session["key"] as? String ?: mainSessionKey
                            val sessionLabel = sessionDisplayLabel(session, roomId, agent.name)
                            CollaborationRoom(
                                id = roomId,
                                title = agent.name,
                                purpose = "Direct chat with ${agent.name}",
                                members = listOf(agent.id),
                                unreadCount = 0,
                                active = roomId == config.sessionKey,
                                lastActivity = sessionUpdatedLabel(session),
                                sessionLabel = sessionLabel
                            )
                        }
                    }
                } else {
                    agentSessions.map { session ->
                        val roomId = session["key"] as? String ?: agentMainSessionKey(agent.id)
                        val sessionLabel = sessionDisplayLabel(session, roomId, agent.name)
                        CollaborationRoom(
                            id = roomId,
                            title = agent.name,
                            purpose = "Direct chat with ${agent.name}",
                            members = listOf(agent.id),
                            unreadCount = 0,
                            active = roomId == config.sessionKey,
                            lastActivity = sessionUpdatedLabel(session),
                            sessionLabel = sessionLabel
                        )
                    }
                }
            }
        }

        return@withContext (localRooms.values.toList() + remoteRooms)
            .distinctBy { it.id }
    }

    override suspend fun fetchRoomMessages(roomId: String): List<RoomMessage> = withContext(dispatcher) {
        if (localRooms.containsKey(roomId)) {
            syncLocalRoomReplies(roomId, awaitReplies = false)
            return@withContext localMessages[roomId].orEmpty().filterNot { isProtocolNoiseMessage(it.body) }.toList()
        }

        fetchRemoteRoomMessages(roomId)
    }

    override suspend fun sendRoomMessage(request: SendRoomMessageRequest) {
        withContext(dispatcher) {
            val localRoom = localRooms[request.roomId]
            if (localRoom != null) {
                val roomMessages = localMessages.getOrPut(request.roomId) { mutableListOf() }
                val memberSessionKeys = localRoomSessionKeys[request.roomId].orEmpty()
                val userTimestampMs = System.currentTimeMillis()
                roomMessages += RoomMessage(
                    id = "local-user-$userTimestampMs",
                    senderId = "solo",
                    senderName = "SoLo",
                    senderRole = "Operator",
                    senderType = MessageSenderType.USER,
                    body = request.text,
                    timestampLabel = "Now",
                    internal = false,
                    messageKey = buildMessageKey("user", "solo", request.text)
                )

                val targetAgentIds = resolveLocalRoomTargets(localRoom, roomMessages, request.text)
                if (targetAgentIds.isEmpty()) {
                    val systemTimestampMs = System.currentTimeMillis()
                    roomMessages += RoomMessage(
                        id = "local-system-$systemTimestampMs",
                        senderId = "system",
                        senderName = "System",
                        senderRole = "Routing",
                        senderType = MessageSenderType.SYSTEM,
                        body = "No agent was targeted. In group rooms, use @agent-name for one agent or @all for the full room.",
                        timestampLabel = "Now",
                        internal = true,
                        messageKey = buildMessageKey(
                            "system",
                            "system",
                            "No agent was targeted. In group rooms, use @agent-name for one agent or @all for the full room."
                        )
                    )
                    persistLocalRooms()
                    return@withContext
                }

                Log.d(
                    "OpenClawGateway",
                    "Routing local room message roomId=${request.roomId} targets=${targetAgentIds.joinToString()} memberCount=${localRoom.members.size}"
                )

                val failedAgents = mutableListOf<String>()
                targetAgentIds.forEach { agentId ->
                    val sessionKey = memberSessionKeys[agentId] ?: agentMainSessionKey(agentId)
                    runCatching {
                        sendRemoteChatMessage(
                            sessionKey = sessionKey,
                            text = buildGroupRelayPrompt(
                                room = localRoom,
                                roomMessages = roomMessages,
                                currentAgentId = agentId,
                                addressedAgentIds = targetAgentIds,
                                latestUserMessage = request.text
                            ),
                            idempotencyKey = "local-room-${request.roomId}-$agentId-${System.currentTimeMillis()}"
                        )
                    }.onFailure { error ->
                        Log.e("OpenClawGateway", "Failed relaying local room message to $agentId", error)
                        failedAgents += agentId
                    }
                }

                if (failedAgents.isNotEmpty()) {
                    val systemTimestampMs = System.currentTimeMillis()
                    roomMessages += RoomMessage(
                        id = "local-system-$systemTimestampMs",
                        senderId = "system",
                        senderName = "System",
                        senderRole = "Delivery",
                        senderType = MessageSenderType.SYSTEM,
                        body = "Could not reach: ${failedAgents.joinToString(", ")}",
                        timestampLabel = "Now",
                        internal = true,
                        messageKey = buildMessageKey(
                            "system",
                            "system",
                            "Could not reach: ${failedAgents.joinToString(", ")}"
                        )
                    )
                }

                syncLocalRoomReplies(request.roomId, awaitReplies = true)
                persistLocalRooms()
                pushSharedRoomStateToServer()
                return@withContext
            }

            sendRemoteChatMessage(
                sessionKey = request.roomId,
                text = request.text,
                idempotencyKey = "android-${System.currentTimeMillis()}"
            )
            delay(500)
        }
    }

    override suspend fun createRoom(request: CreateRoomRequest): CollaborationRoom = withContext(dispatcher) {
        syncSharedRoomStateFromServer()
        val timestampMs = System.currentTimeMillis()
        val room = CollaborationRoom(
            id = "local-room-$timestampMs",
            title = request.title.ifBlank { friendlySessionTitle(config.sessionKey) },
            purpose = request.purpose.ifBlank { "Shared team room" },
            members = request.agentIds.ifEmpty { listOf(agentIdFromSessionKey(config.sessionKey)) },
            unreadCount = 0,
            active = true,
            lastActivity = "Now"
        )
        val memberSessionKeys = room.members.distinct().associateWith { agentId ->
            groupRelaySessionKey(agentId = agentId, roomId = room.id, roomTitle = room.title)
        }
        val knownAgents = cachedAgents.ifEmpty { runCatching { fetchAgents() }.getOrDefault(emptyList()) }
            .associateBy { it.id.lowercase() }
        val memberNames = room.members.distinct().associateWith { agentId ->
            knownAgents[agentId.lowercase()]?.name ?: prettifyId(agentId)
        }
        localRooms[room.id] = room
        localRoomSessionKeys[room.id] = memberSessionKeys
        localRoomMemberNames[room.id] = memberNames
        localMessages[room.id] = mutableListOf(
            RoomMessage(
                id = "local-room-created-$timestampMs",
                senderId = "system",
                senderName = "System",
                senderRole = "Room Created",
                senderType = MessageSenderType.SYSTEM,
                body = "Room created for ${room.members.joinToString(", ")}. Use @agent-name to target one agent, or @all to broadcast to the full room. Messages will be relayed into room-specific agent sessions for ${room.title}.",
                timestampLabel = "Now",
                internal = true,
                messageKey = buildMessageKey(
                    "system",
                    "system",
                    "Room created for ${room.members.joinToString(", ")}. Use @agent-name to target one agent, or @all to broadcast to the full room. Messages will be relayed into room-specific agent sessions for ${room.title}."
                )
            )
        )
        localRoomReplyCursor[room.id] = memberSessionKeys.mapValues { (_, sessionKey) ->
            latestAssistantMessageKey(sessionKey)
        }.toMutableMap()
        persistLocalRooms()
        pushSharedRoomStateToServer()
        return@withContext room
    }

    override suspend fun deleteRoom(roomId: String) = withContext(dispatcher) {
        if (localRooms.containsKey(roomId)) {
            val sessionKeys = localRoomSessionKeys[roomId].orEmpty().values.distinct()
            val failedSessionKeys = mutableListOf<String>()
            sessionKeys.forEach { sessionKey ->
                runCatching {
                    deleteGatewaySession(sessionKey)
                }.onFailure { error ->
                    Log.e("OpenClawGateway", "Failed deleting relay session $sessionKey for roomId=$roomId", error)
                    failedSessionKeys += sessionKey
                }
            }

            if (failedSessionKeys.isNotEmpty()) {
                throw IllegalStateException(
                    "Could not delete all room sessions: ${failedSessionKeys.joinToString(", ")}"
                )
            }

            localRooms.remove(roomId)
            localMessages.remove(roomId)
            localRoomReplyCursor.remove(roomId)
            localRoomSessionKeys.remove(roomId)
            localRoomMemberNames.remove(roomId)
            persistLocalRooms()
            pushSharedRoomStateToServer()
            return@withContext
        }

        if (!roomId.startsWith("agent:")) {
            throw IllegalArgumentException("Only group rooms and direct agent sessions can be deleted.")
        }

        if (isMainSessionKey(roomId)) {
            throw IllegalArgumentException("Main direct sessions cannot be deleted.")
        }

        Log.d("OpenClawGateway", "Deleting remote session key=$roomId")
        deleteGatewaySession(roomId)
        Log.d("OpenClawGateway", "Deleted remote session key=$roomId")
    }

    suspend fun requestGatewayMethod(
        method: String,
        params: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> = request(method, params)

    override suspend fun speakWithGatewayTalk(text: String, settings: VoiceSettings): TalkSpeechResult = withContext(dispatcher) {
        val params = buildMap<String, Any?> {
            put("text", text)
            put("outputFormat", "pcm_24000")
            settings.speechLocale.trim().takeIf { it.isNotEmpty() }?.let { put("lang", it) }
        }
        val response = request(method = "talk.speak", params = params)
        val audioBase64 = response["audioBase64"] as? String
            ?: throw IllegalStateException("talk.speak returned no audio")
        return@withContext TalkSpeechResult(
            audioBase64 = audioBase64,
            provider = (response["provider"] as? String).orEmpty().ifBlank { "gateway" },
            outputFormat = response["outputFormat"] as? String,
            mimeType = response["mimeType"] as? String,
            fileExtension = response["fileExtension"] as? String,
            voiceCompatible = response["voiceCompatible"] as? Boolean
        )
    }

    override suspend fun createRealtimeTalkSession(sessionKey: String): TalkRealtimeSessionResult = withContext(dispatcher) {
        val response = request(
            method = "talk.realtime.session",
            params = mapOf("sessionKey" to sessionKey)
        )
        val audio = (response["audio"] as? Map<*, *>)?.let { raw ->
            TalkRealtimeAudioConfig(
                inputEncoding = (raw["inputEncoding"] as? String).orEmpty(),
                inputSampleRateHz = (raw["inputSampleRateHz"] as? Number)?.toInt() ?: 24_000,
                outputEncoding = (raw["outputEncoding"] as? String).orEmpty(),
                outputSampleRateHz = (raw["outputSampleRateHz"] as? Number)?.toInt() ?: 24_000
            )
        }
        TalkRealtimeSessionResult(
            provider = (response["provider"] as? String).orEmpty().ifBlank { "gateway" },
            transport = response["transport"] as? String,
            relaySessionId = response["relaySessionId"] as? String,
            audio = audio,
            model = response["model"] as? String,
            voice = response["voice"] as? String
        )
    }

    override suspend fun openRealtimeEventClient(onEvent: (GatewayEvent) -> Unit): GatewayEventClient = withContext(dispatcher) {
        PersistentGatewayEventClient(onEvent).also { it.connect() }
    }

    suspend fun requestHttpJson(
        path: String,
        method: String = "GET",
        body: String? = null
    ): String = withContext(dispatcher) {
        val requestBuilder = Request.Builder()
            .url("${apiBaseUrl()}${path.ensureLeadingSlash()}")
            .header("Content-Type", "application/json")

        config.apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { apiKey ->
            requestBuilder.header("Authorization", "Bearer $apiKey")
            requestBuilder.header("X-API-Key", apiKey)
        }

        val jsonType = "application/json; charset=utf-8".toMediaType()
        val request = when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody(jsonType)).build()
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody(jsonType)).build()
            "PATCH" -> requestBuilder.patch((body ?: "").toRequestBody(jsonType)).build()
            "DELETE" -> if (body == null) requestBuilder.delete().build()
                else requestBuilder.delete(body.toRequestBody(jsonType)).build()
            else -> requestBuilder.get().build()
        }

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${payload.ifBlank { response.message }}")
            }
            payload
        }
    }

    fun apiBaseUrl(): String {
        return config.gatewayUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .trimEnd('/')
    }

    private suspend fun fetchRemoteRoomMessages(roomId: String): List<RoomMessage> {
        val response = request(
            method = "chat.history",
            params = mapOf(
                "sessionKey" to roomId,
                "limit" to 50
            )
        )

        val messages = response["messages"] as? List<*> ?: emptyList<Any>()
        return messages.mapIndexedNotNull { index, raw ->
            val message = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val role = (message["role"] as? String)?.lowercase() ?: "assistant"
            val content = flattenContent(message["content"])
                ?: (message["text"] as? String)
                ?: return@mapIndexedNotNull null
            if (isProtocolNoiseMessage(content)) return@mapIndexedNotNull null
            val timestampMs = parseTimestampMs(message["timestamp"] ?: message["timestampMs"] ?: message["ts"])
            val senderId = when (role) {
                "user" -> "solo"
                "assistant" -> agentIdFromSessionKey(roomId)
                else -> "system"
            }
            val messageKey = buildMessageKey(role, senderId, content)
            val internal = isInternalHistoryMessage(role, content, message)

            when (role) {
                "user" -> RoomMessage(
                    id = messageKey,
                    senderId = senderId,
                    senderName = "SoLo",
                    senderRole = "Operator",
                    senderType = MessageSenderType.USER,
                    body = content,
                    timestampLabel = formatTimestampLabel(timestampMs),
                    internal = internal,
                    messageKey = messageKey
                )

                "assistant" -> RoomMessage(
                    id = messageKey,
                    senderId = senderId,
                    senderName = friendlySessionTitle(roomId),
                    senderRole = "Agent",
                    senderType = MessageSenderType.AGENT,
                    body = content,
                    timestampLabel = formatTimestampLabel(timestampMs),
                    internal = internal,
                    messageKey = messageKey
                )

                else -> RoomMessage(
                    id = messageKey,
                    senderId = senderId,
                    senderName = role.replaceFirstChar { it.uppercase() },
                    senderRole = "System",
                    senderType = MessageSenderType.SYSTEM,
                    body = content,
                    timestampLabel = formatTimestampLabel(timestampMs),
                    internal = internal,
                    messageKey = messageKey
                )
            }
        }
    }

    private suspend fun request(method: String, params: Map<String, Any?>): Map<String, Any?> = withContext(dispatcher) {
        suspendCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString()
            val clientId = "openclaw-android"
            val instanceId = UUID.randomUUID().toString()
            val connectRequestId = UUID.randomUUID().toString()
            val storedDeviceAuth = deviceAuthStore.read()
            var webSocket: WebSocket? = null
            var finished = false
            var connected = false
            var connectNonce: String? = null
            var grantedScopes: List<String> = storedDeviceAuth.grantedScopes
            var deviceToken: String? = storedDeviceAuth.deviceToken

            fun finish(result: Result<Map<String, Any?>>) {
                if (finished) return
                finished = true
                webSocket?.close(1000, null)
                result.onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }

            fun sendRpc(id: String, rpcMethod: String, rpcParams: Map<String, Any?>) {
                val payload = mapOf(
                    "type" to "req",
                    "id" to id,
                    "method" to rpcMethod,
                    "params" to rpcParams
                )
                webSocket?.send(SimpleJson.stringify(payload))
            }

            fun persistDeviceAuth(updatedDeviceToken: String?, updatedGrantedScopes: List<String>) {
                deviceToken = updatedDeviceToken ?: deviceToken
                grantedScopes = updatedGrantedScopes.ifEmpty { grantedScopes }
                deviceAuthStore.write(deviceToken, grantedScopes)
            }

            fun extractAndPersistDeviceAuth(source: Map<*, *>?) {
                val auth = source?.get("auth") as? Map<*, *>
                val nextDeviceToken = auth?.get("deviceToken") as? String
                val nextScopes = (auth?.get("scopes") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                if (nextDeviceToken != null || nextScopes.isNotEmpty()) {
                    persistDeviceAuth(nextDeviceToken, nextScopes)
                }
            }

            fun buildConnectParams(nonce: String?): Map<String, Any?> {
                val sharedToken = config.apiKey?.takeIf { it.isNotBlank() }
                val auth = buildMap<String, Any?> {
                    sharedToken?.let { put("token", it) }
                    config.password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
                }.takeIf { it.isNotEmpty() }

                val device = nonce?.takeIf { it.isNotBlank() }?.let { challenge ->
                    val signedAt = System.currentTimeMillis()
                    val deviceId = deviceIdentity.deviceId()
                    val publicKey = deviceIdentity.publicKeyBase64Url()
                    val payload = buildDeviceAuthPayloadV3(
                        deviceId = deviceId,
                        clientId = clientId,
                        clientMode = "ui",
                        role = "operator",
                        scopes = REQUESTED_OPERATOR_SCOPES,
                        signedAtMs = signedAt,
                        token = sharedToken,
                        nonce = challenge,
                        platform = "android",
                        deviceFamily = "Android"
                    )
                    val signature = deviceIdentity.signPayload(payload)
                    val selfVerified = deviceIdentity.verifySelfSignature(payload, signature)
                    Log.d(
                        "OpenClawGateway",
                        "Device auth payload deviceId=$deviceId publicKeyRawSize=${deviceIdentity.publicKeyRawSize()} payloadSha256=${sha256Hex(payload)} signatureChars=${signature.length} selfVerified=$selfVerified"
                    )
                    mapOf(
                        "id" to deviceId,
                        "publicKey" to publicKey,
                        "signature" to signature,
                        "signedAt" to signedAt,
                        "nonce" to challenge
                    )
                }

                return buildMap {
                    put("minProtocol", 3)
                    put("maxProtocol", 3)
                    put("client", mapOf(
                        "id" to clientId,
                        "displayName" to config.deviceLabel,
                        "version" to "0.1.0",
                        "platform" to "android",
                        "deviceFamily" to "Android",
                        "mode" to "ui",
                        "instanceId" to instanceId
                    ))
                    put("role", "operator")
                    put("scopes", REQUESTED_OPERATOR_SCOPES)
                    auth?.let { put("auth", it) }
                    device?.let { put("device", it) }
                    put("userAgent", "OpenClaw Agents Android/0.1.0")
                    put("locale", java.util.Locale.getDefault().toLanguageTag())
                }
            }

            val wsRequest = Request.Builder()
                .url(config.gatewayUrl)
                .build()

            webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("OpenClawGateway", "Socket opened url=${config.gatewayUrl} method=$method")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val parsed = SimpleJson.parseObject(text)
                        when (parsed["type"] as? String) {
                            "event" -> {
                                val event = parsed["event"] as? String
                                val payload = parsed["payload"] as? Map<*, *>
                                when (event) {
                                    "connect.challenge" -> {
                                        connectNonce = payload?.get("nonce") as? String
                                        Log.d(
                                            "OpenClawGateway",
                                            "Received connect challenge noncePresent=${!connectNonce.isNullOrBlank()} method=$method payloadKeys=${payload?.keys.orEmpty()}"
                                        )
                                        Log.d(
                                            "OpenClawGateway",
                                            "Sending connect params mode=ui role=operator hasApiKey=${!config.apiKey.isNullOrBlank()} hasDeviceToken=${!deviceToken.isNullOrBlank()} withDevice=${!connectNonce.isNullOrBlank()}"
                                        )
                                        sendRpc(connectRequestId, "connect", buildConnectParams(connectNonce))
                                    }
                                    "hello" -> extractAndPersistDeviceAuth(payload)
                                }
                            }
                            "res" -> {
                                val id = parsed["id"] as? String ?: return
                                val ok = parsed["ok"] as? Boolean ?: false
                                if (id == connectRequestId) {
                                    if (!ok) {
                                        val error = parsed["error"] as? Map<*, *>
                                        val details = error?.get("details") as? Map<*, *>
                                        val detailCode = details?.get("code") as? String
                                        val detailText = if (!detailCode.isNullOrBlank()) " [$detailCode]" else ""
                                        Log.e(
                                            "OpenClawGateway",
                                            "connect failed method=$method message=${error?.get("message")} details=$details"
                                        )
                                        finish(Result.failure(IllegalStateException((error?.get("message") as? String ?: "Gateway connect failed") + detailText)))
                                        return
                                    }
                                    val payload = parsed["payload"] as? Map<*, *>
                                    extractAndPersistDeviceAuth(payload)
                                    Log.d("OpenClawGateway", "connect ok, granted scopes=$grantedScopes, hasDeviceToken=${!deviceToken.isNullOrBlank()}")
                                    connected = true
                                    sendRpc(requestId, method, params)
                                    return
                                }
                                if (id == requestId) {
                                    if (!ok) {
                                        val error = parsed["error"] as? Map<*, *>
                                        val message = error?.get("message") as? String ?: "Gateway RPC failed"
                                        Log.e(
                                            "OpenClawGateway",
                                            "request failed method=$method params=${summarizeParams(params)} error=$error grantedScopes=$grantedScopes"
                                        )
                                        finish(Result.failure(IllegalStateException("$message (grantedScopes=$grantedScopes)")))
                                        return
                                    }
                                    Log.d(
                                        "OpenClawGateway",
                                        "request ok method=$method params=${summarizeParams(params)}"
                                    )
                                    @Suppress("UNCHECKED_CAST")
                                    finish(Result.success(parsed["payload"] as? Map<String, Any?> ?: emptyMap()))
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        finish(Result.failure(t))
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    onMessage(webSocket, bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("OpenClawGateway", "RPC failed", t)
                    finish(Result.failure(t))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!finished && !connected) {
                        finish(Result.failure(IllegalStateException("Gateway closed before connect: $code $reason")))
                    }
                }
            })
        }
    }

    private inner class PersistentGatewayEventClient(
        private val onEvent: (GatewayEvent) -> Unit
    ) : GatewayEventClient {
        private val pending = ConcurrentHashMap<String, CompletableDeferred<Map<String, Any?>>>()
        private var webSocket: WebSocket? = null
        private var connected = CompletableDeferred<Unit>()
        private val clientId = "openclaw-android"
        private val instanceId = UUID.randomUUID().toString()
        private val connectRequestId = UUID.randomUUID().toString()
        private val storedDeviceAuth = deviceAuthStore.read()
        private var connectNonce: String? = null
        private var grantedScopes: List<String> = storedDeviceAuth.grantedScopes
        private var deviceToken: String? = storedDeviceAuth.deviceToken

        suspend fun connect() {
            val wsRequest = Request.Builder().url(config.gatewayUrl).build()
            webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("OpenClawGateway", "Persistent socket opened url=${config.gatewayUrl}")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!connected.isCompleted) connected.completeExceptionally(t)
                    pending.values.forEach { it.completeExceptionally(t) }
                    pending.clear()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val error = IllegalStateException("Gateway socket closed: $code $reason")
                    if (!connected.isCompleted) connected.completeExceptionally(error)
                    pending.values.forEach { it.completeExceptionally(error) }
                    pending.clear()
                }
            })
            connected.await()
        }

        override suspend fun request(method: String, params: Map<String, Any?>): Map<String, Any?> {
            connected.await()
            val id = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Map<String, Any?>>()
            pending[id] = deferred
            sendRpc(id, method, params)
            return deferred.await()
        }

        override fun close() {
            pending.values.forEach { it.cancel() }
            pending.clear()
            webSocket?.close(1000, null)
            webSocket = null
        }

        private fun handleMessage(text: String) {
            try {
                val parsed = SimpleJson.parseObject(text)
                when (parsed["type"] as? String) {
                    "event" -> {
                        val event = parsed["event"] as? String
                        val payload = parsed["payload"] as? Map<*, *>
                        when (event) {
                            "connect.challenge" -> {
                                connectNonce = payload?.get("nonce") as? String
                                sendRpc(connectRequestId, "connect", buildConnectParams(connectNonce))
                            }
                            "hello" -> extractAndPersistDeviceAuth(payload)
                            null -> Unit
                            else -> {
                                @Suppress("UNCHECKED_CAST")
                                onEvent(GatewayEvent(event, payload as? Map<String, Any?> ?: emptyMap()))
                            }
                        }
                    }
                    "res" -> handleResponse(parsed)
                }
            } catch (t: Throwable) {
                Log.w("OpenClawGateway", "Persistent socket message failed", t)
            }
        }

        private fun handleResponse(parsed: Map<String, Any?>) {
            val id = parsed["id"] as? String ?: return
            val ok = parsed["ok"] as? Boolean ?: false
            if (id == connectRequestId) {
                if (!ok) {
                    val error = parsed["error"] as? Map<*, *>
                    connected.completeExceptionally(IllegalStateException(error?.get("message") as? String ?: "Gateway connect failed"))
                    return
                }
                extractAndPersistDeviceAuth(parsed["payload"] as? Map<*, *>)
                connected.complete(Unit)
                return
            }
            val deferred = pending.remove(id) ?: return
            if (!ok) {
                val error = parsed["error"] as? Map<*, *>
                deferred.completeExceptionally(IllegalStateException(error?.get("message") as? String ?: "Gateway RPC failed"))
                return
            }
            @Suppress("UNCHECKED_CAST")
            deferred.complete(parsed["payload"] as? Map<String, Any?> ?: emptyMap())
        }

        private fun sendRpc(id: String, rpcMethod: String, rpcParams: Map<String, Any?>) {
            val payload = mapOf(
                "type" to "req",
                "id" to id,
                "method" to rpcMethod,
                "params" to rpcParams
            )
            webSocket?.send(SimpleJson.stringify(payload))
        }

        private fun persistDeviceAuth(updatedDeviceToken: String?, updatedGrantedScopes: List<String>) {
            deviceToken = updatedDeviceToken ?: deviceToken
            grantedScopes = updatedGrantedScopes.ifEmpty { grantedScopes }
            deviceAuthStore.write(deviceToken, grantedScopes)
        }

        private fun extractAndPersistDeviceAuth(source: Map<*, *>?) {
            val auth = source?.get("auth") as? Map<*, *>
            val nextDeviceToken = auth?.get("deviceToken") as? String
            val nextScopes = (auth?.get("scopes") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            if (nextDeviceToken != null || nextScopes.isNotEmpty()) {
                persistDeviceAuth(nextDeviceToken, nextScopes)
            }
        }

        private fun buildConnectParams(nonce: String?): Map<String, Any?> {
            val sharedToken = config.apiKey?.takeIf { it.isNotBlank() }
            val auth = buildMap<String, Any?> {
                sharedToken?.let { put("token", it) }
                config.password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
            }.takeIf { it.isNotEmpty() }
            val device = nonce?.takeIf { it.isNotBlank() }?.let { challenge ->
                val signedAt = System.currentTimeMillis()
                val deviceId = deviceIdentity.deviceId()
                val publicKey = deviceIdentity.publicKeyBase64Url()
                val payload = buildDeviceAuthPayloadV3(
                    deviceId = deviceId,
                    clientId = clientId,
                    clientMode = "ui",
                    role = "operator",
                    scopes = REQUESTED_OPERATOR_SCOPES,
                    signedAtMs = signedAt,
                    token = sharedToken,
                    nonce = challenge,
                    platform = "android",
                    deviceFamily = "Android"
                )
                val signature = deviceIdentity.signPayload(payload)
                mapOf(
                    "id" to deviceId,
                    "publicKey" to publicKey,
                    "signature" to signature,
                    "signedAt" to signedAt,
                    "nonce" to challenge
                )
            }
            return buildMap {
                put("minProtocol", 3)
                put("maxProtocol", 3)
                put("client", mapOf(
                    "id" to clientId,
                    "displayName" to config.deviceLabel,
                    "version" to "0.1.0",
                    "platform" to "android",
                    "deviceFamily" to "Android",
                    "mode" to "ui",
                    "instanceId" to instanceId
                ))
                put("role", "operator")
                put("scopes", REQUESTED_OPERATOR_SCOPES)
                auth?.let { put("auth", it) }
                device?.let { put("device", it) }
                put("userAgent", "OpenClaw Agents Android/0.1.0")
                put("locale", java.util.Locale.getDefault().toLanguageTag())
            }
        }
    }

    private fun flattenContent(content: Any?): String? {
        return when (content) {
            is String -> content
            is List<*> -> content.mapNotNull { part ->
                val map = part as? Map<*, *> ?: return@mapNotNull null
                when (map["type"] as? String) {
                    "text" -> map["text"] as? String
                    else -> null
                }
            }.joinToString("\n").ifBlank { null }
            else -> null
        }
    }

    private fun parseTimestampMs(raw: Any?): Long? {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    private fun isInternalHistoryMessage(
        role: String,
        content: String,
        message: Map<*, *>
    ): Boolean {
        if (role != "assistant" && role != "user") {
            return true
        }
        val contentLower = content.lowercase()
        val classifiedType = listOf("type", "kind", "channel", "subtype")
            .mapNotNull { key -> message[key] as? String }
            .joinToString(" ")
            .lowercase()
        if (classifiedType.contains("tool") || classifiedType.contains("reason") || classifiedType.contains("think")) {
            return true
        }
        val internalMarkers = listOf(
            "<tool_call",
            "</tool_call>",
            "<function_call",
            "</function_call>",
            "<tool_calls",
            "</tool_calls>",
            "<function_calls",
            "</function_calls>",
            "<thinking",
            "</thinking>",
            "tool call:",
            "function call:",
            "thinking:",
            "reasoning:"
        )
        return internalMarkers.any(contentLower::contains)
    }

    private fun formatTimestampLabel(timestampMs: Long?): String {
        if (timestampMs == null) return "History"
        val minutes = ((System.currentTimeMillis() - timestampMs) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes <= 1L -> "Now"
            minutes < 60L -> "${minutes}m ago"
            else -> "History"
        }
    }

    private suspend fun sendRemoteChatMessage(
        sessionKey: String,
        text: String,
        idempotencyKey: String
    ) {
        Log.d(
            "OpenClawGateway",
            "chat.send sessionKey=$sessionKey length=${text.length} deliver=true"
        )
        request(
            method = "chat.send",
            params = mapOf(
                "sessionKey" to sessionKey,
                "message" to text,
                "deliver" to true,
                "thinking" to "medium",
                "timeoutMs" to 30_000,
                "idempotencyKey" to idempotencyKey
            )
        )
    }

    private suspend fun latestAssistantMessageKey(sessionKey: String): String? {
        return runCatching {
            fetchRemoteRoomMessages(sessionKey)
                .lastOrNull { it.senderType == MessageSenderType.AGENT }
                ?.messageKey
        }.getOrElse { error ->
            Log.w("OpenClawGateway", "Failed to snapshot assistant cursor for $sessionKey", error)
            null
        }
    }

    private suspend fun syncLocalRoomReplies(roomId: String, awaitReplies: Boolean) {
        val attempts = if (awaitReplies) 6 else 1
        var sawReply = false
        repeat(attempts) { attempt ->
            val added = syncLocalRoomRepliesOnce(roomId)
            sawReply = sawReply || added
            if (!awaitReplies) return
            if (sawReply && !added) return
            if (attempt < attempts - 1) {
                delay(1_000)
            }
        }
    }

    private suspend fun syncLocalRoomRepliesOnce(roomId: String): Boolean {
        val room = localRooms[roomId] ?: return false
        val roomMessages = localMessages[roomId] ?: return false
        val cursors = localRoomReplyCursor.getOrPut(roomId) { mutableMapOf() }
        val sessionKeys = localRoomSessionKeys[roomId].orEmpty()
        var addedAny = false

        room.members.distinct().forEach { agentId ->
            val sessionKey = sessionKeys[agentId] ?: agentMainSessionKey(agentId)
            val history = runCatching { fetchRemoteRoomMessages(sessionKey) }
                .getOrElse { error ->
                    Log.w("OpenClawGateway", "Failed syncing replies for $agentId", error)
                    return@forEach
                }
            val assistantMessages = history.filter { it.senderType == MessageSenderType.AGENT && !isProtocolNoiseMessage(it.body) }
            Log.d(
                "OpenClawGateway",
                "syncLocalRoomReplies roomId=$roomId agentId=$agentId sessionKey=$sessionKey historyCount=${history.size} assistantCount=${assistantMessages.size} previousKey=${cursors[agentId]}"
            )
            if (assistantMessages.isEmpty()) return@forEach

            val previousKey = cursors[agentId]
            val newMessages = when {
                previousKey.isNullOrBlank() -> assistantMessages
                else -> {
                    val lastSeenIndex = assistantMessages.indexOfLast { it.messageKey == previousKey }
                    if (lastSeenIndex >= 0) {
                        assistantMessages.drop(lastSeenIndex + 1)
                    } else {
                        assistantMessages.takeLast(1)
                    }
                }
            }
            Log.d(
                "OpenClawGateway",
                "syncLocalRoomReplies roomId=$roomId agentId=$agentId newAssistantCount=${newMessages.size}"
            )

            newMessages.forEach { message ->
                val mirroredId = "local-room-$roomId-${message.messageKey}"
                if (roomMessages.any { it.id == mirroredId }) return@forEach
                roomMessages += message.copy(
                    id = mirroredId,
                    messageKey = mirroredId,
                    senderName = resolveRoomAgentDisplayName(roomId, agentId),
                    internal = message.internal,
                    senderRole = message.senderRole.ifBlank { "Agent" }
                )
                addedAny = true
                Log.d(
                    "OpenClawGateway",
                    "Mirrored group reply roomId=$roomId agentId=$agentId mirroredId=$mirroredId bodyLength=${message.body.length}"
                )
            }

            cursors[agentId] = assistantMessages.last().messageKey
        }

        if (addedAny && roomMessages.isNotEmpty()) {
            localRooms[roomId] = room.copy(lastActivity = roomMessages.last().timestampLabel)
            persistLocalRooms()
            runCatching { pushSharedRoomStateToServer() }
                .onFailure { Log.w("OpenClawGateway", "Shared room save after reply sync failed", it) }
        }
        return addedAny
    }

    private fun restoreLocalRooms() {
        applyLocalRoomSnapshot(localRoomStore.read())
    }

    private fun persistLocalRooms() {
        localRoomStore.write(currentLocalRoomSnapshot())
    }

    private fun applyLocalRoomSnapshot(snapshot: LocalRoomSnapshot) {
        localRooms.clear()
        localMessages.clear()
        localRoomReplyCursor.clear()
        localRoomSessionKeys.clear()
        localRoomMemberNames.clear()

        snapshot.rooms.forEach { room ->
            localRooms[room.id] = room
        }
        snapshot.messages.forEach { (roomId, messages) ->
            localMessages[roomId] = messages.toMutableList()
        }
        snapshot.replyCursors.forEach { (roomId, cursors) ->
            localRoomReplyCursor[roomId] = cursors.toMutableMap()
        }
        localRoomSessionKeys.putAll(snapshot.sessionKeys)
        localRoomMemberNames.putAll(snapshot.memberNames)
    }

    private fun currentLocalRoomSnapshot(): LocalRoomSnapshot {
        return LocalRoomSnapshot(
            rooms = localRooms.values.toList(),
            messages = localMessages.mapValues { it.value.toList() },
            replyCursors = localRoomReplyCursor.mapValues { it.value.toMap() },
            sessionKeys = localRoomSessionKeys.toMap(),
            memberNames = localRoomMemberNames.toMap()
        )
    }

    private suspend fun syncSharedRoomStateFromServer() {
        val errors = mutableListOf<String>()
        for (url in sharedRoomStateUrls()) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Shared room fetch failed (${response.code})")
                    }
                    applyLocalRoomSnapshot(LocalRoomStore.decodeSnapshot(response.body?.string()))
                    persistLocalRooms()
                }
            }
            if (result.isSuccess) return
            val message = result.exceptionOrNull()?.message ?: "unknown"
            errors += "$url -> $message"
        }
        Log.w("OpenClawGateway", "Shared room fetch failed; using local cache. Tried: ${errors.joinToString(" | ")}")
    }

    private suspend fun pushSharedRoomStateToServer() {
        val payload = LocalRoomStore.encodeSnapshot(currentLocalRoomSnapshot()).apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
        }.toString()
        val errors = mutableListOf<String>()
        for (url in sharedRoomStateUrls()) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Shared room save failed (${response.code})")
                    }
                }
            }
            if (result.isSuccess) return
            val message = result.exceptionOrNull()?.message ?: "unknown"
            errors += "$url -> $message"
        }
        Log.w("OpenClawGateway", "Shared room save failed; kept local cache. Tried: ${errors.joinToString(" | ")}")
    }

    private fun sharedRoomStateUrls(): List<String> {
        val gatewayHttpBase = config.gatewayUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .trimEnd('/')
        val gatewayHttpUri = runCatching { URI(gatewayHttpBase) }.getOrNull()
        val gatewayHost = gatewayHttpUri?.host
        val gatewayScheme = gatewayHttpUri?.scheme ?: "https"

        return buildList {
            fun addCandidate(url: String?) {
                val normalized = url?.trim()?.takeIf { it.isNotBlank() } ?: return
                if (!contains(normalized)) add(normalized)
            }

            // Production devices reach the shared room state through the Cloudflare tunnel.
            // Do not prefer localhost here: on a physical Android device localhost is the phone,
            // not the SoLoBot host running the dashboard/gateway services.
            addCandidate("https://dash.solobot.cloud/api/group-rooms-state")
            gatewayHost?.let { host ->
                addCandidate("$gatewayScheme://$host/api/group-rooms-state")
            }
            addCandidate("https://dashboard.solobot.cloud/api/group-rooms-state")
            addCandidate("https://solobot.cloud/api/group-rooms-state")

            // Local/emulator fallbacks are kept last for development only.
            addCandidate("http://10.0.2.2:3124/api/group-rooms-state")
            addCandidate("http://127.0.0.1:3124/api/group-rooms-state")
            addCandidate("http://localhost:3124/api/group-rooms-state")
            addCandidate("http://10.0.2.2:3000/api/group-rooms-state")
            addCandidate("http://127.0.0.1:3000/api/group-rooms-state")
            addCandidate("http://localhost:3000/api/group-rooms-state")
        }
    }

    private fun groupRelaySessionKey(agentId: String, roomId: String, roomTitle: String): String {
        val slug = roomTitle
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "team-room" }
        val suffix = roomId.substringAfterLast('-').takeLast(8).ifBlank { "room" }
        return "agent:$agentId:room:$slug-$suffix"
    }

    private fun parseAgents(response: Map<String, Any?>): List<Agent> {
        val defaultId = (response["defaultId"] as? String)?.trim().orEmpty()
        val agents = response["agents"] as? List<*> ?: emptyList<Any>()
        return agents.mapNotNull { raw ->
            val agent = raw as? Map<*, *> ?: return@mapNotNull null
            val id = (agent["id"] as? String)?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            val identity = agent["identity"] as? Map<*, *>
            val name = (agent["name"] as? String)?.trim().takeUnless { it.isNullOrEmpty() } ?: prettifyId(id)
            val emoji = (identity?.get("emoji") as? String)?.trim().orEmpty()
            Agent(
                id = id,
                name = name,
                role = if (id == defaultId) "Default Agent" else "OpenClaw Agent",
                status = if (id == defaultId) "Default" else "Ready",
                accent = accentColorForAgent(id),
                summary = buildAgentSummary(name, emoji)
            )
        }.sortedBy { it.name.lowercase() }
    }

    private suspend fun deriveAgentsFromSessions(): List<Agent> {
        val response = request(
            method = "sessions.list",
            params = mapOf(
                "limit" to 100,
                "includeGlobal" to true,
                "includeUnknown" to false
            )
        )
        val sessions = response["sessions"] as? List<*> ?: emptyList<Any>()
        return sessions.mapNotNull { raw ->
            val session = raw as? Map<*, *> ?: return@mapNotNull null
            val key = session["key"] as? String ?: return@mapNotNull null
            if (!key.startsWith("agent:")) return@mapNotNull null
            val agentId = agentIdFromSessionKey(key)
            Agent(
                id = agentId,
                name = sessionDisplayName(session) ?: prettifyId(agentId),
                role = "OpenClaw Agent",
                status = "Ready",
                accent = accentColorForAgent(agentId),
                summary = "Live OpenClaw agent discovered from gateway sessions."
            )
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private fun summarizeParams(params: Map<String, Any?>): String {
        return params.entries.joinToString(
            prefix = "{",
            postfix = "}"
        ) { (key, value) ->
            val rendered = when (key) {
                "message" -> "len=${(value as? String)?.length ?: 0}"
                "sessionKey" -> value.toString()
                "idempotencyKey" -> "present"
                else -> value.toString()
            }
            "$key=$rendered"
        }
    }

    private fun buildDeviceAuthPayloadV3(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String,
        platform: String?,
        deviceFamily: String?
    ): String {
        return listOf(
            "v3",
            deviceId,
            clientId,
            clientMode,
            role,
            scopes.joinToString(","),
            signedAtMs.toString(),
            token.orEmpty(),
            nonce,
            normalizeDeviceAuthField(platform),
            normalizeDeviceAuthField(deviceFamily)
        ).joinToString("|")
    }

    private fun normalizeDeviceAuthField(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val out = StringBuilder(trimmed.length)
        for (char in trimmed) {
            if (char in 'A'..'Z') {
                out.append((char.code + 32).toChar())
            } else {
                out.append(char)
            }
        }
        return out.toString()
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun friendlySessionTitle(sessionKey: String): String {
        val agent = agentIdFromSessionKey(sessionKey)
        return displayAgentName(agent)
    }

    private suspend fun resolveLocalRoomTargets(
        room: CollaborationRoom,
        roomMessages: List<RoomMessage>,
        latestUserMessage: String
    ): List<String> {
        val members = room.members.distinct()
        if (members.size <= 1) return members

        if (isBroadcastMessage(latestUserMessage)) {
            return members
        }

        val normalizedMessage = latestUserMessage.lowercase()
        val explicitTargets = members.filter { agentId ->
            val aliases = buildAgentAliases(agentId, room.id)
            aliases.any { alias ->
                normalizedMessage.contains("@$alias") || normalizedMessage.contains(alias)
            }
        }
        if (explicitTargets.isNotEmpty()) {
            return explicitTargets
        }

        val previousAgentTarget = roomMessages
            .asReversed()
            .firstOrNull { it.senderType == MessageSenderType.AGENT && members.contains(it.senderId) }
            ?.senderId
        return previousAgentTarget?.let(::listOf) ?: members
    }

    private fun isBroadcastMessage(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf("@all", "everyone", "everybody", "all of you", "whole team", "entire team")
            .any { normalized.contains(it) }
    }

    private fun buildAgentAliases(agentId: String, roomId: String): Set<String> {
        val agentName = resolveRoomAgentDisplayName(roomId, agentId)
        val values = linkedSetOf<String>()
        values += sanitizeAlias(agentId)
        values += sanitizeAlias(prettifyId(agentId))
        agentName?.let {
            values += sanitizeAlias(it)
            it.split(' ', '-', '_').map(::sanitizeAlias).filterTo(values) { alias -> alias.isNotBlank() }
        }
        return values.filter { it.isNotBlank() }.toSet()
    }

    private fun sanitizeAlias(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun buildGroupRelayPrompt(
        room: CollaborationRoom,
        roomMessages: List<RoomMessage>,
        currentAgentId: String,
        addressedAgentIds: List<String>,
        latestUserMessage: String
    ): String {
        val participantLine = room.members.distinct().joinToString(", ") { resolveRoomAgentDisplayName(room.id, it) }
        val addressedLine = addressedAgentIds.joinToString(", ") { resolveRoomAgentDisplayName(room.id, it) }
        val currentAgentName = resolveRoomAgentDisplayName(room.id, currentAgentId)
        val transcript = buildRecentGroupTranscript(roomMessages)
        return buildString {
            appendLine("You are participating in the OpenClaw group room \"${room.title}\".")
            appendLine("You are $currentAgentName.")
            appendLine("Participants: $participantLine")
            appendLine("This turn is addressed to: $addressedLine")
            appendLine("Shared room purpose: ${room.purpose}")
            appendLine()
            appendLine("Recent room transcript:")
            appendLine(transcript)
            appendLine()
            appendLine("Latest user message:")
            appendLine(latestUserMessage)
            appendLine()
            append("Reply as $currentAgentName. Consider the recent room transcript so your response stays aware of what the rest of the team already said.")
        }.trim()
    }

    private fun buildRecentGroupTranscript(roomMessages: List<RoomMessage>): String {
        val transcriptLines = roomMessages
            .filter { it.senderType != MessageSenderType.SYSTEM }
            .takeLast(12)
            .map { message ->
                val speaker = when (message.senderType) {
                    MessageSenderType.USER -> "SoLo"
                    MessageSenderType.AGENT -> message.senderName
                    MessageSenderType.SYSTEM -> "System"
                }
                "[$speaker] ${message.body.trim()}"
        }
        return transcriptLines.joinToString("\n").ifBlank { "[No prior conversation]" }
    }

    private fun resolveRoomAgentDisplayName(roomId: String, agentId: String): String {
        return localRoomMemberNames[roomId]?.get(agentId)
            ?: displayAgentName(agentId)
    }

    private fun agentMainSessionKey(agentId: String): String = "agent:$agentId:main"

    private suspend fun deleteGatewaySession(sessionKey: String) {
        repeat(3) { attempt ->
            runCatching {
                request(
                    method = "sessions.abort",
                    params = mapOf("key" to sessionKey)
                )
            }.onSuccess { payload ->
                Log.d("OpenClawGateway", "Abort before delete key=$sessionKey payloadKeys=${payload.keys}")
            }.onFailure { error ->
                Log.w("OpenClawGateway", "Abort before delete failed key=$sessionKey", error)
            }

            try {
                val payload = request(
                    method = "sessions.delete",
                    params = mapOf(
                        "key" to sessionKey,
                        "deleteTranscript" to true
                    )
                )
                Log.d(
                    "OpenClawGateway",
                    "Delete session result key=$sessionKey deleted=${payload["deleted"]} archived=${payload["archived"]}"
                )
                return
            } catch (error: IllegalStateException) {
                val message = error.message.orEmpty()
                val shouldRetry = message.contains("still active", ignoreCase = true) && attempt < 2
                if (!shouldRetry) {
                    throw error
                }
                Log.w(
                    "OpenClawGateway",
                    "Delete session retrying key=$sessionKey attempt=${attempt + 1} reason=$message"
                )
                delay(1_000L * (attempt + 1))
            }
        }
    }

    private fun isMainSessionKey(sessionKey: String): Boolean {
        return sessionKey.split(':').drop(2).firstOrNull().equals("main", ignoreCase = true)
    }

    private fun sessionBelongsToAgent(session: Map<*, *>, agentId: String): Boolean {
        val sessionAgentId = (session["agentId"] as? String)?.trim()
        val key = (session["key"] as? String)?.trim().orEmpty()
        return sessionAgentId.equals(agentId, ignoreCase = true) || agentIdFromSessionKey(key).equals(agentId, ignoreCase = true)
    }

    private fun sessionDisplayLabel(session: Map<*, *>, key: String, agentName: String): String {
        val display = sessionDisplayName(session)?.takeIf { !it.equals(agentName, ignoreCase = true) }
        return display ?: sessionLabelForKey(key)
    }

    private fun sessionLabelForKey(key: String): String {
        val parts = key.split(':')
        if (parts.size < 3) return PRIMARY_SESSION_LABEL
        val tail = parts.drop(2)
        return when {
            tail.firstOrNull().equals("main", ignoreCase = true) -> PRIMARY_SESSION_LABEL
            tail.firstOrNull().equals("room", ignoreCase = true) -> tail.drop(1).joinToString(":").ifBlank { "Room Session" }
            else -> tail.joinToString(":").ifBlank { "Session" }
        }.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun displayAgentName(agentId: String): String {
        return cachedAgents.firstOrNull { it.id.equals(agentId, ignoreCase = true) }?.name
            ?: if (agentId.equals("main", ignoreCase = true)) PRIMARY_SESSION_LABEL else prettifyId(agentId)
    }

    private fun agentIdFromSessionKey(sessionKey: String): String {
        val parts = sessionKey.split(":")
        return if (parts.size >= 2 && parts.first() == "agent") parts[1] else sessionKey
    }

    private fun sessionDisplayName(session: Map<*, *>): String? {
        return (session["displayName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (session["label"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun sessionUpdatedLabel(session: Map<*, *>?): String {
        val updatedAt = (session?.get("updatedAt") as? Number)?.toLong()
        if (updatedAt == null || updatedAt <= 0L) return "Live"
        val minutes = ((System.currentTimeMillis() - updatedAt) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes <= 1L -> "Now"
            minutes < 60L -> "${minutes}m ago"
            else -> "Live"
        }
    }

    private fun prettifyId(id: String): String {
        return id
            .split('-', '_', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
            .ifBlank { id }
    }

    private fun buildAgentSummary(name: String, emoji: String): String {
        val intro = if (emoji.isNotEmpty()) "$emoji $name" else name
        return "$intro is available through the OpenClaw gateway."
    }

    private fun accentColorForAgent(id: String): Long {
        val palette = listOf(
            0xFF38BDF8L,
            0xFF22C55EL,
            0xFFF97316L,
            0xFFFB7185L,
            0xFF7C5CFFL,
            0xFFFACC15L
        )
        val index = id.lowercase().fold(0) { acc, char -> (acc * 31 + char.code) and Int.MAX_VALUE } % palette.size
        return palette[index]
    }
}

private object SimpleJson {
    fun parseObject(input: String): Map<String, Any?> = Parser(input).parseObject()

    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${escapeJson(value)}\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") {
            val key = it.key as? String ?: ""
            "\"${escapeJson(key)}\":${stringify(it.value)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { stringify(it) }
        else -> "\"${escapeJson(value.toString())}\""
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseObject(): Map<String, Any?> {
            skipWhitespace()
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return result
                    }
                    else -> error("Invalid JSON object at $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                result += parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return result
                    }
                    else -> error("Invalid JSON array at $index")
                }
            }
        }

        private fun parseValue(): Any? = when (peek()) {
            '"' -> parseString()
            '{' -> parseObject()
            '[' -> parseArray()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (index < text.length) {
                val c = text[index++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val escaped = text[index++]
                        sb.append(
                            when (escaped) {
                                '"' -> '"'
                                '\\' -> '\\'
                                '/' -> '/'
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    val hex = text.substring(index, index + 4)
                                    index += 4
                                    hex.toInt(16).toChar()
                                }
                                else -> escaped
                            }
                        )
                    }
                    else -> sb.append(c)
                }
            }
            error("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = index
            while (index < text.length && text[index] !in listOf(',', '}', ']', ' ', '\n', '\r', '\t')) {
                index++
            }
            val raw = text.substring(start, index)
            return if (raw.contains('.')) raw.toDouble() else raw.toLong()
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!text.startsWith(literal, index)) error("Expected $literal at $index")
            index += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun expect(char: Char) {
            if (peek() != char) error("Expected $char at $index")
            index++
        }

        private fun peek(): Char = text.getOrElse(index) { '\u0000' }
    }
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"

private fun buildMessageKey(role: String, senderId: String, body: String): String {
    val normalized = listOf(role.trim().lowercase(), senderId.trim().lowercase(), body.trim())
        .joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    val fingerprint = digest.joinToString("") { "%02x".format(it) }.take(16)
    return "msg-$fingerprint"
}
