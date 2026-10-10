package com.esn.jarvis

import android.content.Context
import org.json.JSONArray

/**
 * Local, app-private JARVIS MAX memory store.
 * This is a foundation for explicit, user-controlled memory, not an AI backend.
 * Never store passwords, API keys, tokens, or sensitive personal information.
 */
class JarvisMaxMemory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("jarvis_max_memory_v1", Context.MODE_PRIVATE)
    private val key = "items"
    private val maxItems = 100
    private val maxChars = 500

    @Synchronized
    fun list(): List<String> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun remember(note: String): Boolean {
        val clean = note.trim()
        if (clean.isEmpty() || clean.length > maxChars) return false
        val updated = (list() + clean).takeLast(maxItems)
        val array = JSONArray()
        updated.forEach(array::put)
        return preferences.edit().putString(key, array.toString()).commit()
    }

    @Synchronized
    fun forget(index: Int): Boolean {
        val items = list().toMutableList()
        if (index !in items.indices) return false
        items.removeAt(index)
        val array = JSONArray()
        items.forEach(array::put)
        return preferences.edit().putString(key, array.toString()).commit()
    }

    @Synchronized
    fun forgetAll(): Boolean = preferences.edit().remove(key).commit()
}
