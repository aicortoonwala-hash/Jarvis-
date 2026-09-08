package com.aicortoonwala.jarvis

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAssistant(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS is listening...")
    }

    fun close() = tts.shutdown()
}
