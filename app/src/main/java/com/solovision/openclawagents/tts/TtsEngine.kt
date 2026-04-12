package com.solovision.openclawagents.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.solovision.openclawagents.model.VoiceOption
import com.solovision.openclawagents.model.VoiceProvider
import com.solovision.openclawagents.model.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TtsPlaybackListener {
    fun onPlaybackStarted(provider: VoiceProvider, voiceLabel: String)
    fun onPlaybackFinished()
    fun onPlaybackError(message: String)
}

interface TtsEngine {
    fun setPlaybackListener(listener: TtsPlaybackListener?)
    suspend fun speak(text: String, settings: VoiceSettings)
    suspend fun fetchAvailableVoices(settings: VoiceSettings): List<VoiceOption>
    fun pause(): Boolean
    fun resume(): Boolean
    fun stop()
    fun shutdown()
}

class ProviderBackedTtsEngine(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) : TtsEngine {
    private val appContext = context.applicationContext
    private val systemEngine = AndroidTtsEngine(appContext)
    @Volatile
    private var playbackListener: TtsPlaybackListener? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activePlaybackFile: File? = null
    private val playbackGeneration = AtomicLong(0)
    private var remotePlaybackPaused = false

    override fun setPlaybackListener(listener: TtsPlaybackListener?) {
        playbackListener = listener
        systemEngine.setPlaybackListener(listener)
    }

    override suspend fun speak(text: String, settings: VoiceSettings) {
        val requestGeneration = playbackGeneration.incrementAndGet()
        stopPlayback(notifyListener = false)
        try {
            when (settings.provider) {
                VoiceProvider.System -> systemEngine.speak(text, settings.systemVoiceLabel())
                VoiceProvider.Cartesia -> speakCartesia(text, settings, requestGeneration)
                VoiceProvider.Kokoro -> speakKokoro(text, settings, requestGeneration)
                VoiceProvider.Lemonfox -> speakLemonfox(text, settings, requestGeneration)
            }
        } catch (error: Throwable) {
            if (isCurrentGeneration(requestGeneration)) throw error
        }
    }

    override suspend fun fetchAvailableVoices(settings: VoiceSettings): List<VoiceOption> {
        return when (settings.provider) {
            VoiceProvider.Cartesia -> fetchCartesiaVoices(settings)
            VoiceProvider.System -> listOf(VoiceOption(id = "system", label = settings.systemVoiceLabel()))
            VoiceProvider.Kokoro -> emptyList()
            VoiceProvider.Lemonfox -> lemonfoxVoices()
        }
    }

    override fun pause(): Boolean {
        val player = mediaPlayer ?: return false
        return runCatching {
            if (!player.isPlaying) return@runCatching false
            player.pause()
            remotePlaybackPaused = true
            true
        }.getOrDefault(false)
    }

    override fun resume(): Boolean {
        val player = mediaPlayer ?: return false
        return runCatching {
            if (!remotePlaybackPaused) return@runCatching false
            player.start()
            remotePlaybackPaused = false
            true
        }.getOrDefault(false)
    }

    override fun stop() {
        playbackGeneration.incrementAndGet()
        stopPlayback(notifyListener = true)
    }

    override fun shutdown() {
        stop()
        systemEngine.shutdown()
    }

    private suspend fun speakCartesia(text: String, settings: VoiceSettings, requestGeneration: Long) {
        val apiKey = settings.cartesiaApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Cartesia API key is missing.")
        }
        val voiceId = settings.cartesiaVoiceId.trim().ifBlank { DEFAULT_CARTESIA_VOICE_ID }
        val voiceLabel = settings.cartesiaVoiceLabel.ifBlank { DEFAULT_CARTESIA_VOICE_LABEL }
        val body = JSONObject().apply {
            put("model_id", settings.cartesiaModelId.ifBlank { DEFAULT_CARTESIA_MODEL_ID })
            put("transcript", text)
            put("voice", JSONObject().apply {
                put("mode", "id")
                put("id", voiceId)
            })
            put("output_format", JSONObject().apply {
                put("container", "wav")
                put("encoding", "pcm_s16le")
                put("sample_rate", 44100)
            })
            put("language", "en")
            put("generation_config", JSONObject().apply {
                put("speed", 1)
                put("emotion", "neutral")
                put("volume", 1)
            })
        }
        val request = Request.Builder()
            .url(CARTESIA_TTS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Cartesia-Version", CARTESIA_API_VERSION)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val audioBytes = executeForBytes(request, "Cartesia synthesis failed")
        playRemoteAudio(
            audioBytes = audioBytes,
            fileSuffix = ".wav",
            provider = VoiceProvider.Cartesia,
            voiceLabel = voiceLabel,
            requestGeneration = requestGeneration
        )
    }

    private suspend fun speakKokoro(text: String, settings: VoiceSettings, requestGeneration: Long) {
        val endpoint = settings.kokoroEndpoint.trim()
        if (endpoint.isBlank()) {
            throw IllegalStateException("Kokoro endpoint is missing.")
        }
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(
                JSONObject().apply {
                    put("model", settings.kokoroModel.ifBlank { DEFAULT_KOKORO_MODEL })
                    put("input", text)
                    put("voice", settings.kokoroVoice.ifBlank { DEFAULT_KOKORO_VOICE })
                    put("response_format", "mp3")
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
            )
        settings.kokoroApiKey.trim().takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val audioBytes = executeForBytes(
            requestBuilder.build(),
            "Kokoro synthesis failed"
        )
        playRemoteAudio(
            audioBytes = audioBytes,
            fileSuffix = ".mp3",
            provider = VoiceProvider.Kokoro,
            voiceLabel = settings.kokoroVoice.ifBlank { DEFAULT_KOKORO_VOICE },
            requestGeneration = requestGeneration
        )
    }

    private suspend fun speakLemonfox(text: String, settings: VoiceSettings, requestGeneration: Long) {
        val apiKey = settings.lemonfoxApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Lemonfox API key is missing.")
        }
        val voice = settings.lemonfoxVoice.ifBlank { DEFAULT_LEMONFOX_VOICE }
        val language = settings.lemonfoxLanguage.ifBlank { DEFAULT_LEMONFOX_LANGUAGE }
        val speed = settings.lemonfoxSpeed.toFloatOrNull()?.coerceIn(0.5f, 4.0f) ?: 1.0f
        val request = Request.Builder()
            .url(LEMONFOX_TTS_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(
                JSONObject().apply {
                    put("input", text)
                    put("voice", voice)
                    put("language", language)
                    put("response_format", "mp3")
                    put("speed", speed)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
            )
            .build()
        val audioBytes = executeForBytes(request, "Lemonfox synthesis failed")
        playRemoteAudio(
            audioBytes = audioBytes,
            fileSuffix = ".mp3",
            provider = VoiceProvider.Lemonfox,
            voiceLabel = voice,
            requestGeneration = requestGeneration
        )
    }

    private suspend fun fetchCartesiaVoices(settings: VoiceSettings): List<VoiceOption> {
        val apiKey = settings.cartesiaApiKey.trim()
        if (apiKey.isBlank()) return emptyList()
        val request = Request.Builder()
            .url(CARTESIA_VOICES_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Cartesia-Version", CARTESIA_API_VERSION)
            .get()
            .build()
        val raw = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty()
                    throw IOException("Cartesia voice list failed (${response.code}): $detail")
                }
                response.body?.string().orEmpty()
            }
        }
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val name = item.optString("name").ifBlank { id }
                val language = item.optString("language")
                add(
                    VoiceOption(
                        id = id,
                        label = if (language.isBlank()) name else "$name ($language)"
                    )
                )
            }
        }
    }

    private suspend fun executeForBytes(request: Request, failurePrefix: String): ByteArray {
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty()
                    throw IOException("$failurePrefix (${response.code}): $detail")
                }
                response.body?.bytes() ?: throw IOException("$failurePrefix: empty response body")
            }
        }
    }

    private suspend fun playRemoteAudio(
        audioBytes: ByteArray,
        fileSuffix: String,
        provider: VoiceProvider,
        voiceLabel: String,
        requestGeneration: Long
    ) {
        withContext(Dispatchers.IO) {
            val target = File.createTempFile("tts_", fileSuffix, appContext.cacheDir).apply {
                writeBytes(audioBytes)
            }
            if (!isCurrentGeneration(requestGeneration)) {
                target.delete()
                return@withContext
            }
            withContext(Dispatchers.Main) {
                if (!isCurrentGeneration(requestGeneration)) {
                    target.delete()
                    return@withContext
                }
                suspendCancellableCoroutine<Unit> { continuation ->
                    runCatching {
                        val player = MediaPlayer().apply {
                            setDataSource(target.absolutePath)
                            setOnPreparedListener {
                                if (!isCurrentGeneration(requestGeneration)) {
                                    cleanupPlayer(this, target)
                                    if (continuation.isActive) continuation.resume(Unit)
                                    return@setOnPreparedListener
                                }
                                mediaPlayer = this
                                activePlaybackFile = target
                                remotePlaybackPaused = false
                                playbackListener?.onPlaybackStarted(provider, voiceLabel)
                                start()
                            }
                            setOnCompletionListener {
                                cleanupPlayer(this, target)
                                playbackListener?.onPlaybackFinished()
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            setOnErrorListener { mp, _, extra ->
                                cleanupPlayer(mp, target)
                                val message = "Audio playback failed (extra=$extra)."
                                playbackListener?.onPlaybackError(message)
                                if (continuation.isActive) {
                                    continuation.resumeWithException(IllegalStateException(message))
                                }
                                true
                            }
                            prepareAsync()
                        }
                        continuation.invokeOnCancellation {
                            cleanupPlayer(player, target)
                        }
                    }.onFailure { error ->
                        cleanupPlayer(mediaPlayer, target)
                        playbackListener?.onPlaybackError(error.message ?: "Audio playback failed.")
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        }
    }

    private fun stopPlayback(notifyListener: Boolean) {
        remotePlaybackPaused = false
        systemEngine.stop(notifyListener)
        val hadRemotePlayback = mediaPlayer != null || activePlaybackFile != null
        mediaPlayer?.runCatching {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        activePlaybackFile?.delete()
        activePlaybackFile = null
        if (notifyListener && hadRemotePlayback) {
            playbackListener?.onPlaybackFinished()
        }
    }

    private fun isCurrentGeneration(requestGeneration: Long): Boolean {
        return playbackGeneration.get() == requestGeneration
    }

    private fun cleanupPlayer(player: MediaPlayer?, file: File?) {
        remotePlaybackPaused = false
        player?.runCatching {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        if (activePlaybackFile?.absolutePath == file?.absolutePath) {
            activePlaybackFile = null
        }
        file?.delete()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val CARTESIA_API_VERSION = "2026-03-01"
        private const val CARTESIA_TTS_URL = "https://api.cartesia.ai/tts/bytes"
        private const val CARTESIA_VOICES_URL = "https://api.cartesia.ai/voices"
        private const val LEMONFOX_TTS_URL = "https://api.lemonfox.ai/v1/audio/speech"
        private const val DEFAULT_CARTESIA_MODEL_ID = "sonic-3"
        private const val DEFAULT_CARTESIA_VOICE_ID = "f786b574-daa5-4673-aa0c-cbe3e8534c02"
        private const val DEFAULT_CARTESIA_VOICE_LABEL = "Katie"
        private const val DEFAULT_KOKORO_MODEL = "kokoro"
        private const val DEFAULT_KOKORO_VOICE = "af_heart"
        private const val DEFAULT_LEMONFOX_VOICE = "sarah"
        private const val DEFAULT_LEMONFOX_LANGUAGE = "en-us"
    }
}

private class AndroidTtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var pendingRequest: PendingUtterance? = null
    private var activeUtteranceId: String? = null
    private var activeVoiceLabel: String = "System"
    private var activeContinuation: (() -> Unit)? = null
    private var playbackListener: TtsPlaybackListener? = null

    init {
        textToSpeech = TextToSpeech(appContext, this).apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            playbackListener?.onPlaybackStarted(
                                provider = VoiceProvider.System,
                                voiceLabel = activeVoiceLabel
                            )
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            finishPlayback()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onError(utteranceId, TextToSpeech.ERROR)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == activeUtteranceId) {
                            playbackListener?.onPlaybackError("System voice failed (code=$errorCode).")
                            finishPlayback()
                        }
                    }
                }
            )
        }
    }

    fun setPlaybackListener(listener: TtsPlaybackListener?) {
        playbackListener = listener
    }

    override fun onInit(status: Int) {
        val engine = textToSpeech ?: return
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        val localeResult = engine.setLanguage(Locale.getDefault())
        if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        pendingRequest?.also { request ->
            pendingRequest = null
            activeVoiceLabel = request.voiceLabel
            engine.speak(
                request.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                request.utteranceId
            )
        }
    }

    suspend fun speak(text: String, voiceLabel: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            activeUtteranceId = utteranceId
            activeContinuation = {
                if (continuation.isActive) continuation.resume(Unit)
            }
            val request = PendingUtterance(
                text = text,
                utteranceId = utteranceId,
                voiceLabel = voiceLabel
            )
            val engine = textToSpeech
            if (!ready || engine == null) {
                pendingRequest = request
            } else {
                pendingRequest = request
                activeVoiceLabel = voiceLabel
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
            continuation.invokeOnCancellation { stop() }
        }
    }

    fun stop(notifyListener: Boolean = true) {
        textToSpeech?.stop()
        finishPlayback(notifyListener)
    }

    fun shutdown() {
        stop(notifyListener = false)
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
        pendingRequest = null
    }

    private fun finishPlayback(notifyListener: Boolean = true) {
        activeUtteranceId = null
        pendingRequest = null
        if (notifyListener) {
            playbackListener?.onPlaybackFinished()
        }
        activeContinuation?.invoke()
        activeContinuation = null
    }

    private data class PendingUtterance(
        val text: String,
        val utteranceId: String,
        val voiceLabel: String
    )
}

class StubTtsEngine : TtsEngine {
    override fun setPlaybackListener(listener: TtsPlaybackListener?) = Unit

    override suspend fun speak(text: String, settings: VoiceSettings) = Unit

    override suspend fun fetchAvailableVoices(settings: VoiceSettings): List<VoiceOption> = emptyList()

    override fun pause(): Boolean = false

    override fun resume(): Boolean = false

    override fun stop() = Unit

    override fun shutdown() = Unit
}

private fun VoiceSettings.systemVoiceLabel(): String = "System"

private fun lemonfoxVoices(): List<VoiceOption> {
    val englishUs = listOf(
        "heart", "bella", "michael", "alloy", "aoede", "kore", "jessica", "nicole",
        "nova", "river", "sarah", "sky", "echo", "eric", "fenrir", "liam", "onyx",
        "puck", "adam", "santa"
    )
    val englishGb = listOf(
        "alice", "emma", "isabella", "lily", "daniel", "fable", "george", "lewis"
    )
    return buildList {
        englishUs.forEach { voice ->
            add(VoiceOption(id = voice, label = "$voice (en-us)"))
        }
        englishGb.forEach { voice ->
            add(VoiceOption(id = voice, label = "$voice (en-gb)"))
        }
    }
}
