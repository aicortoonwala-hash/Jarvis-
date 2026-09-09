package com.aicortoonwala.jarvis

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        memory = MemoryStore(this)
        router = CommandRouter(this, memory)
        tts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopVoice() else startVoice()
        return START_STICKY
    }

    private fun startVoice() {
        if (active) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return }
        active = true
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit().putBoolean("hands_free", true).apply()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS is listening")
            .setContentText("Hands-free active • Say: Jarvis, followed by anything")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { active = false; stopSelf(); return }
        createRecognizer()
        scheduleListening(300)
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
    }

    private fun scheduleListening(delay: Long = 700) {
        if (!active || speaking) return
        handler.postDelayed({ startListening() }, delay)
    }

    private fun startListening() {
        if (!active || speaking) return
        val r = recognizer ?: run { createRecognizer(); recognizer } ?: return
        try {
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        } catch (_: Exception) { scheduleListening(1200) }
    }

    private fun handleCommand(heard: String) {
        val lower = heard.lowercase(Locale.getDefault())
        val marker = lower.indexOf("jarvis")
        if (marker < 0) { scheduleListening(); return }
        val command = heard.substring(marker + 6).trim(' ', ',', '.', '!', '?')
        if (command.isBlank()) { speak("Yes, I'm listening."); return }
        val local = router.execute(command)
        if (!local.startsWith("I heard:")) { speak(local); return }
        serviceScope.launch {
            val backend = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
                .getString("backend_url", "https://jarvis-ji6k.onrender.com/chat").orEmpty()
            val reply = AiClient(backend).ask(command, memory.all())
            withContext(Dispatchers.Main) { speak(reply) }
        }
    }

    private fun speak(text: String) {
        if (!active) return
        speaking = true
        if (ttsReady) {
            val polished = text.replace(Regex("\\s+"), " ").trim()
            tts.speak(polished, TextToSpeech.QUEUE_FLUSH, null, "jarvis_service")
        } else { speaking = false; scheduleListening() }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        try {
            tts.language = Locale.US
            val voices = tts.voices.orEmpty()
                .filter { it.locale.language == "en" && it.locale.country.equals("US", true) }
                .filterNot { isFemaleVoice(it) }
            val preferredMale = voices.map { it to maleVoiceScore(it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
            if (preferredMale != null) tts.voice = preferredMale
            tts.setSpeechRate(0.82f)
            tts.setPitch(0.58f)
        } catch (_: Exception) {
            try { tts.language = Locale.US; tts.setSpeechRate(0.82f); tts.setPitch(0.58f) } catch (_: Exception) { }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) { handler.post { speaking = false; scheduleListening(700) } }
            override fun onDone(utteranceId: String?) { handler.post { speaking = false; scheduleListening(500) } }
        })
    }

    private fun isFemaleVoice(voice: Voice): Boolean {
        val n = voice.name.lowercase(Locale.US)
        return n.contains("female") || n.contains("woman") || n.contains("f1") || n.contains("f2")
    }

    private fun maleVoiceScore(voice: Voice): Int {
        val n = voice.name.lowercase(Locale.US)
        var score = 0
        if (n.contains("male")) score += 100
        if (n.contains("man")) score += 80
        if (n.contains("m1")) score += 70
        if (n.contains("m2")) score += 60
        if (n.contains("m3")) score += 50
        if (n.contains("x-sfg")) score += 20
        if (n.contains("local")) score += 5
        if (voice.quality >= Voice.QUALITY_NORMAL) score += 3
        return score
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
            handleCommand(heard)
        }
        override fun onError(error: Int) { scheduleListening(900) }
    }

    private fun stopVoice() {
        active = false; speaking = false
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit().putBoolean("hands_free", false).apply()
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "JARVIS voice assistant", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keep = getSharedPreferences("jarvis_settings", MODE_PRIVATE).getBoolean("hands_free", false)
        if (keep) {
            try { ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java).setAction(ACTION_START)) } catch (_: Exception) { }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false; handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        serviceScope.cancel(); tts.shutdown(); super.onDestroy()
    }
}
