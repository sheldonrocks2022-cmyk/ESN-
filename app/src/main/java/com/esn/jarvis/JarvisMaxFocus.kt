package com.esn.jarvis

import android.content.Context

/**
 * Local focus sessions using elapsed realtime to avoid wall-clock changes.
 * Focus does not block apps or change system settings.
 */
class JarvisMaxFocus(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_max_focus", Context.MODE_PRIVATE)
    private val clock get() = android.os.SystemClock.elapsedRealtime()

    @Synchronized fun start(minutes: Int): String {
        if (minutes !in 1..180) return "Focus session must be 1 to 180 minutes."
        prefs.edit().putLong("deadline", clock + minutes * 60_000L).apply()
        return "Focus session started for $minutes minutes."
    }

    @Synchronized fun status(): String {
        val deadline = prefs.getLong("deadline", 0)
        if (deadline <= 0L) return "No active focus session."
        val remaining = deadline - clock
        if (remaining <= 0L) {
            prefs.edit().remove("deadline").apply()
            return "Focus session complete."
        }
        val minutes = (remaining + 59_999L) / 60_000L
        return "Focus session active. About $minutes minutes remaining."
    }

    @Synchronized fun stop(): String {
        prefs.edit().remove("deadline").apply()
        return "Focus session stopped."
    }
}
