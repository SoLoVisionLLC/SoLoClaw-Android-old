package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences
import com.solovision.openclawagents.model.VoiceProvider
import com.solovision.openclawagents.model.VoiceProfile
import com.solovision.openclawagents.model.VoiceSettings
import org.json.JSONArray
import org.json.JSONObject

class VoiceSettingsStore(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.applicationContext
        ?.getSharedPreferences(VOICE_SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun read(): VoiceSettings {
        val prefs = prefs ?: return VoiceSettings()
        return VoiceSettings(
            provider = prefs.getString(KEY_PROVIDER, null)
                ?.let { runCatching { VoiceProvider.valueOf(it) }.getOrDefault(VoiceProvider.System) }
                ?: VoiceProvider.System,
            cartesiaApiKey = prefs.getString(KEY_CARTESIA_API_KEY, "").orEmpty(),
            cartesiaVoiceId = prefs.getString(KEY_CARTESIA_VOICE_ID, "").orEmpty(),
            cartesiaVoiceLabel = prefs.getString(KEY_CARTESIA_VOICE_LABEL, "").orEmpty(),
            cartesiaModelId = prefs.getString(KEY_CARTESIA_MODEL_ID, "sonic-3").orEmpty(),
            kokoroEndpoint = prefs.getString(KEY_KOKORO_ENDPOINT, "").orEmpty(),
            kokoroApiKey = prefs.getString(KEY_KOKORO_API_KEY, "").orEmpty(),
            kokoroModel = prefs.getString(KEY_KOKORO_MODEL, "kokoro").orEmpty(),
            kokoroVoice = prefs.getString(KEY_KOKORO_VOICE, "af_heart").orEmpty(),
            lemonfoxApiKey = prefs.getString(KEY_LEMONFOX_API_KEY, "").orEmpty(),
            lemonfoxVoice = prefs.getString(KEY_LEMONFOX_VOICE, "sarah").orEmpty(),
            lemonfoxLanguage = prefs.getString(KEY_LEMONFOX_LANGUAGE, "en-us").orEmpty(),
            lemonfoxSpeed = prefs.getString(KEY_LEMONFOX_SPEED, "1.0").orEmpty()
        )
    }

    fun write(settings: VoiceSettings) {
        prefs?.edit()
            ?.putString(KEY_PROVIDER, settings.provider.name)
            ?.putString(KEY_CARTESIA_API_KEY, settings.cartesiaApiKey)
            ?.putString(KEY_CARTESIA_VOICE_ID, settings.cartesiaVoiceId)
            ?.putString(KEY_CARTESIA_VOICE_LABEL, settings.cartesiaVoiceLabel)
            ?.putString(KEY_CARTESIA_MODEL_ID, settings.cartesiaModelId)
            ?.putString(KEY_KOKORO_ENDPOINT, settings.kokoroEndpoint)
            ?.putString(KEY_KOKORO_API_KEY, settings.kokoroApiKey)
            ?.putString(KEY_KOKORO_MODEL, settings.kokoroModel)
            ?.putString(KEY_KOKORO_VOICE, settings.kokoroVoice)
            ?.putString(KEY_LEMONFOX_API_KEY, settings.lemonfoxApiKey)
            ?.putString(KEY_LEMONFOX_VOICE, settings.lemonfoxVoice)
            ?.putString(KEY_LEMONFOX_LANGUAGE, settings.lemonfoxLanguage)
            ?.putString(KEY_LEMONFOX_SPEED, settings.lemonfoxSpeed)
            ?.apply()
    }

    fun readProfiles(): List<VoiceProfile> {
        val raw = prefs?.getString(KEY_PROFILES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val settings = item.optJSONObject("settings")?.toVoiceSettings() ?: continue
                    if (id.isBlank() || name.isBlank()) continue
                    add(VoiceProfile(id = id, name = name, settings = settings))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun writeProfiles(profiles: List<VoiceProfile>) {
        prefs?.edit()
            ?.putString(
                KEY_PROFILES,
                JSONArray().apply {
                    profiles.forEach { put(it.toJson()) }
                }.toString()
            )
            ?.apply()
    }

    companion object {
        private const val VOICE_SETTINGS_PREFS = "voice_settings_store"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_CARTESIA_API_KEY = "cartesia_api_key"
        private const val KEY_CARTESIA_VOICE_ID = "cartesia_voice_id"
        private const val KEY_CARTESIA_VOICE_LABEL = "cartesia_voice_label"
        private const val KEY_CARTESIA_MODEL_ID = "cartesia_model_id"
        private const val KEY_KOKORO_ENDPOINT = "kokoro_endpoint"
        private const val KEY_KOKORO_API_KEY = "kokoro_api_key"
        private const val KEY_KOKORO_MODEL = "kokoro_model"
        private const val KEY_KOKORO_VOICE = "kokoro_voice"
        private const val KEY_LEMONFOX_API_KEY = "lemonfox_api_key"
        private const val KEY_LEMONFOX_VOICE = "lemonfox_voice"
        private const val KEY_LEMONFOX_LANGUAGE = "lemonfox_language"
        private const val KEY_LEMONFOX_SPEED = "lemonfox_speed"
        private const val KEY_PROFILES = "profiles"
    }
}

private fun VoiceProfile.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("settings", settings.toJson())
    }
}

private fun VoiceSettings.toJson(): JSONObject {
    return JSONObject().apply {
        put("provider", provider.name)
        put("cartesiaApiKey", cartesiaApiKey)
        put("cartesiaVoiceId", cartesiaVoiceId)
        put("cartesiaVoiceLabel", cartesiaVoiceLabel)
        put("cartesiaModelId", cartesiaModelId)
        put("kokoroEndpoint", kokoroEndpoint)
        put("kokoroApiKey", kokoroApiKey)
        put("kokoroModel", kokoroModel)
        put("kokoroVoice", kokoroVoice)
        put("lemonfoxApiKey", lemonfoxApiKey)
        put("lemonfoxVoice", lemonfoxVoice)
        put("lemonfoxLanguage", lemonfoxLanguage)
        put("lemonfoxSpeed", lemonfoxSpeed)
    }
}

private fun JSONObject.toVoiceSettings(): VoiceSettings {
    return VoiceSettings(
        provider = optString("provider")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { VoiceProvider.valueOf(it) }.getOrDefault(VoiceProvider.System) }
            ?: VoiceProvider.System,
        cartesiaApiKey = optString("cartesiaApiKey"),
        cartesiaVoiceId = optString("cartesiaVoiceId"),
        cartesiaVoiceLabel = optString("cartesiaVoiceLabel"),
        cartesiaModelId = optString("cartesiaModelId").ifBlank { "sonic-3" },
        kokoroEndpoint = optString("kokoroEndpoint"),
        kokoroApiKey = optString("kokoroApiKey"),
        kokoroModel = optString("kokoroModel").ifBlank { "kokoro" },
        kokoroVoice = optString("kokoroVoice").ifBlank { "af_heart" },
        lemonfoxApiKey = optString("lemonfoxApiKey"),
        lemonfoxVoice = optString("lemonfoxVoice").ifBlank { "sarah" },
        lemonfoxLanguage = optString("lemonfoxLanguage").ifBlank { "en-us" },
        lemonfoxSpeed = optString("lemonfoxSpeed").ifBlank { "1.0" }
    )
}
