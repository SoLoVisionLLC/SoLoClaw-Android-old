package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences
import com.solovision.openclawagents.model.AgentVoiceConfig
import com.solovision.openclawagents.model.VoiceProvider
import org.json.JSONObject

class AgentVoiceConfigStore(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.applicationContext
        ?.getSharedPreferences(AGENT_VOICE_CONFIG_PREFS, Context.MODE_PRIVATE)

    fun read(): Map<String, AgentVoiceConfig> {
        val raw = prefs?.getString(KEY_CONFIGS, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { agentId ->
                val json = root.optJSONObject(agentId) ?: JSONObject()
                AgentVoiceConfig(
                    provider = json.optString("provider")
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { VoiceProvider.valueOf(it) }.getOrDefault(VoiceProvider.System) }
                        ?: VoiceProvider.System,
                    voiceId = json.optString("voiceId"),
                    voiceLabel = json.optString("voiceLabel")
                )
            }
        }.getOrDefault(emptyMap())
    }

    fun write(configs: Map<String, AgentVoiceConfig>) {
        prefs?.edit()
            ?.putString(
                KEY_CONFIGS,
                JSONObject().apply {
                    configs.forEach { (agentId, config) ->
                        put(
                            agentId,
                            JSONObject().apply {
                                put("provider", config.provider.name)
                                put("voiceId", config.voiceId)
                                put("voiceLabel", config.voiceLabel)
                            }
                        )
                    }
                }.toString()
            )
            ?.apply()
    }

    companion object {
        private const val AGENT_VOICE_CONFIG_PREFS = "agent_voice_config_store"
        private const val KEY_CONFIGS = "configs"
    }
}
