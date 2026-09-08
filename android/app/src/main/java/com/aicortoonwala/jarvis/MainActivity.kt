package com.aicortoonwala.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent { JarvisApp() }
    }
}

@Composable
private fun JarvisApp() {
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("JARVIS Android online") }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("JARVIS", color = Color.Cyan, style = MaterialTheme.typography.displaySmall)
            Text("Android edition • Phase 1", color = Color.LightGray)

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Command") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    status = if (command.isBlank()) "Say or type a command" else "Command received: $command"
                }) { Text("EXECUTE") }
                Button(onClick = { status = "Voice module ready — LiveKit integration next" }) {
                    Text("MIC")
                }
            }

            Text(status, color = Color.Green)
            Text(
                "Next: connect the existing Gemini/LiveKit agent through a mobile-safe backend, then add memory, weather, search and other tools.",
                color = Color.Gray
            )
        }
    }
}
