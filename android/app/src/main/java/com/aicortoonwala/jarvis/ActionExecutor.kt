package com.aicortoonwala.jarvis

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale

class ActionExecutor(private val context: Context, private val memory: MemoryStore) {
    fun execute(action: JarvisAction): String? = try {
        when (action.type) {
            "open_url" -> { open(action.target); "Opening it." }
            "search_web" -> { open("https://www.google.com/search?q=" + Uri.encode(action.target)); "Searching the web for ${action.target}." }
            "search_youtube", "play_youtube" -> { open("https://www.youtube.com/results?search_query=" + Uri.encode(action.target)); "Opening YouTube for ${action.target}." }
            "open_app" -> openApp(action.target)
            "maps" -> { open(Uri.parse("geo:0,0?q=" + Uri.encode(action.target))); "Opening maps for ${action.target}." }
            "camera" -> { context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening the camera." }
            "settings" -> openSettings(action.target)
            "flashlight" -> flashlight(action.value.equals("on", true))
            "volume" -> volume(action.value)
            "alarm" -> alarm(action.target, action.value)
            "timer" -> timer(action.value, action.target)
            "call" -> call(action.target)
            "sms" -> sms(action.target, action.text)
            "remember" -> { if (action.target.isNotBlank() && action.text.isNotBlank()) memory.remember(action.target, action.text); "I'll remember that." }
            else -> null
        }
    } catch (_: Exception) { "I couldn't complete that phone action." }

    private fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun open(uri: Uri) = context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun openApp(name: String): String {
        val n = name.lowercase(Locale.US).trim()
        val pkg = when {
            n.contains("youtube") -> "com.google.android.youtube"
            n.contains("whatsapp") -> "com.whatsapp"
            n.contains("chrome") -> "com.android.chrome"
            n.contains("maps") -> "com.google.android.apps.maps"
            n.contains("gmail") -> "com.google.android.gm"
            n.contains("phone") || n.contains("dialer") -> "com.google.android.dialer"
            n.contains("camera") -> return execute(JarvisAction("camera")) ?: "Opening camera."
            else -> null
        }
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else open("https://www.google.com/search?q=" + Uri.encode(name))
        return "Opening $name."
    }

    private fun openSettings(target: String): String {
        val action = when {
            target.contains("wifi") -> Settings.ACTION_WIFI_SETTINGS
            target.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            target.contains("sound") || target.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
            target.contains("display") -> Settings.ACTION_DISPLAY_SETTINGS
            target.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            target.contains("app") -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Opening settings."
    }

    private fun flashlight(on: Boolean): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "This phone has no controllable flashlight."
        manager.setTorchMode(id, on); return if (on) "Flashlight on." else "Flashlight off."
    }

    private fun volume(value: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (value.lowercase(Locale.US)) {
            "up", "increase" -> audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
            "down", "decrease" -> audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)
            "mute", "silent" -> audio.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            "unmute" -> audio.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
            else -> return "Tell me volume up, down, mute, or unmute."
        }
        return "Done."
    }

    private fun alarm(time: String, label: String): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return "I need an alarm time like 7:30 PM."
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, if (hour > 12) hour else hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent); return "Setting the alarm for $time."
    }

    private fun timer(seconds: String, message: String): String {
        val s = seconds.toLongOrNull() ?: return "I need a timer duration in seconds."
        context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, s.coerceIn(1, 86400L).toInt())
            putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "JARVIS timer" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Starting a ${s}-second timer."
    }

    private fun call(target: String): String {
        if (target.isBlank()) return "Who should I call?"
        val number = findContact(target) ?: target
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); return "Calling $target."
    }

    private fun sms(target: String, text: String): String {
        if (target.isBlank() || text.isBlank()) return "I need the contact and message."
        val number = findContact(target) ?: target
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply { putExtra("sms_body", text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return "Opening the message composer for $target."
        }
        SmsManager.getDefault().sendTextMessage(number, null, text, null, null)
        return "Message sent to $target."
    }

    private fun findContact(name: String): String? = try {
        val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        context.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, args, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }
}
