package com.esn.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object JarvisCommandEngine {
    fun execute(context: Context, raw: String): String {
        val command = raw.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
        if (command.isBlank()) return "I didn't catch that."

        return when {
            command.contains("code 101") -> "Code 101 is handled by the voice service."

            command.matches(Regex("(open|launch|start) discord")) || command.contains("open discord") -> launchApp(context, "Discord", "com.discord")
            command.matches(Regex("(open|launch|start) youtube")) || command.contains("open youtube") -> launchApp(context, "YouTube", "com.google.android.youtube")
            command.matches(Regex("(open|launch|start) minecraft")) || command.contains("open minecraft") -> launchApp(context, "Minecraft", "com.mojang.minecraftpe")
            command.matches(Regex("(open|launch|start) spotify")) || command.contains("open spotify") -> launchApp(context, "Spotify", "com.spotify.music")
            command.matches(Regex("(open|launch|start) snapchat")) || command.contains("open snapchat") -> launchApp(context, "Snapchat", "com.snapchat.android")
            command.matches(Regex("(open|launch|start) gmail")) || command.contains("open gmail") -> launchApp(context, "Gmail", "com.google.android.gm")

            command.startsWith("search youtube for ") || command.startsWith("search youtube ") -> {
                val q = command.substringAfter("search youtube").removePrefix(" for ").trim()
                if (q.isBlank()) "Tell me what you want me to search for on YouTube." else { openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Searching YouTube for $q." }
            }
            command.startsWith("search the web for ") || command.startsWith("google ") || command.startsWith("search for ") -> {
                val q = when {
                    command.startsWith("search the web for ") -> command.substringAfter("search the web for ")
                    command.startsWith("search for ") -> command.substringAfter("search for ")
                    else -> command.substringAfter("google ")
                }.trim()
                if (q.isBlank()) "Tell me what you want me to search for." else { openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching the web for $q." }
            }

            isSmsCommand(command) -> handleSms(context, raw)

            command.contains("volume up") || command.contains("turn the volume up") || command.contains("increase volume") -> { audio(context).adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Volume increased." }
            command.contains("volume down") || command.contains("turn the volume down") || command.contains("decrease volume") -> { audio(context).adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Volume decreased." }
            command.contains("mute") || command.contains("silence volume") -> { audio(context).adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); "Volume muted." }
            command.contains("unmute") -> { audio(context).adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); "Volume unmuted." }

            command.contains("flashlight") || command.contains("flash light") || command.contains("torch") -> toggleFlashlight(context, command.contains("off"))
            command.startsWith("brightness") || command.contains("brightness to ") -> setBrightness(context, command)

            command.contains("battery") || command.contains("battery level") || command.contains("how much battery") -> batteryStatus(context)
            command.contains("android version") -> "This phone is running Android ${android.os.Build.VERSION.RELEASE}."
            command.contains("system status") || command.contains("status report") -> systemStatus(context)
            command.contains("run diagnostics") || command.contains("diagnostics") -> diagnostics(context)

            command.contains("wifi settings") || command.contains("wi-fi settings") || command == "wifi" || command == "wi fi" -> openSettings(context, Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")
            command.contains("bluetooth settings") || command == "bluetooth" -> openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            command.contains("airplane mode") -> openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode settings")
            command.contains("open settings") || command == "settings" -> openSettings(context, Settings.ACTION_SETTINGS, "Settings")

            command.contains("go back") || command == "back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access isn't enabled, so I can't control the screen yet."
            command.contains("go home") || command == "home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access isn't enabled, so I can't control the screen yet."
            command.contains("show recent apps") || command == "recents" || command == "recent apps" -> if (JarvisAccessibilityService.recents()) "Showing recent apps." else "Phone Access isn't enabled, so I can't control the screen yet."
            command == "scroll down" || command == "scroll down please" -> if (JarvisAccessibilityService.scrollForward()) "Scrolling down." else "I can't control scrolling without Phone Access."
            command == "scroll up" || command == "scroll up please" -> if (JarvisAccessibilityService.scrollBackward()) "Scrolling up." else "I can't control scrolling without Phone Access."
            command.startsWith("click ") -> { val target = raw.substringAfter("click ").trim(); if (JarvisAccessibilityService.clickText(target)) "Clicked $target." else "I can't click that without Phone Access enabled." }
            command.startsWith("type ") -> { val text = raw.substringAfter("type ").trim(); if (JarvisAccessibilityService.typeText(text)) "Text entered." else "I can't type into the screen without Phone Access enabled." }

            command.contains("set a timer") || command.contains("start a timer") || command.matches(Regex("timer for .+")) -> setTimer(context, command)
            command.contains("remind me") || command.contains("set a reminder") -> setReminder(context, command)

            command.contains("what time") || command == "time" || command.contains("current time") -> {
                val now = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
                "The current time is $now."
            }
            command.contains("what date") || command == "date" || command.contains("today's date") -> {
                val today = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
                "Today is $today."
            }

            command.contains("gaming mode") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", true).apply(); "Gaming Mode engaged. Performance commands are standing by." }
            command.contains("work mode") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", false).apply(); "Work Mode engaged. Ready when you are." }
            command.contains("speak slower") || command.contains("talk slower") -> { changeSpeechRate(context, -0.1f); "Speech speed reduced." }
            command.contains("speak faster") || command.contains("talk faster") -> { changeSpeechRate(context, 0.1f); "Speech speed increased." }
            command.contains("normal speech") || command.contains("normal speed") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putFloat("speech_rate", 0.82f).apply(); "Speech speed restored to normal." }

            else -> "I heard you, but I don't have that command yet. Try open Discord, open YouTube, open Minecraft, search the web for something, volume up, flashlight on, set a timer, or system status."
        }
    }

    private fun launchApp(context: Context, name: String, packageName: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening $name."
        } else {
            "I can't find $name installed on this phone."
        }
    }

    private fun openSettings(context: Context, action: String, name: String): String {
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening $name."
        } catch (_: Exception) { "I couldn't open $name." }
    }

    private fun batteryStatus(context: Context): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return if (level >= 0) "Battery is at $level percent${if (charging) ", and the phone is charging" else ""}." else "I couldn't read the battery level."
    }

    private fun systemStatus(context: Context): String {
        val battery = batteryStatus(context)
        val memory = Runtime.getRuntime().let { "${(it.totalMemory() - it.freeMemory()) / (1024 * 1024)} MB memory in use" }
        return "$battery Android ${android.os.Build.VERSION.RELEASE}. $memory. JARVIS command systems are online."
    }

    private fun diagnostics(context: Context): String {
        val speech = if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) "speech recognition available" else "speech recognition unavailable"
        return "Diagnostics complete. $speech, native voice enabled, command engine online, and ${batteryStatus(context).lowercase()}."
    }

    private fun toggleFlashlight(context: Context, forceOff: Boolean): String {
        return try {
            val camera = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return "This phone doesn't appear to have a flashlight."
            val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("flashlight", false)
            val next = if (forceOff) false else !current
            camera.setTorchMode(id, next)
            prefs.edit().putBoolean("flashlight", next).apply()
            if (next) "Flashlight on." else "Flashlight off."
        } catch (_: Exception) { "I couldn't control the flashlight on this phone." }
    }

    private fun setBrightness(context: Context, command: String): String {
        val match = Regex("(\\d{1,3})\\s*(percent|%)?").find(command.substringAfter("brightness"))
            ?: return "Tell me a brightness percentage, for example: set brightness to 50 percent."
        val percent = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return "That brightness value isn't valid."
        return try {
            if (!Settings.System.canWrite(context)) return "Please allow JARVIS to modify system settings before I can change brightness."
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255) / 100)
            "Brightness set to $percent percent."
        } catch (_: Exception) { "I couldn't change the brightness." }
    }

    private fun setTimer(context: Context, command: String): String {
        val match = Regex("(\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(command)
            ?: return "Tell me how long, for example: set a timer for 10 minutes."
        val amount = match.groupValues[1].toLongOrNull() ?: return "I couldn't understand that timer."
        val unit = match.groupValues[2]
        val millis = when {
            unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(amount)
            unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(amount)
            else -> TimeUnit.HOURS.toMillis(amount)
        }
        scheduleNotification(context, millis, "Timer finished. $amount $unit have passed.")
        return "Timer set for $amount $unit."
    }

    private fun setReminder(context: Context, command: String): String {
        val match = Regex("in (\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(command)
            ?: return "Tell me when, for example: remind me to check Discord in 20 minutes."
        val amount = match.groupValues[1].toLongOrNull() ?: return "I couldn't understand the reminder time."
        val unit = match.groupValues[2]
        val millis = when {
            unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(amount)
            unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(amount)
            else -> TimeUnit.HOURS.toMillis(amount)
        }
        val reminderText = command.substringAfter("remind me", "check your reminder").substringBefore(" in $amount").trim().removePrefix("to ").ifBlank { "check your reminder" }
        scheduleNotification(context, millis, "Reminder: $reminderText")
        return "Reminder set for $amount $unit from now."
    }

    private fun scheduleNotification(context: Context, delayMillis: Long, message: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JarvisReminderReceiver::class.java).putExtra(JarvisReminderReceiver.EXTRA_MESSAGE, message)
        val requestCode = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis.coerceAtLeast(1000L), pending)
    }

    private fun changeSpeechRate(context: Context, delta: Float) {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val current = prefs.getFloat("speech_rate", 0.82f)
        prefs.edit().putFloat("speech_rate", (current + delta).coerceIn(0.5f, 1.5f)).apply()
    }

    private fun isSmsCommand(command: String): Boolean = command.startsWith("text ") || command.startsWith("send a text to ") || command.startsWith("send a text message to ") || command.startsWith("send sms to ") || command.startsWith("sms ")

    private fun handleSms(context: Context, raw: String): String {
        val normalized = raw.trim()
        val lower = normalized.lowercase(Locale.getDefault())
        val prefix = when {
            lower.startsWith("send a text message to ") -> "send a text message to "
            lower.startsWith("send a text to ") -> "send a text to "
            lower.startsWith("send sms to ") -> "send sms to "
            lower.startsWith("text ") -> "text "
            else -> "sms "
        }
        val split = splitSmsRecipientAndMessage(normalized.substring(prefix.length).trim())
            ?: return "Tell me who to text and what to say, for example: text 5551234567 saying I'll be home soon."
        val (recipient, message) = split
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:${Uri.encode(recipient)}"); putExtra("sms_body", message); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return try { context.startActivity(intent); "I opened your messaging app with the text ready for $recipient. Review it and tap Send." } catch (_: Exception) { "I couldn't open the messaging app on this phone." }
    }

    private fun splitSmsRecipientAndMessage(remainder: String): Pair<String, String>? {
        for (separator in listOf(" saying ", " that says ", ": ", " message ")) {
            val index = remainder.lowercase(Locale.getDefault()).indexOf(separator)
            if (index > 0) {
                val recipient = remainder.substring(0, index).trim()
                val message = remainder.substring(index + separator.length).trim()
                if (recipient.isNotBlank() && message.isNotBlank()) return recipient to message
            }
        }
        return null
    }

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun openUrl(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
