package com.esn.jarvis

import android.content.Context
import java.util.Locale

object JarvisBrain {
    private const val PREFS = "jarvis_brain"

    fun respond(context: Context, raw: String): String {
        val text = raw.trim()
        val lower = text.lowercase(Locale.US)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("last_user", "").orEmpty()

        val response = when {
            lower in setOf("who are you", "what are you") ->
                "I'm JARVIS, your personal assistant. I can control supported phone functions and keep track of our recent conversation."
            lower.contains("what did i just say") || lower.contains("what was my last question") ->
                if (previous.isBlank()) "We haven't established any conversation context yet." else "You said, $previous."
            lower.startsWith("remember that ") -> {
                val note = text.substringAfter("remember that ").trim()
                prefs.edit().putString("note", note).apply()
                "I'll keep that in our local conversation context."
            }
            lower == "what do you remember" || lower == "what did i ask you to remember" -> {
                val note = prefs.getString("note", "").orEmpty()
                if (note.isBlank()) "You haven't asked me to remember anything yet." else "You asked me to remember: $note."
            }
            lower.startsWith("thanks") || lower == "thank you" -> "You're welcome."
            lower.contains("how are you") -> "All systems are operational."
            else -> "I understand the request, but my online AI brain is not connected yet. Your phone commands still work normally."
        }
        prefs.edit().putString("last_user", text).putString("last_response", response).apply()
        return response
    }
}
