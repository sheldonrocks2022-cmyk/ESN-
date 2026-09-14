package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

object JarvisNaturalCommandRouter {
    fun execute(context: Context, raw: String): String? {
        val text = raw.lowercase(Locale.US).replace(Regex("[^a-z0-9% ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return "I didn't catch that."
        return when {
            text.contains("what am i looking at") || text.contains("what is on my screen") || text.contains("read this screen") || text.contains("describe my screen") -> JarvisScreenInspector.describeScreen()
            text == "open notifications" || text == "show notifications" || text == "pull down notifications" -> if (JarvisAccessibilityService.notifications()) "Opening notifications." else "Phone Access is not enabled."
            text == "open quick settings" || text == "show quick settings" -> if (JarvisAccessibilityService.quickSettings()) "Opening quick settings." else "Phone Access is not enabled."
            text.contains("read my messages") || text.contains("read my texts") || text.contains("read my sms") || text.contains("read my dms") -> JarvisNotificationListenerService.readLatestMessages(context)
            text.contains("any new messages") || text.contains("what did i miss") || text.contains("any new texts") || text.contains("unread messages") -> JarvisNotificationListenerService.readLatestMessages(context)
            text.contains("read my notifications") || text.contains("show me my notifications") -> JarvisNotificationListenerService.readLatestMessages(context)
            text.contains("notification access") || text.contains("let you read my notifications") || text.contains("enable notification access") -> JarvisNotificationListenerService.notificationAccessSettings(context)
            text == "go to settings" || text == "take me to settings" -> open(context, Settings.ACTION_SETTINGS, "Settings opened.")
            text == "go home" || text == "take me home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access is not enabled."
            text == "go back" || text == "take me back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access is not enabled."
            text.contains("slow down") || text.contains("speak slower") -> setSpeechRate(context, -0.08f, "Speech speed reduced.")
            text.contains("speak faster") || text.contains("speed up") -> setSpeechRate(context, 0.08f, "Speech speed increased.")
            text.contains("normal speech") || text.contains("normal speed") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putFloat("speech_rate", 0.74f).apply(); "Speech speed restored." }
            text == "good morning" || text.contains("morning briefing") -> morningBriefing(context)
            text.contains("good night") || text.contains("bedtime mode") || text.contains("going to bed") -> bedtime(context)
            else -> null
        }
    }

    private fun setSpeechRate(context: Context, delta: Float, response: String): String {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val current = prefs.getFloat("speech_rate", 0.74f)
        prefs.edit().putFloat("speech_rate", (current + delta).coerceIn(0.60f, 1.05f)).apply()
        return response
    }

    private fun morningBriefing(context: Context): String = "Good morning. ${JarvisNotificationListenerService.readLatestMessages(context)} ${JarvisCommandEngine.execute(context, "battery status") }"

    private fun bedtime(context: Context): String {
        val opened = try { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        return if (opened) "Bedtime mode ready. Sound settings opened so you can confirm your preferred sleep settings." else "I couldn't open the sound settings."
    }

    private fun open(context: Context, action: String, response: String): String = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); response
    } catch (_: Exception) { "I couldn't open that setting." }
}
