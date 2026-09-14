package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

/**
 * Natural-language front door for JARVIS. It translates conversational requests
 * into the existing command engine without requiring rigid command wording.
 */
object JarvisNaturalCommandRouter {
    fun execute(context: Context, raw: String): String? {
        val text = raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9% ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isBlank()) return "I didn't catch that."

        return when {
            isMessageReadRequest(text) -> JarvisNotificationListenerService.readLatestMessages(context)
            isNotificationAccessRequest(text) -> JarvisNotificationListenerService.notificationAccessSettings(context)
            isUnreadRequest(text) -> JarvisNotificationListenerService.readLatestMessages(context)
            text == "what did i miss" || text == "anything new" || text == "any new messages" -> JarvisNotificationListenerService.readLatestMessages(context)
            text.contains("open notification access") || text.contains("notification access settings") -> JarvisNotificationListenerService.notificationAccessSettings(context)
            text == "go to settings" || text == "take me to settings" -> open(context, Settings.ACTION_SETTINGS, "Settings opened.")
            text == "go home" || text == "take me home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access is not enabled."
            text == "go back" || text == "take me back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access is not enabled."
            text.contains("show me my notifications") || text.contains("read my notifications") -> JarvisNotificationListenerService.readLatestMessages(context)
            else -> null
        }
    }

    private fun isMessageReadRequest(text: String): Boolean {
        val read = text.contains("read") || text.contains("tell me") || text.contains("show me") || text.contains("what")
        val message = text.contains("message") || text.contains("messages") || text.contains("text") || text.contains("texts") || text.contains("sms") || text.contains("dm") || text.contains("dms")
        return read && message && (text.contains("my") || text.contains("recent") || text.contains("latest") || text.contains("new") || text.contains("unread"))
    }

    private fun isUnreadRequest(text: String): Boolean =
        (text.contains("unread") || text.contains("new messages") || text.contains("new texts")) &&
            (text.contains("message") || text.contains("text") || text.contains("sms") || text.contains("dm"))

    private fun isNotificationAccessRequest(text: String): Boolean =
        text.contains("give you access to my notifications") ||
            text.contains("let you read my notifications") ||
            text.contains("enable notification access")

    private fun open(context: Context, action: String, response: String): String = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        response
    } catch (_: Exception) {
        "I couldn't open that setting."
    }
}
