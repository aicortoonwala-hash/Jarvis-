package com.aicortoonwala.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.aicortoonwala.jarvis.START_VOICE"
        const val ACTION_STOP = "com.aicortoonwala.jarvis.STOP_VOICE"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 1001
    }

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var active = false
    private var speaking = false
    private val handler = Handler(mainLooper)
    private lateinit var router: CommandRouter
    private lateinit var memory: MemoryStore
    private val market = MarketClient()

    override fun onCreate() {
        super.onCreate()
        memory = MemoryStore(this)
        router = CommandRouter(this, memory)
        tts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVoice()
            else -> startVoice()
        }
        return START_STICKY
    }

    private fun startVoice() {
        if (active) return
        active = true
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS is listening")
            .setContentText("Say: Jarvis, followed by your command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        createRecognizer()
        scheduleListening(300)
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
    }

    private fun scheduleListening(delay: Long = 700) {
        if (!active || speaking) return
        handler.postDelayed({ startListening() }, delay)
    }

    private fun startListening() {
        if (!active || speaking) return
        val r = recognizer ?: return
        try {
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        } catch (_: Exception) {
            scheduleListening(1200)
        }
    }

    private fun handleCommand(heard: String) {
        val lower = heard.lowercase(Locale.getDefault())
        val marker = lower.indexOf("jarvis")
        if (marker < 0) {
            scheduleListening()
            return
        }
        val command = heard.substring(marker + 6).trim(' ', ',', '.', '!', '?')
        if (command.isBlank()) {
            speak("Yes, I'm listening.")
            return
        }
        val local = router.execute(command)
        if (!local.startsWith("I heard:")) {
            speak(local)
            return
        }
        if (isNiftyCommand(command)) {
            speak("Checking Nifty now.")
            Thread {
                val reply = market.niftyPrice()
                handler.post { speak(reply) }
            }.start()
            return
        }
        Thread {
            val backend = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
                .getString("backend_url", "https://jarvis-ji6k.onrender.com/chat").orEmpty()
            val reply = AiClient(backend).ask(command, memory.all())
            handler.post { speak(reply) }
        }.start()
    }

    private fun isNiftyCommand(command: String): Boolean {
        val c = command.lowercase(Locale.getDefault())
        return c.contains("nifty") || c.contains("nse")
    }

    private fun speak(text: String) {
        if (!active) return
        speaking = true
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_service")
        } else {
            speaking = false
            scheduleListening()
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale.getDefault()
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) { handler.post { speaking = false; scheduleListening(700) } }
                override fun onDone(utteranceId: String?) { handler.post { speaking = false; scheduleListening(500) } }
            })
        }
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
            val heard = results?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            handleCommand(heard)
        }
        override fun onError(error: Int) { scheduleListening(900) }
    }

    private fun stopVoice() {
        active = false
        speaking = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS voice assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
        super.onDestroy()
    }
}
