package com.aicortoonwala.jarvis

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class VoiceAssistant(private val context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false
    private var handsFree = false
    private var recognizer: SpeechRecognizer? = null
    private var onCommand: ((String) -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        try {
            tts.language = Locale.US
            tts.setSpeechRate(0.86f)
            tts.setPitch(0.72f)
            if (Build.VERSION.SDK_INT >= 21) {
                val voices = tts.voices.orEmpty()
                val candidates = voices
                    .filter { it.locale.language == "en" && it.locale.country.equals("US", true) }
                    .sortedWith(compareByDescending<Voice> {
                        val n = it.name.lowercase(Locale.US)
                        when {
                            n.contains("male") || n.contains("man") || n.contains("m1") -> 4
                            n.contains("local") -> 3
                            it.quality >= Voice.QUALITY_NORMAL -> 2
                            else -> 1
                        }
                    })
                candidates.firstOrNull()?.let { tts.voice = it }
            }
        } catch (_: Exception) {
            try { tts.language = Locale.US } catch (_: Exception) { }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (handsFree) Handler(context.mainLooper).postDelayed({ startListening() }, 500)
            }
        })
    }

    fun speak(text: String) {
        if (ready) tts.speak(text.replace(Regex("\\s+"), " ").trim(), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        else if (handsFree) scheduleRestart()
    }

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS is listening...")
    }

    fun startHandsFree(command: (String) -> Unit, status: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status("Voice recognition is not available on this phone.")
            return
        }
        onCommand = command
        onStatus = status
        handsFree = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener) }
        startListening()
    }

    fun stopHandsFree() {
        handsFree = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onStatus?.invoke("Hands-free off")
    }

    fun isHandsFree(): Boolean = handsFree

    private fun startListening() {
        if (!handsFree) return
        val r = recognizer ?: return
        try {
            r.startListening(intent())
            onStatus?.invoke("Hands-free: listening for JARVIS...")
        } catch (_: Exception) { scheduleRestart() }
    }

    private fun scheduleRestart() {
        if (handsFree) Handler(context.mainLooper).postDelayed({ startListening() }, 1000)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            val lower = heard.lowercase(Locale.getDefault())
            val marker = lower.indexOf("jarvis")
            if (marker >= 0) {
                val command = heard.substring(marker + "jarvis".length).trim(' ', ',', '.', '!', '?')
                if (command.isNotBlank()) { onCommand?.invoke(command); return }
            }
            scheduleRestart()
        }

        override fun onError(error: Int) { scheduleRestart() }
    }

    fun close() {
        handsFree = false
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
    }
}
