package com.solovision.openclawagents.data

import android.content.Context
import android.util.Log
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface OpenClawRepository {
    suspend fun getRooms(): List<CollaborationRoom>
    suspend fun getRoomMessages(roomId: String): List<RoomMessage>
    suspend fun sendMessage(roomId: String, text: String)
    suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom
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
    suspend fun fetchRooms(): List<CollaborationRoom>
    suspend fun fetchRoomMessages(roomId: String): List<RoomMessage>
    suspend fun sendRoomMessage(request: SendRoomMessageRequest)
    suspend fun createRoom(request: CreateRoomRequest): CollaborationRoom
}

class FakeOpenClawRepository : OpenClawRepository {
    private val mutableRooms = AppSeedData.rooms.toMutableList()
    private val mutableMessages = AppSeedData.messagesByRoom.toMutableMap()

    override suspend fun getRooms(): List<CollaborationRoom> = mutableRooms

    override suspend fun getRoomMessages(roomId: String): List<RoomMessage> = mutableMessages[roomId].orEmpty()

    override suspend fun sendMessage(roomId: String, text: String) {
        val current = mutableMessages[roomId].orEmpty()
        mutableMessages[roomId] = current + RoomMessage(
            id = "user-${System.currentTimeMillis()}",
            senderId = "solo",
            senderName = "SoLo",
            senderRole = "Operator",
            senderType = MessageSenderType.USER,
            body = text,
            timestampLabel = "Now"
        )
    }

    override suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom {
        val room = CollaborationRoom(
            id = title.lowercase().replace(" ", "-") + "-${System.currentTimeMillis()}",
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
                id = "system-${System.currentTimeMillis()}",
                senderId = "system",
                senderName = "System",
                senderRole = "Room Created",
                senderType = MessageSenderType.SYSTEM,
                body = "Room created with ${agentIds.size} agents.",
                timestampLabel = "Now"
            )
        )
        return room
    }
}

class RealOpenClawRepository(
    private val transport: OpenClawTransport
) : OpenClawRepository {
    override suspend fun getRooms(): List<CollaborationRoom> = transport.fetchRooms()
    override suspend fun getRoomMessages(roomId: String): List<RoomMessage> = transport.fetchRoomMessages(roomId)
    override suspend fun sendMessage(roomId: String, text: String) = transport.sendRoomMessage(SendRoomMessageRequest(roomId, text))
    override suspend fun createRoom(title: String, purpose: String, agentIds: List<String>): CollaborationRoom =
        transport.createRoom(CreateRoomRequest(title, purpose, agentIds))
}

class GatewayRpcOpenClawTransport(
    context: Context,
    private val config: OpenClawBackendConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null
) : OpenClawTransport {

    private val deviceAuthStore = OpenClawDeviceAuthStore(context)
    private val deviceIdentity = OpenClawDeviceIdentity(
        alias = "${context.packageName}.openclaw.device"
    )

    private val client = okHttpClient ?: OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun fetchRooms(): List<CollaborationRoom> = withContext(dispatcher) {
        val response = request(
            method = "sessions.list",
            params = mapOf(
                "limit" to 20,
                "includeGlobal" to true,
                "includeUnknown" to false
            )
        )

        val sessions = response["sessions"] as? List<*> ?: emptyList<Any>()
        val matched = sessions.mapNotNull { it as? Map<*, *> }
            .filter { (it["key"] as? String) == config.sessionKey }

        if (matched.isEmpty()) {
            listOf(
                CollaborationRoom(
                    id = config.sessionKey,
                    title = friendlySessionTitle(config.sessionKey),
                    purpose = "Live OpenClaw session",
                    members = listOf(agentIdFromSessionKey(config.sessionKey)),
                    unreadCount = 0,
                    active = true,
                    lastActivity = "Live"
                )
            )
        } else {
            matched.map { session ->
                val key = session["key"] as? String ?: config.sessionKey
                CollaborationRoom(
                    id = key,
                    title = session["label"] as? String ?: friendlySessionTitle(key),
                    purpose = "Live OpenClaw session",
                    members = listOf(agentIdFromSessionKey(key)),
                    unreadCount = 0,
                    active = true,
                    lastActivity = "Live"
                )
            }
        }
    }

    override suspend fun fetchRoomMessages(roomId: String): List<RoomMessage> = withContext(dispatcher) {
        val response = request(
            method = "chat.history",
            params = mapOf(
                "sessionKey" to roomId,
                "limit" to 50
            )
        )

        val messages = response["messages"] as? List<*> ?: emptyList<Any>()
        messages.mapIndexedNotNull { index, raw ->
            val message = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val role = (message["role"] as? String)?.lowercase() ?: "assistant"
            val content = flattenContent(message["content"])
                ?: (message["text"] as? String)
                ?: return@mapIndexedNotNull null

            when (role) {
                "user" -> RoomMessage(
                    id = "history-user-$index",
                    senderId = "solo",
                    senderName = "SoLo",
                    senderRole = "Operator",
                    senderType = MessageSenderType.USER,
                    body = content,
                    timestampLabel = "History"
                )

                "assistant" -> RoomMessage(
                    id = "history-assistant-$index",
                    senderId = agentIdFromSessionKey(roomId),
                    senderName = friendlySessionTitle(roomId),
                    senderRole = "Agent",
                    senderType = MessageSenderType.AGENT,
                    body = content,
                    timestampLabel = "History"
                )

                else -> RoomMessage(
                    id = "history-system-$index",
                    senderId = "system",
                    senderName = role.replaceFirstChar { it.uppercase() },
                    senderRole = "System",
                    senderType = MessageSenderType.SYSTEM,
                    body = content,
                    timestampLabel = "History"
                )
            }
        }
    }

    override suspend fun sendRoomMessage(request: SendRoomMessageRequest) {
        withContext(dispatcher) {
            this@GatewayRpcOpenClawTransport.request(
                method = "chat.send",
                params = mapOf(
                    "sessionKey" to request.roomId,
                    "message" to request.text,
                    "deliver" to false,
                    "idempotencyKey" to "android-${System.currentTimeMillis()}"
                )
            )
        }
    }

    override suspend fun createRoom(request: CreateRoomRequest): CollaborationRoom {
        return CollaborationRoom(
            id = config.sessionKey,
            title = request.title.ifBlank { friendlySessionTitle(config.sessionKey) },
            purpose = request.purpose.ifBlank { "Live OpenClaw session" },
            members = request.agentIds.ifEmpty { listOf(agentIdFromSessionKey(config.sessionKey)) },
            unreadCount = 0,
            active = true,
            lastActivity = "Live"
        )
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
                val auth = buildMap<String, Any?> {
                    config.apiKey?.takeIf { it.isNotBlank() }?.let { put("token", it) }
                    config.password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
                    deviceToken?.takeIf { it.isNotBlank() }?.let { put("deviceToken", it) }
                }.takeIf { it.isNotEmpty() }

                val device = nonce?.takeIf { it.isNotBlank() }?.let { challenge ->
                    val signedAt = System.currentTimeMillis()
                    val deviceId = deviceIdentity.deviceId()
                    val publicKey = deviceIdentity.publicKeyBase64Url()
                    val requestedScopes = listOf("operator.admin", "operator.read", "operator.write", "operator.approvals", "operator.pairing")
                    val payload = listOf(
                        "v3",
                        deviceId,
                        clientId,
                        "control-ui",
                        "operator",
                        requestedScopes.joinToString(","),
                        signedAt.toString(),
                        deviceToken.orEmpty(),
                        challenge,
                        "Android",
                        "phone"
                    ).joinToString("|")
                    mapOf(
                        "id" to deviceId,
                        "publicKey" to publicKey,
                        "signature" to deviceIdentity.signPayload(payload),
                        "signedAt" to signedAt,
                        "nonce" to challenge
                    )
                }

                return buildMap {
                    put("minProtocol", 3)
                    put("maxProtocol", 3)
                    put("client", mapOf(
                        "id" to clientId,
                        "version" to "0.1.0",
                        "platform" to "Android",
                        "deviceFamily" to "phone",
                        "mode" to "control-ui",
                        "instanceId" to instanceId
                    ))
                    put("role", "operator")
                    put("scopes", listOf("operator.admin", "operator.read", "operator.write", "operator.approvals", "operator.pairing"))
                    put("caps", listOf("tool-events"))
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
                    Unit
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
                                        finish(Result.failure(IllegalStateException("$message (grantedScopes=$grantedScopes)")))
                                        return
                                    }
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

    private fun friendlySessionTitle(sessionKey: String): String {
        val agent = agentIdFromSessionKey(sessionKey)
        return agent.replaceFirstChar { it.uppercase() }
    }

    private fun agentIdFromSessionKey(sessionKey: String): String {
        val parts = sessionKey.split(":")
        return if (parts.size >= 2 && parts.first() == "agent") parts[1] else sessionKey
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
