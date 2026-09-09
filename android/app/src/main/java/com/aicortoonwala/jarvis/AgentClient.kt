package com.aicortoonwala.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

        val cleanEndpoint = endpoint.trim().removeSuffix("/")
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("memories", JSONObject(memories))
        }.toString()

        var lastError = "AI server unavailable. Check your backend connection."

        repeat(2) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(cleanEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Connection", "close")
                }

                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    val detail = try {
                        JSONObject(body).opt("detail")?.toString().orEmpty()
                    } catch (_: Exception) { "" }
                    lastError = if (detail.isNotBlank()) {
                        "AI server returned HTTP $code: $detail"
                    } else {
                        "AI server returned HTTP $code."
                    }
                    if (attempt == 0) {
                        delay(1200)
                        return@repeat
                    }
                    return@withContext JarvisPlan(lastError.take(500), emptyList())
                }

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
                return@withContext JarvisPlan(reply, actions)
            } catch (e: Exception) {
                lastError = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "JARVIS backend timed out. Render may be waking up; please try again."
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "JARVIS cannot reach the backend. Check internet connection or backend URL."
                    else -> "JARVIS connection error: ${e.message?.take(180).orEmpty()}".trimEnd()
                }
                if (attempt == 0) delay(1200)
            } finally {
                connection?.disconnect()
            }
        }

        JarvisPlan(lastError.take(500), emptyList())
    }
}
