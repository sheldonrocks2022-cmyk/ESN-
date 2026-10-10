package com.esn.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local, bounded conversation history. Not an AI inference engine. */
class JarvisMaxConversation(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_max_conversation", Context.MODE_PRIVATE)
    private val key = "turns"
    @Synchronized fun append(role: String, text: String): Boolean {
        if (role !in setOf("user", "assistant") || text.isBlank() || text.length > 2000) return false
        val previous = read()
        val array = JSONArray()
        previous.takeLast(39).forEach { array.put(it) }
        array.put(JSONObject().put("role", role).put("text", text.trim()).put("at", System.currentTimeMillis()))
        return prefs.edit().putString(key, array.toString()).commit()
    }
    @Synchronized fun read(): List<JSONObject> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    } catch (_: Exception) { emptyList() }
    @Synchronized fun summary(): String {
        val items = read()
        if (items.isEmpty()) return "No saved conversation turns."
        return items.takeLast(6).joinToString(". ") { "${it.optString("role")}: ${it.optString("text").take(180)}" }.take(1300)
    }
    @Synchronized fun clear(): Boolean = prefs.edit().remove(key).commit()
}
