package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences

private const val AGENT_VISIBILITY_PREFS = "openclaw_agent_visibility"
private const val PREF_HIDDEN_AGENT_IDS = "hidden_agent_ids"
private const val PREF_AGENT_ORDER_IDS = "agent_order_ids"

class AgentVisibilityStore private constructor(
    private val prefs: SharedPreferences?
) {

    constructor() : this(null)

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(AGENT_VISIBILITY_PREFS, Context.MODE_PRIVATE)
    )

    fun readHiddenAgentIds(): Set<String> {
        return prefs?.getStringSet(PREF_HIDDEN_AGENT_IDS, emptySet())
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
    }

    fun writeHiddenAgentIds(agentIds: Set<String>) {
        prefs?.edit()
            ?.putStringSet(
                PREF_HIDDEN_AGENT_IDS,
                agentIds.map(String::trim).filter(String::isNotEmpty).toSet()
            )
            ?.apply()
    }

    fun readAgentOrderIds(): List<String> {
        return prefs?.getString(PREF_AGENT_ORDER_IDS, "")
            .orEmpty()
            .split('\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    fun writeAgentOrderIds(agentIds: List<String>) {
        prefs?.edit()
            ?.putString(
                PREF_AGENT_ORDER_IDS,
                agentIds.map(String::trim).filter(String::isNotEmpty).joinToString("\n")
            )
            ?.apply()
    }
}
