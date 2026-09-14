package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class JarvisCommandEngine(private val context: Context) {
    fun execute(command: String): String {
        val raw = command.trim()
        val c = raw.lowercase()

        if (c == "code 101" || c.contains("code one oh one") || c.contains("code 101")) {
            return "Emergency shutdown code accepted. JARVIS is going offline."
        }

        // SMS / text messaging. Uses the system SMS composer so the user can review and send.
        val smsPrefixes = listOf(
            "text ", "send a text to ", "send text to ", "send sms to ", "sms "
        )
        for (prefix in smsPrefixes) {
            if (c.startsWith(prefix)) {
                val payload = raw.substring(prefix.length).trim()
                val parsed = parseSms(payload)
                if (parsed != null) {
                    val (recipient, message) = parsed
                    return openSmsComposer(recipient, message)
                }
            }
        }

        if (c == "call" || c.startsWith("call ")) {
            val number = raw.removePrefix("call").trim()
            if (number.isNotBlank()) {
                return try {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Opening the phone for $number."
                } catch (_: Exception) {
                    "I couldn't open the phone app."
                }
            }
        }

        if (c == "open contacts" || c == "open my contacts" || c == "show contacts") {
            return launchPackage("com.google.android.contacts", "Contacts")
        }
        if (c == "open messages" || c == "open my messages") {
            return launchPackage("com.google.android.apps.messaging", "Messages")
        }
        if (c == "open snapchat") return launchPackage("com.snapchat.android", "Snapchat")
        if (c == "open discord") return launchPackage("com.discord", "Discord")
        if (c == "open youtube") return launchPackage("com.google.android.youtube", "YouTube")
        if (c == "open google") return openUrl("https://www.google.com", "Google")
        if (c == "open gmail") return launchPackage("com.google.android.gm", "Gmail")
        if (c == "open spotify") return launchPackage("com.spotify.music", "Spotify")
        if (c == "search google") return openUrl("https://www.google.com", "Google")

        if (c.startsWith("search for ")) return openUrl("https://www.google.com/search?q=" + Uri.encode(raw.substring(11).trim()), "Google")
        if (c.startsWith("search the web for ")) return openUrl("https://www.google.com/search?q=" + Uri.encode(raw.substring(19).trim()), "Google")
        if (c.startsWith("search youtube for ")) return openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(raw.substring(19).trim()), "YouTube")
        if (c.startsWith("search reddit for ")) return openUrl("https://www.reddit.com/search/?q=" + Uri.encode(raw.substring(18).trim()), "Reddit")

        if (c == "turn on flashlight" || c == "turn the flashlight on" || c == "flashlight on") return "I can't directly control the flashlight without Phone Access on this device."
        if (c == "turn off flashlight" || c == "turn the flashlight off" || c == "flashlight off") return "I can't directly control the flashlight without Phone Access on this device."
        if (c.startsWith("set brightness to ")) return "I can't directly change brightness without Phone Access on this device."
        if (c == "turn on wifi" || c == "turn off wifi" || c == "turn on bluetooth" || c == "turn off bluetooth" || c == "turn on airplane mode" || c == "turn off airplane mode") return "Android requires system controls for that setting on this device. I can open Settings for you."
        if (c == "open settings" || c == "open phone settings") return openSettings()
        if (c == "go back" || c == "back") return "I can't press Back without Phone Access enabled."
        if (c == "go home" || c == "home") return "I can't press Home without Phone Access enabled."
        if (c == "open recents" || c == "show recent apps") return "I can't open Recents without Phone Access enabled."
        if (c == "scroll up" || c == "scroll down") return "I can't control scrolling without Phone Access."
        if (c.startsWith("click ")) return "I can't click that without Phone Access enabled."
        if (c.startsWith("type ")) return "I can't type into the screen without Phone Access enabled."
        if (c == "volume up" || c == "turn the volume up") return "Volume control is available through the system controls on this device."
        if (c == "volume down" || c == "turn the volume down") return "Volume control is available through the system controls on this device."
        if (c == "mute my phone" || c == "mute the phone") return "I can't directly change your ringer mode without Phone Access on this device."
        if (c == "lock my phone") return "I can't lock the screen without Phone Access enabled."
        if (c == "take a screenshot" || c == "take screenshot") return "I can't capture a screenshot without Phone Access enabled."

        if (c == "what time is it" || c == "what's the time" || c == "what is the time") {
            val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            return "It is $time."
        }
        if (c == "what's today's date" || c == "what is today's date" || c == "what is the date" || c == "today's date") {
            val date = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            return "Today is $date."
        }

        if (c.startsWith("set a timer for ")) return "Timer support is ready to be connected to Android's Clock app."
        if (c.startsWith("set an alarm for ")) return "Alarm support is ready to be connected to Android's Clock app."
        if (c.startsWith("remind me in ")) return "Reminder support is ready to be connected to Android notifications."

        if (c == "play music" || c == "pause music" || c == "skip this song") return launchPackage("com.spotify.music", "Spotify")
        if (c == "jarvis help" || c == "help" || c == "what can you do") return "I can send texts, open apps, call numbers, search the web, tell the time and date, and handle supported phone commands. Say 'JARVIS, help' for more."
        if (c == "go to sleep" || c == "sleep" || c == "stop listening") return "JARVIS voice mode can be stopped from the notification controls."
        if (c == "wake up" || c == "jarvis wake up") return "JARVIS is already awake."
        if (c == "repeat that") return "I don't have the previous response stored yet."
        if (c == "cancel") return "Cancelled."

        return "I can hear you, but I don't have an action for that yet. Try saying: text a number, call a number, open Discord, open Snapchat, search Google, what time is it, or help."
    }

    private fun parseSms(payload: String): Pair<String, String>? {
        val patterns = listOf(
            Regex("^(.+?)\\s+(?:saying|that says|and say)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s*:\\s*(.+)$")
        )
        for (pattern in patterns) {
            val match = pattern.find(payload) ?: continue
            val recipient = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            if (recipient.isNotBlank() && message.isNotBlank()) return recipient to message
        }
        return null
    }

    private fun openSmsComposer(recipient: String, message: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(recipient)}")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "I opened a text to $recipient. Review it and tap Send."
        } catch (_: Exception) {
            "I couldn't open the messaging app for that text."
        }
    }

    private fun launchPackage(packageName: String, appName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "$appName isn't installed on this phone."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $appName."
        } catch (_: Exception) {
            "I couldn't open $appName."
        }
    }

    private fun openUrl(url: String, name: String): String {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening $name."
        } catch (_: Exception) {
            "I couldn't open $name."
        }
    }

    private fun openSettings(): String {
        return try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening Settings."
        } catch (_: Exception) {
            "I couldn't open Settings."
        }
    }
}
