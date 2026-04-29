package com.solovision.openclawagents.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TalkAudioPlayer(private val context: Context) {
    @Volatile
    private var activeTrack: AudioTrack? = null
    @Volatile
    private var activePlayer: MediaPlayer? = null

    fun stop() {
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        activeTrack = null
        activePlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        activePlayer = null
    }

    suspend fun play(audioBase64: String, outputFormat: String?, mimeType: String?, fileExtension: String?) {
        val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val normalizedFormat = outputFormat.orEmpty().lowercase()
        if (normalizedFormat.startsWith("pcm_")) {
            playPcm(audioBytes, sampleRateFromOutputFormat(normalizedFormat))
        } else {
            playCompressed(audioBytes, fileExtension, mimeType)
        }
    }

    private suspend fun playPcm(audioBytes: ByteArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(audioBytes.size)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        activeTrack = track
        try {
            track.play()
            var offset = 0
            while (offset < audioBytes.size && activeTrack === track) {
                val written = track.write(audioBytes, offset, audioBytes.size - offset)
                if (written <= 0) break
                offset += written
            }
            runCatching { track.stop() }
        } finally {
            if (activeTrack === track) activeTrack = null
            runCatching { track.release() }
        }
    }

    private suspend fun playCompressed(audioBytes: ByteArray, fileExtension: String?, mimeType: String?) = withContext(Dispatchers.IO) {
        stop()
        val suffix = "." + (fileExtension?.trim()?.trimStart('.')?.ifBlank { null }
            ?: mimeTypeToExtension(mimeType)
            ?: "mp3")
        val target = File(context.cacheDir, "talk_${UUID.randomUUID()}$suffix")
        target.writeBytes(audioBytes)
        try {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                val player = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(target.absolutePath)
                    setOnPreparedListener {
                        activePlayer = this
                        start()
                    }
                    setOnCompletionListener {
                        if (completed.compareAndSet(false, true)) {
                            if (activePlayer === this) activePlayer = null
                            release()
                            continuation.resume(Unit)
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        if (completed.compareAndSet(false, true)) {
                            if (activePlayer === this) activePlayer = null
                            release()
                            continuation.resumeWithException(IllegalStateException("Talk playback failed ($what/$extra)"))
                        }
                        true
                    }
                }
                activePlayer = player
                continuation.invokeOnCancellation {
                    if (activePlayer === player) activePlayer = null
                    runCatching { player.stop() }
                    runCatching { player.release() }
                }
                player.prepareAsync()
            }
        } finally {
            target.delete()
        }
    }

    private fun sampleRateFromOutputFormat(outputFormat: String): Int {
        return outputFormat.substringAfter("pcm_", "24000")
            .takeWhile { it.isDigit() }
            .toIntOrNull()
            ?: 24_000
    }

    private fun mimeTypeToExtension(mimeType: String?): String? {
        return when (mimeType?.lowercase()?.substringBefore(';')) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/wave", "audio/x-wav" -> "wav"
            "audio/ogg" -> "ogg"
            "audio/aac" -> "aac"
            "audio/mp4" -> "m4a"
            else -> null
        }
    }
}
