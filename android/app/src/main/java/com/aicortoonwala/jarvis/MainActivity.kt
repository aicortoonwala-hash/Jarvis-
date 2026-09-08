package com.aicortoonwala.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var voice: VoiceAssistant
    private lateinit var router: CommandRouter
    private lateinit var memory: MemoryStore
    private val weather = WeatherClient()
    private lateinit var location: LocationHelper
    private val settings by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onCommand(it) }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startHandsFree()
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onLocationReady() }
    private var status: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceAssistant(this)
        memory = MemoryStore(this)
        location = LocationHelper(this)
        router = CommandRouter(this, memory)
        setContent { JarvisApp() }
        window.decorView.post {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startHandsFree()
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setStatus(value: String) { status?.invoke(value) }

    private fun startHandsFree() {
        voice.startHandsFree(
            command = { onCommand(it) },
            status = { setStatus(it) }
        )
    }

    private fun onCommand(command: String) {
        val local = router.execute(command)
        if (!local.startsWith("I heard:")) {
            setStatus(local); voice.speak(local); return
        }
        if (isWeatherCommand(command)) {
            getWeather()
            return
        }
        setStatus("Thinking...")
        lifecycleScope.launch {
            val reply = AiClient(settings.getString("backend_url", "http://10.0.2.2:8080/chat").orEmpty())
                .ask(command, memory.all())
            setStatus(reply)
            voice.speak(reply)
        }
    }

    private fun isWeatherCommand(command: String): Boolean {
        val c = command.lowercase()
        return c.contains("weather") || c.contains("temperature") || c.contains("mausam") || c.contains("taapman")
    }

    private fun getWeather() {
        if (!hasLocationPermission()) {
            setStatus("Location permission is needed for local weather.")
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        val coords = location.lastLocation()
        if (coords == null) {
            setStatus("I can't get your phone location yet. Turn on Location and try again.")
            voice.speak("I can't get your phone location yet.")
            return
        }
        setStatus("Getting current weather...")
        lifecycleScope.launch {
            val reply = weather.current(coords.first, coords.second)
            setStatus(reply)
            voice.speak(reply)
        }
    }

    private fun onLocationReady() {
        if (hasLocationPermission()) getWeather()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun JarvisApp() {
        var command by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("JARVIS Android online") }
        var backendUrl by remember { mutableStateOf(settings.getString("backend_url", "http://10.0.2.2:8080/chat") ?: "") }
        var handsFree by remember { mutableStateOf(false) }
        status = { message = it }
        DisposableEffect(Unit) { onDispose { status = null } }
        LaunchedEffect(Unit) {
            while (true) {
                handsFree = voice.isHandsFree()
                kotlinx.coroutines.delay(500)
            }
        }
        MaterialTheme {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("JARVIS", color = Color.Cyan, style = MaterialTheme.typography.displaySmall)
                Text("Android edition • Hybrid AI", color = Color.LightGray)
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Ask JARVIS") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (command.isNotBlank()) onCommand(command) }) { Text("EXECUTE") }
                    Button(onClick = {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voice.intent())
                        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("MIC") }
                }
                Button(onClick = {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (voice.isHandsFree()) {
                        voice.stopHandsFree()
                    } else {
                        startHandsFree()
                    }
                }) {
                    Text(if (handsFree) "HANDS-FREE ON" else "HANDS-FREE OFF")
                }
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI backend URL") },
                    singleLine = true
                )
                Button(onClick = {
                    settings.edit().putString("backend_url", backendUrl.trim()).apply()
                    setStatus("Backend URL saved.")
                }) { Text("SAVE BACKEND") }
                Text(message, color = Color.Green)
                Text("Hands-free mode: say 'Jarvis, ...' for a command.", color = Color.Gray)
            }
        }
    }

    override fun onDestroy() { voice.close(); super.onDestroy() }
}
