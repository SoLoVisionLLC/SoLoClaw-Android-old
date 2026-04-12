package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences

private const val CONVERSATION_DISPLAY_PREFS = "openclaw_conversation_display"
private const val PREF_SHOW_INTERNAL_MESSAGES = "show_internal_messages"

class ConversationDisplayStore private constructor(
    private val prefs: SharedPreferences?
) {

    constructor() : this(null)

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(CONVERSATION_DISPLAY_PREFS, Context.MODE_PRIVATE)
    )

    fun readShowInternalMessages(): Boolean {
        return prefs?.getBoolean(PREF_SHOW_INTERNAL_MESSAGES, true) ?: true
    }

    fun writeShowInternalMessages(show: Boolean) {
        prefs?.edit()?.putBoolean(PREF_SHOW_INTERNAL_MESSAGES, show)?.apply()
    }
}
