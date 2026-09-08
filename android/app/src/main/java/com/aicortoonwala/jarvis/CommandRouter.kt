package com.aicortoonwala.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

class CommandRouter(private val context: Context) {
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
            c.contains("hello") || c.contains("hi jarvis") || c == "hi" -> "Hello. JARVIS is online and ready."
            else -> "I heard: $original. Connect Gemini backend for full AI answers."
        }
    }

    private fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
