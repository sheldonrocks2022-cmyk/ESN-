package com.esn.jarvis

import android.content.Context
import java.util.Locale

object JarvisPersonality {
    private const val PREFS = "jarvis_personality"
    private const val KEY_SIR = "address_as_sir"
    private const val KEY_ACK = "ack_index"

    fun addressAsSir(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SIR, true)

    fun setSir(context: Context, enabled: Boolean): String {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIR, enabled).apply()
        return if (enabled) "Of course, sir." else "Understood. I will stop addressing you as sir."
    }

    fun status(context: Context): String =
        if (addressAsSir(context)) "Personality mode is active. I will address you as sir and keep acknowledgements concise."
        else "Personality mode is active. Sir acknowledgements are disabled."

    fun voiceLine(context: Context, raw: String): String {
        val text = raw.trim()
        if (text.isBlank() || !addressAsSir(context) || shouldStayLiteral(text)) return text
        if (Regex("(?i)\\b(sir)\\b").containsMatchIn(text)) return text
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getInt(KEY_ACK, 0)
        val acknowledgements = listOf("Yes, sir.", "Right away, sir.", "Of course, sir.", "At your service, sir.")
        prefs.edit().putInt(KEY_ACK, (index + 1) % acknowledgements.size).apply()
        return acknowledgements[index % acknowledgements.size] + " " + text
    }

    private fun shouldStayLiteral(text: String): Boolean {
        val n = text.lowercase(Locale.US)
        return n.startsWith("notification from ") ||
            n.startsWith("that will ") ||
            n.startsWith("please say ") ||
            n.startsWith("say yes ") ||
            n.startsWith("i couldn't") ||
            n.startsWith("i can't") ||
            n.startsWith("phone access") ||
            n.startsWith("notification access") ||
            n.startsWith("warning") ||
            n.startsWith("error") ||
            n.contains(" to confirm") ||
            n.contains("permission") ||
            n.contains("failed") ||
            n.contains("stopped safely")
    }
}
