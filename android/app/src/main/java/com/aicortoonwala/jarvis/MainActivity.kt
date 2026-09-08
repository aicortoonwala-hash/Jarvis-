package com.aicortoonwala.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true || hasPermission(Manifest.permission.RECORD_AUDIO)) startVoiceService()
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

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.any { !hasPermission(it) }) permissionLauncher.launch(permissions.toTypedArray())
        else startVoiceService()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun setStatus(value: String) { status?.invoke(value) }

    private fun startVoiceService() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return
        val intent = Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        setStatus("JARVIS hands-free is ON. Say: Jarvis, ...")
    }

    private fun stopVoiceService() {
        startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP))
        setStatus("Hands-free off")
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
            val reply = AiClient(settings.getString("backend_url", "https://jarvis-ji6k.onrender.com/chat").orEmpty())
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

    private fun onLocationReady() { if (hasLocationPermission()) getWeather() }

    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    @Composable
    private fun JarvisApp() {
        var command by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("JARVIS Android online") }
        var backendUrl by remember { mutableStateOf(settings.getString("backend_url", "https://jarvis-ji6k.onrender.com/chat") ?: "") }
        var handsFree by remember { mutableStateOf(true) }
        status = { message = it }
        DisposableEffect(Unit) { onDispose { status = null } }
        MaterialTheme {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("JARVIS", color = Color.Cyan, style = MaterialTheme.typography.displaySmall)
                Text("Android edition • Hybrid AI", color = Color.LightGray)
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Ask JARVIS") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (command.isNotBlank()) onCommand(command) }) { Text("EXECUTE") }
                    Button(onClick = {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) speech.launch(voice.intent())
                        else permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }) { Text("MIC") }
                }
                Button(onClick = {
                    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    } else if (handsFree) {
                        stopVoiceService()
                        handsFree = false
                    } else {
                        startVoiceService()
                        handsFree = true
                    }
                }) { Text(if (handsFree) "HANDS-FREE ON" else "HANDS-FREE OFF") }
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
                Text("Screen-off: keep the JARVIS notification active and set Battery usage to Unrestricted.", color = Color.Gray)
            }
        }
    }

    override fun onDestroy() { voice.close(); super.onDestroy() }
}
