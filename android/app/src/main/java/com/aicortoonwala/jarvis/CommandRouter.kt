package com.aicortoonwala.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

class CommandRouter(private val context: Context) {
    fun execute(command: String): String {
        val c = command.trim().lowercase()
        return when {
            c == "google" || c.contains("open google") -> { open("https://www.google.com"); "Opening Google." }
            c == "youtube" || c.contains("open youtube") -> { open("https://www.youtube.com"); "Opening YouTube." }
            c.startsWith("search ") -> {
                val q = command.substringAfter("search ").trim()
                open("https://www.google.com/search?q=" + Uri.encode(q)); "Searching for $q."
            }
            c.contains("settings") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening settings."
            }
            else -> "Command received. AI brain connection is next."
        }
    }
    private fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
