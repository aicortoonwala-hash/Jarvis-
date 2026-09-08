package com.aicortoonwala.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat

class CommandRouter(private val context: Context, private val memory: MemoryStore) {
    fun execute(command: String): String {
        val original = command.trim()
        val c = original.lowercase()
        return when {
            c == "google" || c.contains("open google") -> { open("https://www.google.com"); "Opening Google." }
            c == "youtube" || c.contains("open youtube") -> { open("https://www.youtube.com"); "Opening YouTube." }
            c.contains("camera") || c.contains("take photo") || c.contains("take a photo") -> openCamera()
            c.contains("maps") || c.contains("google maps") || c.startsWith("navigate to ") || c.startsWith("directions to ") -> openMaps(original)
            c.startsWith("call ") || c.startsWith("dial ") -> callContactOrNumber(original.substringAfter(" ").trim())
            c.startsWith("sms ") || c.startsWith("text ") || c.startsWith("send sms ") -> composeSms(original)
            c.startsWith("whatsapp ") || c.contains("open whatsapp") -> openWhatsApp()
            c.startsWith("search ") || c.startsWith("google search ") -> {
                val q = if (c.startsWith("google search ")) original.substring(14).trim() else original.substring(7).trim()
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

    private fun openCamera(): String = try {
        context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening camera."
    } catch (_: Exception) { "Camera app is not available." }

    private fun openMaps(original: String): String {
        val query = original.substringAfter("to ", "").trim()
        val uri = if (query.isBlank()) Uri.parse("geo:0,0?q=Google+Maps") else Uri.parse("geo:0,0?q=" + Uri.encode(query))
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening maps${if (query.isNotBlank()) " for $query" else ""}."
        } catch (_: Exception) { open("https://www.google.com/maps"); "Opening maps." }
    }

    private fun callContactOrNumber(nameOrNumber: String): String {
        if (nameOrNumber.isBlank()) return "Tell me who to call."
        val number = findContactNumber(nameOrNumber)
        val target = number ?: nameOrNumber
        return try {
            if (number != null && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(target))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Calling $nameOrNumber."
            } else {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(target))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening the dialer for $nameOrNumber."
            }
        } catch (_: Exception) { "I couldn't start the call." }
    }

    private fun findContactNumber(name: String): String? {
        return try {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$name%")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun composeSms(original: String): String {
        val text = original.substringAfter(" ").trim()
        if (text.isBlank()) return "Tell me the phone number and message."
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Opening SMS composer."
    }

    private fun openWhatsApp(): String = try {
        context.startActivity(context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening WhatsApp."
    } catch (_: Exception) { "WhatsApp is not available." }

    private fun rememberCommand(text: String): String {
        val parts = text.split(" is ", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return "Tell me what to remember, for example: remember name is Alex."
        memory.remember(parts[0], parts[1])
        return "Got it. I will remember ${parts[0].trim()}."
    }

    private fun recallCommand(original: String): String {
        val key = original.lowercase().removePrefix("what is my ").removePrefix("recall ").trim()
        if (key.isBlank() || key == "what do you remember") {
            val all = memory.all()
            return if (all.isEmpty()) "I don't have any saved memories yet." else "I remember: " + all.entries.joinToString(", ") { "${it.key} is ${it.value}" } + "."
        }
        return memory.recall(key)?.let { "Your $key is $it." } ?: "I don't have a memory for $key."
    }

    private fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
