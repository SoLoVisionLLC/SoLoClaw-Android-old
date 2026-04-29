package com.solovision.openclawagents.tts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import com.solovision.openclawagents.data.GatewayEvent
import com.solovision.openclawagents.data.GatewayEventClient
import com.solovision.openclawagents.data.OpenClawRepository
import com.solovision.openclawagents.data.TalkRealtimeSessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class RealtimeTalkRelay(
    private val context: Context,
    private val repository: OpenClawRepository,
    private val sessionKey: String,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onStatus(status: Status, detail: String? = null)
        fun onTranscript(role: String, text: String, final: Boolean)
        suspend fun onAgentConsult(question: String): String
    }

    enum class Status { Connecting, Listening, Speaking, Error, Closed }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventClient: GatewayEventClient? = null
    private var session: TalkRealtimeSessionResult? = null
    private var record: AudioRecord? = null
    private var recordJob: Job? = null
    private var outputTrack: AudioTrack? = null
    @Volatile private var closed = false

    suspend fun start() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error("Microphone permission is required for realtime Talk")
        }
        callbacks.onStatus(Status.Connecting, "Opening persistent Gateway realtime socket...")
        val client = repository.openRealtimeEventClient(::handleGatewayEvent)
        eventClient = client
        callbacks.onStatus(Status.Connecting, "Opening Gateway realtime Talk session...")
        val opened = parseSessionResult(client.request("talk.realtime.session", mapOf("sessionKey" to sessionKey)))
        if (opened.transport != "gateway-relay") {
            error("Gateway returned realtime transport ${opened.transport ?: "webrtc-sdp"}; Android currently supports gateway-relay realtime sessions")
        }
        val audio = opened.audio ?: error("Gateway realtime session did not include audio config")
        if (opened.relaySessionId.isNullOrBlank()) error("Gateway realtime session did not include relaySessionId")
        if (audio.inputEncoding != "pcm16" || audio.outputEncoding != "pcm16") {
            error("Android realtime Talk currently requires pcm16 input/output; got ${audio.inputEncoding}/${audio.outputEncoding}")
        }
        session = opened
        outputTrack = buildOutputTrack(audio.outputSampleRateHz)
        outputTrack?.play()
        startMicrophonePump(opened)
        callbacks.onStatus(Status.Listening, "Realtime Talk relay active via ${opened.provider}.")
    }

    fun stop() {
        closed = true
        val relaySessionId = session?.relaySessionId
        val client = eventClient
        recordJob?.cancel()
        recordJob = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { outputTrack?.pause() }
        runCatching { outputTrack?.flush() }
        runCatching { outputTrack?.stop() }
        runCatching { outputTrack?.release() }
        outputTrack = null
        if (!relaySessionId.isNullOrBlank() && client != null) {
            scope.launch { runCatching { client.request("talk.realtime.relayStop", mapOf("relaySessionId" to relaySessionId)) } }
        }
        client?.close()
        eventClient = null
        callbacks.onStatus(Status.Closed, null)
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophonePump(session: TalkRealtimeSessionResult) {
        val audio = session.audio ?: return
        val relaySessionId = session.relaySessionId ?: return
        val minBuffer = AudioRecord.getMinBufferSize(
            audio.inputSampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(audio.inputSampleRateHz / 5 * 2)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(audio.inputSampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .build()
        record = recorder
        recorder.startRecording()
        recordJob = scope.launch {
            val buffer = ByteArray(minBuffer)
            val client = eventClient ?: return@launch
            while (isActive && !closed) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val audioBase64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    runCatching {
                        client.request(
                            "talk.realtime.relayAudio",
                            mapOf(
                                "relaySessionId" to relaySessionId,
                                "audioBase64" to audioBase64,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                    }.onFailure { callbacks.onStatus(Status.Error, it.message) }
                }
            }
        }
    }

    private fun parseSessionResult(response: Map<String, Any?>): TalkRealtimeSessionResult {
        val audio = (response["audio"] as? Map<*, *>)?.let { raw ->
            com.solovision.openclawagents.data.TalkRealtimeAudioConfig(
                inputEncoding = (raw["inputEncoding"] as? String).orEmpty(),
                inputSampleRateHz = (raw["inputSampleRateHz"] as? Number)?.toInt() ?: 24_000,
                outputEncoding = (raw["outputEncoding"] as? String).orEmpty(),
                outputSampleRateHz = (raw["outputSampleRateHz"] as? Number)?.toInt() ?: 24_000
            )
        }
        return TalkRealtimeSessionResult(
            provider = (response["provider"] as? String).orEmpty().ifBlank { "gateway" },
            transport = response["transport"] as? String,
            relaySessionId = response["relaySessionId"] as? String,
            audio = audio,
            model = response["model"] as? String,
            voice = response["voice"] as? String
        )
    }

    private fun handleGatewayEvent(event: GatewayEvent) {
        if (event.event != "talk.realtime.relay" || closed) return
        val active = session ?: return
        val payload = event.payload
        if ((payload["relaySessionId"] as? String) != active.relaySessionId) return
        when (payload["type"] as? String) {
            "ready" -> callbacks.onStatus(Status.Listening, "Realtime Talk relay ready.")
            "audio" -> {
                callbacks.onStatus(Status.Speaking, null)
                (payload["audioBase64"] as? String)?.let(::playPcm16)
            }
            "clear" -> clearOutput()
            "mark" -> ackMarkSoon()
            "transcript" -> {
                val role = payload["role"] as? String ?: return
                val text = payload["text"] as? String ?: return
                callbacks.onTranscript(role, text, payload["final"] as? Boolean ?: false)
            }
            "toolCall" -> handleToolCall(payload)
            "error" -> callbacks.onStatus(Status.Error, payload["message"] as? String)
            "close" -> callbacks.onStatus(Status.Closed, payload["reason"] as? String)
        }
    }

    private fun playPcm16(audioBase64: String) {
        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val track = outputTrack ?: return
        scope.launch {
            var offset = 0
            while (offset < bytes.size && !closed) {
                val written = track.write(bytes, offset, bytes.size - offset)
                if (written <= 0) break
                offset += written
            }
            callbacks.onStatus(Status.Listening, null)
        }
    }

    private fun clearOutput() {
        runCatching { outputTrack?.pause() }
        runCatching { outputTrack?.flush() }
        runCatching { outputTrack?.play() }
    }

    private fun ackMarkSoon() {
        val relaySessionId = session?.relaySessionId ?: return
        val client = eventClient ?: return
        scope.launch {
            delay(50)
            runCatching { client.request("talk.realtime.relayMark", mapOf("relaySessionId" to relaySessionId)) }
        }
    }

    private fun handleToolCall(payload: Map<String, Any?>) {
        val relaySessionId = session?.relaySessionId ?: return
        val client = eventClient ?: return
        val callId = payload["callId"] as? String ?: return
        val name = payload["name"] as? String ?: return
        scope.launch {
            val result: Any = if (name == "openclaw_agent_consult") {
                val question = extractQuestion(payload["args"])
                mapOf("result" to callbacks.onAgentConsult(question))
            } else {
                mapOf("error" to "Tool $name is not available in Android Talk")
            }
            runCatching {
                client.request(
                    "talk.realtime.relayToolResult",
                    mapOf("relaySessionId" to relaySessionId, "callId" to callId, "result" to result)
                )
            }
        }
    }

    private fun extractQuestion(args: Any?): String {
        val fromMap = (args as? Map<*, *>)?.let { it["question"] ?: it["prompt"] ?: it["input"] } as? String
        return fromMap ?: args?.toString().orEmpty()
    }

    private fun buildOutputTrack(sampleRateHz: Int): AudioTrack {
        val minBuffer = max(
            AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            sampleRateHz / 2 * 2
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .build()
    }
}
