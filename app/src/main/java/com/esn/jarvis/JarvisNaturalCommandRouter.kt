package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

object JarvisNaturalCommandRouter {
    fun execute(context: Context, raw: String): String? {
        val text = normalize(raw)
        if (text.isBlank()) return "I didn't catch that."

        // Run natural multi-step requests such as "open YouTube and then turn up the volume".
        val parts = text.split(Regex("\\s+(?:and then|then|and)\\s+"))
            .map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size in 2..4) {
            val responses = mutableListOf<String>()
            for (part in parts) {
                val response = executeSingle(context, part) ?: JarvisCommandEngine.execute(context, part)
                if (response.isNotBlank()) responses += response
            }
            return responses.joinToString(" ")
        }
        return executeSingle(context, text)
    }

    private fun executeSingle(context: Context, text: String): String? = when {
        text == "help" || text == "what can you do" || text == "what can i say" -> "I can open apps, send messages, control supported phone functions, read notifications, inspect your screen, run routines, and chain commands together."
        text == "are you there" || text == "you there" || text == "hello" || text == "hey" -> "At your service."
        text.contains("what am i looking at") || text.contains("what is on my screen") || text.contains("read this screen") || text.contains("describe my screen") -> JarvisScreenInspector.describeScreen()
        text == "open notifications" || text == "show notifications" || text == "pull down notifications" -> if (JarvisAccessibilityService.notifications()) "Opening notifications." else "Phone Access is not enabled."
        text == "open quick settings" || text == "show quick settings" -> if (JarvisAccessibilityService.quickSettings()) "Opening quick settings." else "Phone Access is not enabled."
        text.contains("read my messages") || text.contains("read my texts") || text.contains("read my sms") || text.contains("read my dms") -> JarvisNotificationListenerService.readLatestMessages(context)
        text == "read it" || text == "read that" || text == "read the latest message" -> JarvisNotificationListenerService.readLatestMessage(context)
        text.contains("any new messages") || text.contains("what did i miss") || text.contains("any new texts") || text.contains("unread messages") -> JarvisNotificationListenerService.readLatestMessages(context)
        text.contains("read my notifications") || text.contains("show me my notifications") -> JarvisNotificationListenerService.readLatestMessages(context)
        text.contains("notification access") || text.contains("let you read my notifications") || text.contains("enable notification access") -> JarvisNotificationListenerService.notificationAccessSettings(context)
        text.startsWith("reply ") || text.startsWith("reply that ") -> prepareReply(text)
        text == "go to settings" || text == "take me to settings" -> open(context, Settings.ACTION_SETTINGS, "Settings opened.")
        text == "go home" || text == "take me home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access is not enabled."
        text == "go back" || text == "take me back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access is not enabled."
        text.contains("speak slower") || text.contains("talk slower") -> setSpeechRate(context, -0.08f, "Speech speed reduced.")
        text.contains("speak faster") || text.contains("talk faster") -> setSpeechRate(context, 0.08f, "Speech speed increased.")
        text.contains("normal speech") || text.contains("normal speed") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putFloat("speech_rate", 0.74f).apply(); "Speech speed restored." }
        text == "good morning" || text.contains("morning briefing") -> morningBriefing(context)
        text.contains("good night") || text.contains("bedtime mode") || text.contains("going to bed") -> bedtime(context)
        text.contains("gaming mode") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", true).apply(); "Gaming mode enabled." }
        text.contains("work mode") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", false).apply(); "Work mode enabled." }
        else -> null
    }

    private fun normalize(raw:String)=raw.lowercase(Locale.US).replace(Regex("[^a-z0-9%' ]")," ").replace(Regex("\\s+")," ").trim()
    private fun prepareReply(raw: String): String {
        val reply = raw.replaceFirst(Regex("(?i)^reply( that)?\\s*"), "").trim()
        if (reply.isBlank()) return "Tell me what you want me to reply."
        if (!JarvisAccessibilityService.clickText("Reply") && !JarvisAccessibilityService.clickText("reply")) return "I couldn't find a Reply control. Phone Access may need to be enabled."
        return if (JarvisAccessibilityService.typeText(reply)) "Reply drafted. Review it before sending." else "I opened Reply, but I couldn't enter the text."
    }
    private fun setSpeechRate(context: Context, delta: Float, response: String): String {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        prefs.edit().putFloat("speech_rate", (prefs.getFloat("speech_rate", 0.74f) + delta).coerceIn(0.60f, 1.05f)).apply()
        return response
    }
    private fun morningBriefing(context: Context): String = "Good morning. ${JarvisNotificationListenerService.readLatestMessages(context)} ${JarvisCommandEngine.execute(context, "battery status")}"
    private fun bedtime(context: Context): String = try { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Bedtime mode ready. Sound settings opened for your confirmation." } catch (_: Exception) { "I couldn't open the sound settings." }
    private fun open(context: Context, action: String, response: String): String = try { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); response } catch (_: Exception) { "I couldn't open that setting." }
}
