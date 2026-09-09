package com.aicortoonwala.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var agent: AgentClient
    private lateinit var executor: ActionExecutor
    private lateinit var memory: MemoryStore
    private val weather = WeatherClient()
    private lateinit var location: LocationHelper
    private val settings by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onCommand(it) }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) setStatus("Microphone ready.")
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onLocationReady() }
    private var status: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceAssistant(this)
        memory = MemoryStore(this)
        location = LocationHelper(this)
        router = CommandRouter(this, memory)
        agent = AgentClient(settings.getString("backend_url", "https://jarvis-ji6k.onrender.com/agent").orEmpty())
        executor = ActionExecutor(this, memory)
        setContent { JarvisApp() }
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.any { !hasPermission(it) }) permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        if (settings.getBoolean("hands_free", false)) setStatus("Hands-free is saved. Tap HANDS-FREE to start listening.")
    }

    private fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun setStatus(value: String) { status?.invoke(value) }

    private fun backendAgent(): AgentClient {
        val url = settings.getString("backend_url", "https://jarvis-ji6k.onrender.com/agent").orEmpty()
        return AgentClient(if (url.endsWith("/chat")) url.removeSuffix("/chat") + "/agent" else url)
    }

    private fun startVoiceService() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            setStatus("Microphone permission is required.")
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        settings.edit().putBoolean("hands_free", true).apply()
        try {
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_START))
            setStatus("JARVIS hands-free is ON. Say: Jarvis, ...")
        } catch (_: Exception) {
            setStatus("Hands-free could not start. Allow microphone and set battery usage to Unrestricted.")
        }
    }

    private fun stopVoiceService() {
        settings.edit().putBoolean("hands_free", false).apply()
        startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP))
        setStatus("Hands-free off")
    }

    private fun openBatterySettings() {
        try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        setStatus("Set JARVIS battery usage to Unrestricted, then keep the notification active.")
    }

    private fun onCommand(command: String) {
        val clean = command.trim()
        if (clean.isBlank()) return
        val local = router.execute(clean)
        if (!local.startsWith("I heard:")) { setStatus(local); voice.speak(local); return }
        if (isWeatherCommand(clean)) { getWeather(); return }
        setStatus("JARVIS is thinking...")
        lifecycleScope.launch {
            val plan = backendAgent().plan(clean, memory.all())
            val results = plan.actions.mapNotNull { executor.execute(it) }
            val spoken = if (results.isNotEmpty() && plan.reply.equals("Done.", true)) results.joinToString(" ") else plan.reply
            setStatus(spoken)
            voice.speak(spoken)
        }
    }

    private fun isWeatherCommand(command: String): Boolean {
        val c = command.lowercase()
        return c.contains("weather") || c.contains("temperature") || c.contains("mausam") || c.contains("taapman")
    }

    private fun getWeather() {
        if (!hasLocationPermission()) {
            setStatus("Location permission is needed for local weather.")
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)); return
        }
        val coords = location.lastLocation()
        if (coords == null) { setStatus("Turn on Location and try again."); voice.speak("Turn on Location and try again."); return }
        setStatus("Getting current weather...")
        lifecycleScope.launch { val reply = weather.current(coords.first, coords.second); setStatus(reply); voice.speak(reply) }
    }

    private fun onLocationReady() { if (hasLocationPermission()) getWeather() }
    private fun hasLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    @Composable
    private fun JarvisApp() {
        var command by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("JARVIS Android online • Agent mode") }
        var backendUrl by remember { mutableStateOf(settings.getString("backend_url", "https://jarvis-ji6k.onrender.com/agent") ?: "") }
        var handsFree by remember { mutableStateOf(settings.getBoolean("hands_free", false)) }
        status = { message = it }
        DisposableEffect(Unit) { onDispose { status = null } }
        MaterialTheme {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("JARVIS", color = Color.Cyan, style = MaterialTheme.typography.displaySmall)
                Text("Super Agent • web + voice + phone actions", color = Color.LightGray)
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Ask JARVIS anything") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (command.isNotBlank()) onCommand(command) }) { Text("EXECUTE") }
                    Button(onClick = { if (hasPermission(Manifest.permission.RECORD_AUDIO)) speech.launch(voice.intent()) else permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }) { Text("MIC") }
                }
                Button(onClick = {
                    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    else if (handsFree) { stopVoiceService(); handsFree = false }
                    else { startVoiceService(); handsFree = true }
                }) { Text(if (handsFree) "HANDS-FREE ON" else "HANDS-FREE OFF") }
                Button(onClick = { openBatterySettings() }) { Text("KEEP JARVIS ON IN BACKGROUND") }
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Agent backend URL") }, singleLine = true)
                Button(onClick = { settings.edit().putString("backend_url", backendUrl.trim()).apply(); setStatus("Agent backend saved.") }) { Text("SAVE BACKEND") }
                Text(message, color = Color.Green)
                Text("Voice commands can answer questions, search the live web, open apps, play/search YouTube, maps, camera, flashlight, volume, alarms, timers, settings and memory.", color = Color.Gray)
            }
        }
    }

    override fun onDestroy() { status = null; voice.close(); super.onDestroy() }
}
