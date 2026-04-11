package com.solovision.openclawagents.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

interface TtsEngine {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}

class AndroidTtsEngine(context: Context) : TtsEngine, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        val engine = textToSpeech ?: return
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return

        val localeResult = engine.setLanguage(Locale.getDefault())
        if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }

        pendingText?.also {
            pendingText = null
            speak(it)
        }
    }

    override fun speak(text: String) {
        val engine = textToSpeech
        if (!ready || engine == null) {
            pendingText = text
            return
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
        pendingText = null
    }
}

class StubTtsEngine : TtsEngine {
    override fun speak(text: String) = Unit

    override fun stop() = Unit

    override fun shutdown() = Unit
}
