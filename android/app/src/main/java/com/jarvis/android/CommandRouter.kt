package com.jarvis.android

import android.content.Context
import android.content.Intent
import android.net.Uri

class CommandRouter(private val context: Context) {
    fun execute(command: String): String {
        val c = command.trim().lowercase()
        return when {
            c.contains("open google") || c == "google" -> {
                openUrl("https://www.google.com")
                "Opening Google."
            }
            c.contains("open youtube") || c == "youtube" -> {
                openUrl("https://www.youtube.com")
                "Opening YouTube."
            }
            c.contains("search") -> {
                val query = command.substringAfter("search", "").trim()
                if (query.isNotEmpty()) {
                    openUrl("https://www.google.com/search?q=" + Uri.encode(query))
                    "Searching for $query."
                } else "What should I search for?"
            }
            c.contains("settings") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening settings."
            }
            else -> "I understood: $command. AI connection is the next layer."
        }
    }

    private fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
