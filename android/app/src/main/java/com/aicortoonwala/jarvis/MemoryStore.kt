package com.aicortoonwala.jarvis

import android.content.Context

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    fun remember(key: String, value: String) {
        prefs.edit().putString(key.trim().lowercase(), value.trim()).apply()
    }

    fun recall(key: String): String? = prefs.getString(key.trim().lowercase(), null)

    fun all(): Map<String, String> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let { k to it }
    }.toMap()
}
