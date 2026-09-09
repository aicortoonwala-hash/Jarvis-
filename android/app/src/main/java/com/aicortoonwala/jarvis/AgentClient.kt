package com.aicortoonwala.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class JarvisAction(
    val type: String,
    val target: String = "",
    val text: String = "",
    val value: String = ""
)

data class JarvisPlan(
    val reply: String,
    val actions: List<JarvisAction>
)

class AgentClient(private val endpoint: String) {
    suspend fun plan(prompt: String, memories: Map<String, String> = emptyMap()): JarvisPlan = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext JarvisPlan("AI backend is not configured yet.", emptyList())
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 40000
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
            if (code !in 200..299) return@withContext JarvisPlan("AI server returned HTTP $code.", emptyList())
            val json = JSONObject(body)
            val reply = json.optString("reply").trim().ifBlank { "I couldn't get a useful answer." }
            val actionsJson = json.optJSONArray("actions") ?: JSONArray()
            val actions = buildList {
                for (i in 0 until actionsJson.length()) {
                    val a = actionsJson.optJSONObject(i) ?: continue
                    add(JarvisAction(
                        type = a.optString("type").trim().lowercase(),
                        target = a.optString("target").trim(),
                        text = a.optString("text").trim(),
                        value = a.optString("value").trim()
                    ))
                }
            }
            JarvisPlan(reply, actions)
        } catch (_: Exception) {
            JarvisPlan("AI server unavailable. Check your backend connection.", emptyList())
        } finally {
            connection?.disconnect()
        }
    }
}
