package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.VoiceMode
import org.json.JSONArray
import org.json.JSONObject

private const val LOCAL_ROOM_PREFS = "openclaw_local_rooms"
private const val PREF_LOCAL_ROOM_STATE = "local_room_state_v1"

data class LocalRoomSnapshot(
    val rooms: List<CollaborationRoom> = emptyList(),
    val messages: Map<String, List<RoomMessage>> = emptyMap(),
    val replyCursors: Map<String, Map<String, String?>> = emptyMap(),
    val sessionKeys: Map<String, Map<String, String>> = emptyMap(),
    val memberNames: Map<String, Map<String, String>> = emptyMap()
)

class LocalRoomStore private constructor(
    private val prefs: SharedPreferences?
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(LOCAL_ROOM_PREFS, Context.MODE_PRIVATE)
    )

    fun read(): LocalRoomSnapshot {
        val raw = prefs?.getString(PREF_LOCAL_ROOM_STATE, null)?.trim().orEmpty()
        return decodeSnapshot(raw)
    }

    fun write(snapshot: LocalRoomSnapshot) {
        prefs?.edit()?.putString(PREF_LOCAL_ROOM_STATE, encodeSnapshot(snapshot).toString())?.apply()
    }

    companion object {
        fun decodeSnapshot(raw: String?): LocalRoomSnapshot {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return LocalRoomSnapshot()
            val codec = LocalRoomStore(null)
            return runCatching {
                with(codec) {
                    val root = JSONObject(trimmed)
                    val rooms = root.optJSONArray("rooms").toCollaborationRooms()
                    val messages = root.optJSONObject("messages").toMessagesByRoom()
                    val replyCursors = root.optJSONObject("replyCursors").toNullableStringMapByRoom()
                    val sessionKeys = root.optJSONObject("sessionKeys").toStringMapByRoom()
                    val memberNames = root.optJSONObject("memberNames").toStringMapByRoom()
                    LocalRoomSnapshot(
                        rooms = rooms,
                        messages = messages,
                        replyCursors = replyCursors,
                        sessionKeys = sessionKeys,
                        memberNames = memberNames
                    )
                }
            }.getOrElse { LocalRoomSnapshot() }
        }

        fun encodeSnapshot(snapshot: LocalRoomSnapshot): JSONObject {
            val codec = LocalRoomStore(null)
            return with(codec) {
                JSONObject().apply {
                    put("rooms", JSONArray().apply {
                        snapshot.rooms.forEach { room -> put(room.toJson()) }
                    })
                    put("messages", JSONObject().apply {
                        snapshot.messages.forEach { (roomId, roomMessages) ->
                            put(roomId, JSONArray().apply {
                                roomMessages
                                    .distinctBy { message -> message.id.ifBlank { message.messageKey } }
                                    .forEach { message -> put(message.toJson()) }
                            })
                        }
                    })
                    put("replyCursors", JSONObject().apply {
                        snapshot.replyCursors.forEach { (roomId, cursors) ->
                            put(roomId, JSONObject().apply {
                                cursors.forEach { (agentId, cursor) ->
                                    put(agentId, cursor)
                                }
                            })
                        }
                    })
                    put("sessionKeys", JSONObject().apply {
                        snapshot.sessionKeys.forEach { (roomId, sessionMap) ->
                            put(roomId, JSONObject().apply {
                                sessionMap.forEach { (agentId, sessionKey) ->
                                    put(agentId, sessionKey)
                                }
                            })
                        }
                    })
                    put("memberNames", JSONObject().apply {
                        snapshot.memberNames.forEach { (roomId, memberMap) ->
                            put(roomId, JSONObject().apply {
                                memberMap.forEach { (agentId, memberName) ->
                                    put(agentId, memberName)
                                }
                            })
                        }
                    })
                }
            }
        }
    }

    private fun JSONArray?.toCollaborationRooms(): List<CollaborationRoom> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val room = optJSONObject(index) ?: continue
                add(
                    CollaborationRoom(
                        id = room.optString("id"),
                        title = room.optString("title"),
                        purpose = room.optString("purpose"),
                        members = when {
                            room.has("memberIds") -> room.optJSONArray("memberIds").toStringList()
                            room.has("members") -> room.optJSONArray("members").toStringList()
                            else -> emptyList()
                        },
                        unreadCount = room.optInt("unreadCount", 0),
                        active = room.optBoolean("active", true),
                        voiceMode = room.optString("voiceMode")
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { VoiceMode.valueOf(it) }.getOrDefault(VoiceMode.Auto) }
                            ?: VoiceMode.Auto,
                        lastActivity = room.optString("lastActivity", "Now"),
                        sessionLabel = room.optString("sessionLabel").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun JSONObject?.toMessagesByRoom(): Map<String, List<RoomMessage>> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, List<RoomMessage>>()
        keys().forEach { roomId ->
            val entries = optJSONArray(roomId)
            val messages = buildList {
                if (entries != null) {
                    for (index in 0 until entries.length()) {
                        val message = entries.optJSONObject(index) ?: continue
                        add(
                            RoomMessage(
                                id = message.optString("id"),
                                senderId = message.optString("senderId"),
                                senderName = message.optString("senderName"),
                                senderRole = message.optString("senderRole"),
                                senderType = runCatching {
                                    MessageSenderType.valueOf(message.optString("senderType"))
                                }.getOrDefault(MessageSenderType.SYSTEM),
                                body = message.optString("body"),
                                timestampLabel = message.optString("timestampLabel", "Now"),
                                spoken = message.optBoolean("spoken", false),
                                internal = message.optBoolean("internal", false),
                                messageKey = message.optString("messageKey").ifBlank { message.optString("id") }
                            )
                        )
                    }
                }
            }
            out[roomId] = messages.distinctBy { message -> message.id.ifBlank { message.messageKey } }
        }
        return out
    }

    private fun JSONObject?.toNullableStringMapByRoom(): Map<String, Map<String, String?>> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, Map<String, String?>>()
        keys().forEach { roomId ->
            val value = optJSONObject(roomId) ?: return@forEach
            out[roomId] = buildMap {
                value.keys().forEach { key ->
                    put(key, if (value.isNull(key)) null else value.optString(key))
                }
            }
        }
        return out
    }

    private fun JSONObject?.toStringMapByRoom(): Map<String, Map<String, String>> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, Map<String, String>>()
        keys().forEach { roomId ->
            val value = optJSONObject(roomId) ?: return@forEach
            out[roomId] = buildMap {
                value.keys().forEach { key ->
                    val mapped = value.optString(key).trim()
                    if (mapped.isNotEmpty()) {
                        put(key, mapped)
                    }
                }
            }
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun CollaborationRoom.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("purpose", purpose)
            put("memberIds", JSONArray().apply { members.forEach(::put) })
            put("members", JSONArray().apply { members.forEach(::put) }) // backward compat
            put("unreadCount", unreadCount)
            put("active", active)
            put("voiceMode", voiceMode.name)
            put("lastActivity", lastActivity)
            put("sessionLabel", sessionLabel)
            put("createdAt", System.currentTimeMillis())
            put("isLocal", true)
        }
    }

    private fun RoomMessage.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderRole", senderRole)
            put("senderType", senderType.name)
            put("body", body)
            put("timestampLabel", timestampLabel)
            put("spoken", spoken)
            put("internal", internal)
            put("messageKey", messageKey)
        }
    }
}
