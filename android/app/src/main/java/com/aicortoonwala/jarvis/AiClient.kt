package com.aicortoonwala.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls a user-controlled backend. Keep Gemini/API secrets on the server, never in the APK. */
class AiClient(private val endpoint: String) {
    suspend fun ask(prompt: String, memories: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext "AI backend is not configured yet."
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject().apply {
                put("prompt", prompt)
                put("memories", JSONObject(memories))
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return@withContext "AI server returned HTTP $code."
            JSONObject(body).optString("reply").ifBlank { "AI response unavailable." }
        } catch (_: Exception) {
            "AI server unavailable. Check your backend connection."
        } finally {
            connection?.disconnect()
        }
    }
}
