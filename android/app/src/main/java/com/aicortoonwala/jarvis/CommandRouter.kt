package com.aicortoonwala.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

class CommandRouter(private val context: Context, private val memory: MemoryStore) {
    fun execute(command: String): String {
        val original = command.trim()
        val c = original.lowercase()
        return when {
            c == "google" || c.contains("open google") -> { open("https://www.google.com"); "Opening Google." }
            c == "youtube" || c.contains("open youtube") -> { open("https://www.youtube.com"); "Opening YouTube." }
            c.startsWith("search ") || c.startsWith("google search ") -> {
                val q = original.substringAfter("search ", original.substringAfter("google search ", "")).trim()
                if (q.isBlank()) "What should I search for?" else { open("https://www.google.com/search?q=" + Uri.encode(q)); "Searching for $q." }
            }
            c.contains("settings") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening settings."
            }
            c.contains("time") -> "The current time is ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())}."
            c.startsWith("remember ") -> rememberCommand(original.removePrefix("remember ").trim())
            c.contains("what is my ") || c.startsWith("recall ") || c.startsWith("what do you remember") -> recallCommand(original)
            c.contains("hello") || c.contains("hi jarvis") || c == "hi" -> "Hello. JARVIS is online and ready."
            else -> "I heard: $original. Connect Gemini backend for full AI answers."
        }
    }

    private fun rememberCommand(text: String): String {
        val parts = text.split(" is ", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return "Tell me what to remember, for example: remember name is Alex."
        memory.remember(parts[0], parts[1])
        return "Got it. I will remember ${parts[0].trim()}."
    }

    private fun recallCommand(original: String): String {
        val key = original.lowercase()
            .removePrefix("what is my ")
            .removePrefix("recall ")
            .trim()
        if (key.isBlank() || key == "what do you remember") {
            val all = memory.all()
            return if (all.isEmpty()) "I don't have any saved memories yet." else "I remember: " + all.entries.joinToString(", ") { "${it.key} is ${it.value}" } + "."
        }
        return memory.recall(key)?.let { "Your $key is $it." } ?: "I don't have a memory for $key."
    }

    private fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
