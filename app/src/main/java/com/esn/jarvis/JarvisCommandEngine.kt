package com.esn.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object JarvisCommandEngine {
    fun execute(context: Context, raw: String): String {
        val original = raw.trim()
        val command = original.lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()
        if (command.isBlank()) return "I didn't catch that."

        return when {
            command == "stop" || command == "cancel" || command == "be quiet" || command == "shut up" -> "Standing by."

            // Universal app launching. Known apps get exact package launches; unknown apps are resolved by installed app label.
            command.matches(Regex("(open|launch|start|run) (.+)")) -> {
                val target = command.replaceFirst(Regex("^(open|launch|start|run) "), "").trim()
                if (tryLaunchKnownApp(context, target)) "Opening ${displayName(target)}." else launchByAppLabel(context, target)
            }

            command.startsWith("search youtube") || command.startsWith("find on youtube") -> {
                val q = command.replaceFirst(Regex("^(search youtube( for)?|find on youtube) "), "").trim()
                if (q.isBlank()) "Tell me what to search for on YouTube." else { openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Searching YouTube for $q." }
            }
            command.startsWith("search the web") || command.startsWith("search for ") || command.startsWith("google ") || command.startsWith("look up ") -> {
                val q = command.replaceFirst(Regex("^(search the web( for)?|search for|google|look up) "), "").trim()
                if (q.isBlank()) "Tell me what to search for." else { openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching for $q." }
            }

            // Media controls.
            command.contains("pause") && !command.contains("pause timer") -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, "Media paused.")
            command.contains("resume") || command.contains("play music") || command == "play" -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, "Media playback started.")
            command.contains("next song") || command.contains("next track") || command == "next" -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "Next track.")
            command.contains("previous song") || command.contains("previous track") || command == "previous" -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track.")
            command.contains("volume up") || command.contains("turn the volume up") || command.contains("increase volume") || command.contains("louder") -> changeVolume(context, AudioManager.ADJUST_RAISE, "Volume increased.")
            command.contains("volume down") || command.contains("turn the volume down") || command.contains("decrease volume") || command.contains("quieter") -> changeVolume(context, AudioManager.ADJUST_LOWER, "Volume decreased.")
            command.contains("unmute") -> changeVolume(context, AudioManager.ADJUST_UNMUTE, "Volume unmuted.")
            command.contains("mute") || command.contains("silence volume") -> changeVolume(context, AudioManager.ADJUST_MUTE, "Volume muted.")

            // Device controls and information.
            command.contains("flashlight") || command.contains("flash light") || command.contains("torch") -> toggleFlashlight(context, command.contains("off"))
            command.contains("brightness") -> setBrightness(context, command)
            command.contains("battery") || command.contains("charge") -> batteryStatus(context)
            command.contains("android version") || command.contains("what android") -> "This phone is running Android ${Build.VERSION.RELEASE}."
            command.contains("phone model") || command.contains("what phone") -> "This device is a ${Build.MANUFACTURER} ${Build.MODEL}."
            command.contains("system status") || command.contains("status report") || command == "status" -> systemStatus(context)
            command.contains("run diagnostics") || command.contains("diagnostics") || command.contains("check the system") -> diagnostics(context)
            command.contains("storage") || command.contains("disk space") -> storageStatus()
            command.contains("wifi") && !command.contains("search") -> openSettings(context, Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")
            command.contains("bluetooth") -> openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            command.contains("airplane mode") || command.contains("flight mode") -> openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode settings")
            command.contains("location settings") || command == "location" -> openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location settings")
            command.contains("sound settings") -> openSettings(context, Settings.ACTION_SOUND_SETTINGS, "Sound settings")
            command.contains("display settings") -> openSettings(context, Settings.ACTION_DISPLAY_SETTINGS, "Display settings")
            command.contains("notification settings") -> openSettings(context, Settings.ACTION_NOTIFICATION_SETTINGS, "Notification settings")
            command.contains("app settings") -> openSettings(context, Settings.ACTION_APPLICATION_SETTINGS, "App settings")
            command == "settings" || command.contains("open settings") -> openSettings(context, Settings.ACTION_SETTINGS, "Settings")

            // Navigation / accessibility.
            command == "back" || command.contains("go back") -> if (JarvisAccessibilityService.back()) "Going back." else phoneAccessRequired()
            command == "home" || command.contains("go home") -> if (JarvisAccessibilityService.home()) "Going home." else phoneAccessRequired()
            command.contains("recent apps") || command == "recents" -> if (JarvisAccessibilityService.recents()) "Showing recent apps." else phoneAccessRequired()
            command.startsWith("scroll down") -> if (JarvisAccessibilityService.scrollForward()) "Scrolling down." else phoneAccessRequired()
            command.startsWith("scroll up") -> if (JarvisAccessibilityService.scrollBackward()) "Scrolling up." else phoneAccessRequired()
            command.startsWith("click ") -> { val target = original.substringAfter("click ").trim(); if (JarvisAccessibilityService.clickText(target)) "Clicked $target." else phoneAccessRequired() }
            command.startsWith("tap ") -> { val target = original.substringAfter("tap ").trim(); if (JarvisAccessibilityService.clickText(target)) "Tapped $target." else phoneAccessRequired() }
            command.startsWith("type ") || command.startsWith("enter ") -> { val text = original.replaceFirst(Regex("^(type|enter) "), ""); if (JarvisAccessibilityService.typeText(text)) "Text entered." else phoneAccessRequired() }

            // Time, date, timers, alarms and reminders.
            command.contains("what time") || command == "time" || command.contains("current time") -> "The current time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            command.contains("what date") || command == "date" || command.contains("today's date") || command.contains("what day") -> "Today is ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())}."
            command.contains("set a timer") || command.contains("start a timer") || command.matches(Regex("timer for .+")) -> setTimer(context, command)
            command.contains("remind me") || command.contains("set a reminder") -> setReminder(context, command)
            command.contains("set an alarm") || command.contains("set alarm") || command.contains("alarm for") -> setAlarm(context, command)

            // Communication and useful Android intents.
            command.startsWith("text ") || command.startsWith("send a text") || command.startsWith("send sms") || command.startsWith("sms ") -> handleSms(context, original)
            command.startsWith("call ") || command.startsWith("phone ") || command.startsWith("dial ") -> handleCall(context, original)
            command.contains("email") || command.contains("compose an email") -> openUrl(context, "mailto:") .let { "Opening your email app." }
            command.contains("camera") || command == "take a picture" || command == "take a photo" -> openSystemIntent(context, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera")
            command.contains("clock") || command.contains("alarm app") -> openSystemIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), "clock")
            command.contains("calendar") -> openSystemIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), "calendar")
            command.contains("contacts") -> openSystemIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS), "contacts")
            command.contains("downloads") -> openSystemIntent(context, Intent("").setType("resource/folder"), "downloads")

            // JARVIS modes and voice controls.
            command.contains("gaming mode") -> setMode(context, true)
            command.contains("work mode") -> setMode(context, false)
            command.contains("speak slower") || command.contains("talk slower") -> { changeSpeechRate(context, -0.1f); "Speech speed reduced." }
            command.contains("speak faster") || command.contains("talk faster") -> { changeSpeechRate(context, 0.1f); "Speech speed increased." }
            command.contains("normal speech") || command.contains("normal speed") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putFloat("speech_rate", 0.82f).apply(); "Speech speed restored to normal." }

            // Small built-in utilities.
            command.startsWith("calculate ") || command.startsWith("what is ") && containsMath(command) -> calculate(command)
            command.contains("who are you") || command.contains("what are you") -> "I am JARVIS, your Android control system."
            command.contains("hello") || command.contains("hi jarvis") || command == "hey jarvis" -> "Good to hear from you. Systems are online."
            command.contains("thank you") || command.contains("thanks") -> "You're welcome."

            else -> "I heard you. I don't have a direct handler for that action yet, but I can open installed apps, control media and device settings, search the web, manage timers and alarms, use Phone Access for screen controls, handle calls and texts, and report system information."
        }
    }

    private fun displayName(target: String) = target.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    private fun tryLaunchKnownApp(context: Context, target: String): Boolean {
        val known = mapOf(
            "discord" to "com.discord",
            "youtube" to "com.google.android.youtube",
            "minecraft" to "com.mojang.minecraftpe",
            "spotify" to "com.spotify.music",
            "snapchat" to "com.snapchat.android",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "google chrome" to "com.android.chrome",
            "reddit" to "com.reddit.frontpage",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "x" to "com.twitter.android",
            "twitter" to "com.twitter.android",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs",
            "files" to "com.google.android.documentsui",
            "calculator" to "com.google.android.calculator",
            "play store" to "com.android.vending",
            "settings" to "com.android.settings"
        )
        val pkg = known[target] ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun launchByAppLabel(context: Context, target: String): String {
        val wanted = target.replace(Regex("[^a-z0-9 ]"), "").trim()
        val apps = context.packageManager.getInstalledApplications(0)
        val match = apps.firstOrNull { label(it, context).lowercase(Locale.getDefault()) == wanted }
            ?: apps.firstOrNull { label(it, context).lowercase(Locale.getDefault()).contains(wanted) }
        if (match != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opening ${label(match, context)}."
            }
        }
        return "I couldn't find an installed app called ${displayName(target)}."
    }

    private fun label(info: android.content.pm.ApplicationInfo, context: Context) = context.packageManager.getApplicationLabel(info).toString()

    private fun openSettings(context: Context, action: String, name: String): String = try { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening $name." } catch (_: Exception) { "I couldn't open $name." }

    private fun openSystemIntent(context: Context, intent: Intent, name: String): String = try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening $name." } catch (_: Exception) { "I couldn't open $name." }

    private fun openUrl(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun mediaKey(context: Context, key: Int, response: String): String { try { audio(context).dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)); audio(context).dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key)) } catch (_: Exception) {} ; return response }
    private fun changeVolume(context: Context, direction: Int, response: String): String { audio(context).adjustVolume(direction, AudioManager.FLAG_SHOW_UI); return response }
    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun batteryStatus(context: Context): String {
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return if (level >= 0) "Battery is at $level percent${if (charging) ", and the phone is charging" else ""}." else "I couldn't read the battery level."
    }

    private fun systemStatus(context: Context): String = "${batteryStatus(context)} Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}. JARVIS command systems are online."
    private fun diagnostics(context: Context): String { val speech = if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) "speech recognition available" else "speech recognition unavailable"; return "Diagnostics complete. $speech, native voice enabled, command engine online, and ${batteryStatus(context).lowercase()}." }
    private fun storageStatus(): String { val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path); val free = stat.availableBytes / (1024L * 1024L * 1024L); val total = stat.totalBytes / (1024L * 1024L * 1024L); return "Storage has approximately $free GB free of $total GB." }

    private fun toggleFlashlight(context: Context, forceOff: Boolean): String = try {
        val camera = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone doesn't appear to have a flashlight."
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val current = prefs.getBoolean("flashlight", false); val next = if (forceOff) false else !current
        camera.setTorchMode(id, next); prefs.edit().putBoolean("flashlight", next).apply(); if (next) "Flashlight on." else "Flashlight off."
    } catch (_: Exception) { "I couldn't control the flashlight on this phone." }

    private fun setBrightness(context: Context, command: String): String {
        val match = Regex("(\\d{1,3})\\s*(percent|%)?").find(command.substringAfter("brightness")) ?: return "Tell me a brightness percentage, for example: set brightness to 50 percent."
        val percent = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return "That brightness value isn't valid."
        return try { if (!Settings.System.canWrite(context)) return "Please allow JARVIS to modify system settings before I can change brightness."; Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100); "Brightness set to $percent percent." } catch (_: Exception) { "I couldn't change the brightness." }
    }

    private fun setTimer(context: Context, command: String): String {
        val m = Regex("(\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(command) ?: return "Tell me how long, for example: set a timer for 10 minutes."
        val amount = m.groupValues[1].toLong(); val unit = m.groupValues[2]
        val delay = when { unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(amount); unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(amount); else -> TimeUnit.HOURS.toMillis(amount) }
        scheduleNotification(context, delay, "Timer finished. $amount $unit have passed."); return "Timer set for $amount $unit."
    }

    private fun setReminder(context: Context, command: String): String {
        val m = Regex("in (\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(command) ?: return "Tell me when, for example: remind me to check Discord in 20 minutes."
        val amount = m.groupValues[1].toLong(); val unit = m.groupValues[2]; val delay = when { unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(amount); unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(amount); else -> TimeUnit.HOURS.toMillis(amount) }
        val text = command.substringAfter("remind me", "check your reminder").substringBefore(" in $amount").trim().removePrefix("to ").ifBlank { "check your reminder" }
        scheduleNotification(context, delay, "Reminder: $text"); return "Reminder set for $amount $unit from now."
    }

    private fun setAlarm(context: Context, command: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(command.substringAfter("alarm", "")) ?: return "Tell me an alarm time, for example: set an alarm for 7:30 AM."
        var hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].ifBlank { "0" }.toInt(); val ap = m.groupValues[3]
        if (ap.equals("pm", true) && hour < 12) hour += 12; if (ap.equals("am", true) && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return "That isn't a valid alarm time."
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1) }
        return try { val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "JARVIS alarm"); context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening the alarm setup for ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)}." } catch (_: Exception) { "I couldn't open the alarm app." }
    }

    private fun scheduleNotification(context: Context, delayMillis: Long, message: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JarvisReminderReceiver::class.java).putExtra(JarvisReminderReceiver.EXTRA_MESSAGE, message)
        val requestCode = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis.coerceAtLeast(1000L), pending)
    }

    private fun handleSms(context: Context, raw: String): String {
        val lower = raw.lowercase(Locale.getDefault()); val prefix = when { lower.startsWith("send a text message to ") -> "send a text message to "; lower.startsWith("send a text to ") -> "send a text to "; lower.startsWith("send sms to ") -> "send sms to "; lower.startsWith("text ") -> "text "; else -> "sms " }
        val split = splitSmsRecipientAndMessage(raw.substring(prefix.length).trim()) ?: return "Tell me who to text and what to say, for example: text 5551234567 saying I'll be home soon."
        val (recipient, message) = split
        return try { context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:${Uri.encode(recipient)}"); putExtra("sms_body", message); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "I opened your messaging app with the text ready for $recipient. Review it and tap Send." } catch (_: Exception) { "I couldn't open the messaging app." }
    }

    private fun splitSmsRecipientAndMessage(s: String): Pair<String, String>? { for (sep in listOf(" saying ", " that says ", ": ", " message ")) { val i = s.lowercase(Locale.getDefault()).indexOf(sep); if (i > 0) { val a = s.substring(0, i).trim(); val b = s.substring(i + sep.length).trim(); if (a.isNotBlank() && b.isNotBlank()) return a to b } }; return null }

    private fun handleCall(context: Context, raw: String): String {
        val target = raw.replaceFirst(Regex("^(call|phone|dial) ", RegexOption.IGNORE_CASE), "").trim()
        if (target.isBlank()) return "Tell me who you want to call."
        return try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(target)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening the dialer for $target. Review the number and place the call." } catch (_: Exception) { "I couldn't open the dialer." }
    }

    private fun setMode(context: Context, gaming: Boolean): String { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", gaming).apply(); return if (gaming) "Gaming Mode engaged. Performance commands are standing by." else "Work Mode engaged. Ready when you are." }
    private fun changeSpeechRate(context: Context, delta: Float) { val p = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE); p.edit().putFloat("speech_rate", (p.getFloat("speech_rate", 0.82f) + delta).coerceIn(0.5f, 1.5f)).apply() }
    private fun phoneAccessRequired() = "Phone Access isn't enabled, so I can't control the screen yet."
    private fun containsMath(command: String) = command.matches(Regex(".*\\d+\\s*[+\\-*/x×]\\s*\\d+.*"))
    private fun calculate(command: String): String { val expr = command.replaceFirst(Regex("^(calculate|what is) "), "").replace("×", "*").trim(); val m = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x])\\s*(\\d+(?:\\.\\d+)?)").find(expr) ?: return "I can calculate simple expressions such as 12 times 4."; val a=m.groupValues[1].toDouble(); val op=m.groupValues[2]; val b=m.groupValues[3].toDouble(); if (op != "/" && op != "x" && op != "*") return "The answer is ${if (op == "+") a+b else a-b}."; if ((op == "/" || op == "x" || op == "*") && b == 0.0 && op == "/") return "Division by zero isn't valid."; val r=if(op=="/") a/b else a*b; return "The answer is $r." }
}
