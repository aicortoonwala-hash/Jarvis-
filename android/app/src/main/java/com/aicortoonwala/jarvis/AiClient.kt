package com.aicortoonwala.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls a user-controlled backend. Keep Gemini/API secrets on the server, never in the APK. */
class AiClient(private val endpoint: String) {
    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(JSONObject().put("prompt", prompt).toString().toByteArray()) }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("reply", "AI response unavailable")
        } catch (e: Exception) {
            "AI server unavailable. Check your backend connection."
        }
    }
}
