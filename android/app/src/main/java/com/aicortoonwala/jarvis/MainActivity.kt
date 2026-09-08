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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var voice: VoiceAssistant
    private lateinit var router: CommandRouter
    private val ai = AiClient("http://10.0.2.2:8080/chat")
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onCommand(it) }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var status: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceAssistant(this)
        router = CommandRouter(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent { JarvisApp() }
    }

    private fun setStatus(value: String) { status?.invoke(value) }

    private fun onCommand(command: String) {
        val local = router.execute(command)
        if (!local.startsWith("I heard:")) {
            setStatus(local); voice.speak(local); return
        }
        setStatus("Thinking...")
        kotlinx.coroutines.MainScope().launch {
            val reply = ai.ask(command)
            setStatus(reply)
            voice.speak(reply)
        }
    }

    @Composable
    private fun JarvisApp() {
        var command by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("JARVIS Android online") }
        status = { message = it }
        DisposableEffect(Unit) { onDispose { status = null } }
        MaterialTheme {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("JARVIS", color = Color.Cyan, style = MaterialTheme.typography.displaySmall)
                Text("Android edition • AI online", color = Color.LightGray)
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Ask JARVIS") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { if (command.isNotBlank()) onCommand(command) }) { Text("EXECUTE") }
                    Button(onClick = {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.launch(voice.intent())
                        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("MIC") }
                }
                Text(message, color = Color.Green)
                Text("AI backend URL can be changed in MainActivity.kt. Keep Gemini keys on the server.", color = Color.Gray)
            }
        }
    }

    override fun onDestroy() { voice.close(); super.onDestroy() }
}
