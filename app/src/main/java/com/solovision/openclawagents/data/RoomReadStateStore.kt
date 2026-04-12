package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences

private const val ROOM_READ_STATE_PREFS = "openclaw_room_read_state"
private const val PREF_LAST_READ_PREFIX = "last_read_message_key:"

class RoomReadStateStore private constructor(
    private val prefs: SharedPreferences?
) {

    constructor() : this(null)

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(ROOM_READ_STATE_PREFS, Context.MODE_PRIVATE)
    )

    fun readLastReadMessageKey(roomId: String): String? {
        return prefs?.getString(prefKey(roomId), null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun writeLastReadMessageKey(roomId: String, messageKey: String?) {
        val editor = prefs?.edit() ?: return
        if (messageKey.isNullOrBlank()) {
            editor.remove(prefKey(roomId))
        } else {
            editor.putString(prefKey(roomId), messageKey.trim())
        }
        editor.apply()
    }

    private fun prefKey(roomId: String): String = PREF_LAST_READ_PREFIX + roomId.trim()
}
