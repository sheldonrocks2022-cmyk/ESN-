package com.esn.jarvis

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils

class JarvisNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val PREFS = "jarvis_notifications"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 25
        const val ACTION_INCOMING_MESSAGE = "com.esn.jarvis.INCOMING_MESSAGE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        fun readLatestMessages(serviceContext: android.content.Context): String {
            val raw = serviceContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY_ITEMS, "").orEmpty()
            if (raw.isBlank()) return "I don't have any recent message notifications to read."
            val items = raw.split("\n---\n").filter { it.isNotBlank() }.takeLast(5).reversed()
            return if (items.isEmpty()) "I don't have any recent message notifications to read." else "Here are your most recent messages. " + items.joinToString(" ")
        }

        fun readLatestMessage(serviceContext: android.content.Context): String {
            val raw = serviceContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY_ITEMS, "").orEmpty()
            val latest = raw.split("\n---\n").filter { it.isNotBlank() }.lastOrNull()
            return latest?.let { "The latest message is: $it" } ?: "I don't have a recent message notification."
        }

        fun notificationAccessSettings(serviceContext: android.content.Context): String = try {
            serviceContext.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening notification access settings. Enable JARVIS so I can read your message notifications."
        } catch (_: Exception) { "I couldn't open notification access settings." }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank() || TextUtils.isEmpty(title)) return

        val lower = "$title $text".lowercase()
        if (listOf("download", "update available", "charging", "battery", "silent notifications").any { lower.contains(it) }) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = prefs.getString(KEY_ITEMS, "").orEmpty().split("\n---\n").filter { it.isNotBlank() }.toMutableList()
        val entry = "$title says: $text"
        existing.remove(entry)
        existing.add(entry)
        while (existing.size > MAX_ITEMS) existing.removeAt(0)
        prefs.edit().putString(KEY_ITEMS, existing.joinToString("\n---\n")).apply()

        sendBroadcast(Intent(ACTION_INCOMING_MESSAGE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
        })
    }
}
