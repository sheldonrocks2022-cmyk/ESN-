package com.example.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisCommandEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun execute(command: String): String {
        val result = executeInternal(command)
        if (command.trim().lowercase(Locale.getDefault()) != "repeat that") {
            prefs.edit().putString("last_response", result).putString("last_command", command.trim()).apply()
        }
        return result
    }

    private fun executeInternal(command: String): String {
        val raw = command.trim()
        val c = raw.lowercase(Locale.getDefault())
        if (c.isBlank()) return "Yes, sir?"
        if (c == "hello" || c == "hi" || c == "hey") return "Good to hear from you, sir. JARVIS is standing by."
        if (c == "good morning") return "Good morning, sir. JARVIS is online and ready."
        if (c == "good afternoon") return "Good afternoon, sir. How may I assist you?"
        if (c == "good evening") return "Good evening, sir. All systems are standing by."
        if (c == "good night") return "Good night, sir. I'll remain ready when you return."
        if (c == "thank you" || c == "thanks" || c == "thanks jarvis") return "You're most welcome, sir."
        if (c == "are you there" || c == "are you online") return "Always, sir. JARVIS is online and listening."
        if (c == "what are you doing") return "Monitoring the command system and waiting for your next instruction, sir."
        if (c == "who are you") return "I am JARVIS, your personal Android voice assistant. I manage supported phone commands, information, routines, and system controls."
        if (c == "tell me a joke" || c == "tell me something funny") return "I would tell you a UDP joke, sir, but you might not get it."
        if (c == "code 101" || c.contains("code one oh one")) return "Emergency shutdown code accepted. JARVIS is going offline."

        val smsPrefixes = listOf("text ", "send a text to ", "send text to ", "send a text message to ", "send a text message ", "send sms to ", "sms ")
        for (prefix in smsPrefixes) if (c.startsWith(prefix)) parseSms(raw.substring(prefix.length).trim())?.let { return openSmsComposer(it.first, it.second) }

        if (c == "call" || c.startsWith("call ")) {
            val number = raw.substringAfter("call", "").trim()
            if (number.isNotBlank()) return openDialer(number)
        }
        if (c == "open contacts" || c == "open my contacts" || c == "show contacts") return openContacts()
        if (c == "open messages" || c == "open my messages") return launchMessaging()

        if (c == "open discord") return openAppByName("Discord")
        if (c == "open snapchat") return openAppByName("Snapchat")
        if (c == "open youtube") return openAppByName("YouTube")
        if (c == "open gmail") return openAppByName("Gmail")
        if (c == "open spotify" || c == "open music") return openAppByName("Spotify")
        if (c == "open minecraft") return openAppByName("Minecraft")
        if (c.startsWith("open ")) {
            val appName = raw.substring(5).trim()
            if (appName.isNotBlank()) return openAppByName(appName)
        }

        if (c == "open google") return openUrl("https://www.google.com", "Google")
        if (c == "search google") return openUrl("https://www.google.com", "Google")
        if (c.startsWith("search for ")) return searchGoogle(raw.substring(11).trim())
        if (c.startsWith("search the web for ")) return searchGoogle(raw.substring(19).trim())
        if (c.startsWith("search google for ")) return searchGoogle(raw.substring(19).trim())
        if (c.startsWith("search youtube for ")) return openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(raw.substring(19).trim()), "YouTube")
        if (c.startsWith("search reddit for ")) return openUrl("https://www.reddit.com/search/?q=" + Uri.encode(raw.substring(18).trim()), "Reddit")
        if (c == "search again" || c == "repeat the search") return searchGoogle(prefs.getString("last_search", "").orEmpty())
        if (c.startsWith("now search for ")) return searchGoogle(raw.substring(15).trim())

        if (c == "turn on flashlight" || c == "turn the flashlight on" || c == "flashlight on") return setFlashlight(true)
        if (c == "turn off flashlight" || c == "turn the flashlight off" || c == "flashlight off") return setFlashlight(false)
        if (c.startsWith("set brightness to ")) return setBrightness(raw.substring(18).trim())
        if (c == "turn on wifi" || c == "turn off wifi" || c == "turn on wi fi" || c == "turn off wi fi") return openSettingsPanel(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
        if (c == "turn on bluetooth" || c == "turn off bluetooth") return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
        if (c == "turn on airplane mode" || c == "turn off airplane mode") return openSettingsPanel(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode")
        if (c == "volume up" || c == "turn the volume up" || c == "increase volume") return adjustVolume(AudioManager.ADJUST_RAISE)
        if (c == "volume down" || c == "turn the volume down" || c == "decrease volume") return adjustVolume(AudioManager.ADJUST_LOWER)
        if (c == "mute my phone" || c == "mute the phone" || c == "mute") return setMute()

        if (c == "go back" || c == "back") return "Back control requires Phone Access to be enabled."
        if (c == "go home" || c == "home") return "Home control requires Phone Access to be enabled."
        if (c == "open recents" || c == "show recent apps") return "Recent-app control requires Phone Access to be enabled."
        if (c == "scroll up" || c == "scroll down") return "Scrolling requires Phone Access to be enabled."
        if (c.startsWith("click ")) return "Screen clicking requires Phone Access to be enabled."
        if (c.startsWith("type ")) return "Screen typing requires Phone Access to be enabled."
        if (c == "lock my phone") return "Screen locking requires Phone Access or a supported device-admin role."
        if (c == "take a screenshot" || c == "take screenshot") return "Screenshot capture requires an Android screen-capture consent flow."

        if (c == "what time is it" || c == "what's the time" || c == "what is the time") return "It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        if (c == "what's today's date" || c == "what is today's date" || c == "what is the date" || c == "today's date") return "Today is " + SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()) + "."
        if (c == "what's my battery" || c == "what is my battery" || c == "battery status" || c == "how much battery do i have") return batteryStatus()
        if (c == "am i charging" || c == "is my phone charging") return chargingStatus()
        if (c == "what android version am i on" || c == "android version") return "This phone is running Android ${android.os.Build.VERSION.RELEASE}."
        if (c == "how much storage do i have" || c == "storage status") return storageStatus()
        if (c == "is wifi on" || c == "wifi status") return networkStatus()
        if (c == "is bluetooth on" || c == "bluetooth status") return "Bluetooth status is available in Android Settings."

        if (c.startsWith("set a timer for ")) return setTimer(raw.substring(16).trim())
        if (c.startsWith("set an alarm for ")) return setAlarm(raw.substring(17).trim())
        if (c.startsWith("remind me in ")) return setReminder(raw.substring(13).trim())
        if (c.startsWith("remind me to ")) return parseReminderText(raw.substring(13).trim())

        if (c == "play music" || c == "play") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PLAY, "Play")
        if (c == "pause music" || c == "pause") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause")
        if (c == "skip this song" || c == "skip song" || c == "next song") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "Skip")
        if (c == "previous song" || c == "go back a song") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous")

        if (c == "speak slower") return changeSpeechRate(-0.08f)
        if (c == "speak faster") return changeSpeechRate(0.08f)
        if (c == "normal speaking speed" || c == "reset speaking speed") return resetSpeechRate()

        if (c == "run diagnostics" || c == "run a diagnostic" || c == "diagnostics" || c == "system diagnostics") return diagnostics()
        if (c == "system status" || c == "jarvis status" || c == "status") return systemStatus()

        if (c == "start gaming mode" || c == "gaming mode" || c == "activate gaming mode") return gamingMode()
        if (c == "start work mode" || c == "work mode" || c == "activate work mode") return workMode()

        if (c == "jarvis help" || c == "help" || c == "what can you do") return help()
        if (c == "go to sleep" || c == "sleep" || c == "stop listening") return "JARVIS voice mode can be stopped from the notification controls."
        if (c == "wake up" || c == "jarvis wake up") return "JARVIS is already awake."
        if (c == "repeat that" || c == "repeat") return prefs.getString("last_response", "I don't have a previous response stored yet.") ?: "I don't have a previous response stored yet."
        if (c == "cancel" || c == "cancel that") return "Very well. Cancelled."
        return "I can hear you, but I don't have an action for that yet. Try help, status, diagnostics, open an app, search the web, set a timer, set an alarm, or ask for the time."
    }

    private fun parseSms(payload: String): Pair<String, String>? {
        val patterns = listOf(Regex("^(.+?)\\s+(?:saying|that says|and say)\\s+(.+)$", RegexOption.IGNORE_CASE), Regex("^(.+?)\\s*:\\s*(.+)$"))
        for (pattern in patterns) pattern.find(payload)?.let { match ->
            val recipient = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            if (recipient.isNotBlank() && message.isNotBlank()) return recipient to message
        }
        return null
    }

    private fun openSmsComposer(recipient: String, message: String): String = try {
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:${Uri.encode(recipient)}"); putExtra("sms_body", message); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        "I opened a text to $recipient. Please review the message, then tap Send."
    } catch (_: Exception) { "I'm afraid I couldn't open the messaging app for that text." }

    private fun openDialer(number: String): String = try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening the phone for $number. Review it and tap Call."
    } catch (_: Exception) { "I'm afraid I couldn't open the phone app." }

    private fun openContacts(): String = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening Contacts."
    } catch (_: Exception) { "I'm afraid I couldn't open Contacts." }

    private fun launchMessaging(): String = try {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        "Opening Messages."
    } catch (_: Exception) { "I'm afraid I couldn't open a messaging app." }

    private fun openAppByName(requested: String): String {
        val wanted = requested.trim().lowercase(Locale.getDefault())
        if (wanted.isBlank()) return "Please tell me which app you'd like me to open."
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            val match = apps.firstOrNull { info ->
                val label = info.loadLabel(context.packageManager).toString().lowercase(Locale.getDefault())
                label == wanted || label.contains(wanted) || wanted.contains(label)
            }
            if (match == null) return "I couldn't find $requested among the installed apps."
            val intent = context.packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
                ?: return "I found $requested, but Android did not provide a launch action."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Certainly. Opening ${match.loadLabel(context.packageManager)}."
        } catch (_: Exception) { "I'm afraid I couldn't open $requested." }
    }

    private fun isAppInstalled(name: String): Boolean {
        val wanted = name.lowercase(Locale.getDefault())
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).any {
            it.loadLabel(context.packageManager).toString().lowercase(Locale.getDefault()).contains(wanted)
        }
    }

    private fun openUrl(url: String, name: String): String = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Certainly. Opening $name."
    } catch (_: Exception) { "I'm afraid I couldn't open $name." }

    private fun searchGoogle(query: String): String {
        if (query.isBlank()) return "What would you like me to search for?"
        prefs.edit().putString("last_search", query).apply()
        return openUrl("https://www.google.com/search?q=" + Uri.encode(query), "Google")
    }

    private fun openSettingsPanel(action: String, name: String): String = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Certainly. Opening $name. Android requires the user to confirm that setting here."
    } catch (_: Exception) { "I'm afraid I couldn't open $name." }

    private fun setFlashlight(enabled: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id -> cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (cameraId == null) return "This phone doesn't report a controllable flashlight."
            cameraManager.setTorchMode(cameraId, enabled)
            if (enabled) "Flashlight on." else "Flashlight off."
        } catch (_: SecurityException) { "Android blocked flashlight control on this device." }
        catch (_: Exception) { "I'm afraid I couldn't change the flashlight." }
    }

    private fun setBrightness(value: String): String {
        val percent = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: return "Please give me a brightness percentage, such as 50 percent."
        val level = percent.coerceIn(1, 100)
        if (!Settings.System.canWrite(context)) {
            return try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Android needs permission to change brightness. I've opened the required setting."
            } catch (_: Exception) { "Android requires permission before I can change brightness." }
        }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (level * 255) / 100)
            "Brightness set to $level percent."
        } catch (_: Exception) { "I'm afraid I couldn't change the brightness." }
    }

    private fun adjustVolume(direction: Int): String = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        if (direction == AudioManager.ADJUST_RAISE) "Volume increased." else "Volume decreased."
    } catch (_: Exception) { "I'm afraid I couldn't change the volume." }

    private fun setMute(): String = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        "Ringer muted."
    } catch (_: Exception) { "Android blocked the mute action on this device." }

    private fun setTimer(value: String): String {
        return try {
            val seconds = parseDurationSeconds(value)
            if (seconds <= 0) return "I couldn't understand that timer duration."
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening the Clock timer for $value. Confirm it there."
        } catch (_: Exception) { "I'm afraid I couldn't open the Clock timer." }
    }

    private fun setAlarm(value: String): String {
        return try {
            val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(value)
            if (match == null) return "I couldn't understand that alarm time."
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifBlank { "0" }.toInt()
            val ampm = match.groupValues[3].lowercase(Locale.getDefault())
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour !in 0..23 || minute !in 0..59) return "I couldn't understand that alarm time."
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening the Clock alarm for $value. Confirm it there."
        } catch (_: Exception) { "I'm afraid I couldn't open the Clock alarm." }
    }

    private fun parseDurationSeconds(value: String): Int {
        val text = value.lowercase(Locale.getDefault())
        val number = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: return 0
        return when {
            text.contains("hour") -> number * 3600
            text.contains("minute") || text.contains("min") -> number * 60
            text.contains("second") || text.contains("sec") -> number
            else -> number
        }
    }

    private fun setReminder(value: String): String {
        val duration = parseDurationSeconds(value)
        if (duration <= 0) return "I couldn't understand how long to wait before the reminder."
        val message = value.replace(Regex("^\\d+\\s*(seconds?|secs?|minutes?|mins?|hours?)\\s*", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Your JARVIS reminder is due." }
        return scheduleReminder(duration * 1000L, message)
    }

    private fun parseReminderText(value: String): String {
        val inMatch = Regex("(.+?)\\s+in\\s+(\\d+\\s*(?:seconds?|secs?|minutes?|mins?|hours?))$", RegexOption.IGNORE_CASE).find(value)
        if (inMatch != null) {
            val message = inMatch.groupValues[1].trim()
            val duration = parseDurationSeconds(inMatch.groupValues[2])
            return if (duration > 0) scheduleReminder(duration * 1000L, message) else "I couldn't understand that reminder time."
        }
        return "Try saying, remind me to call John in 20 minutes."
    }

    private fun scheduleReminder(delayMs: Long, message: String): String {
        return try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, com.esn.jarvis.JarvisReminderReceiver::class.java).putExtra(com.esn.jarvis.JarvisReminderReceiver.EXTRA_MESSAGE, message)
            val requestCode = (System.currentTimeMillis() and 0x7fffffff).toInt()
            val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending)
            "Reminder set. I'll alert you in the requested time."
        } catch (_: Exception) { "I'm afraid I couldn't schedule that reminder on this device." }
    }

    private fun mediaButton(keyCode: Int, actionName: String): String = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        "$actionName command sent to the active media session."
    } catch (_: Exception) { "I'm afraid I couldn't control the active media session." }

    private fun changeSpeechRate(delta: Float): String {
        val current = prefs.getFloat("speech_rate", 0.82f).coerceIn(0.5f, 1.5f)
        val next = (current + delta).coerceIn(0.5f, 1.5f)
        prefs.edit().putFloat("speech_rate", next).apply()
        return "Speaking rate adjusted."
    }

    private fun resetSpeechRate(): String {
        prefs.edit().putFloat("speech_rate", 0.82f).apply()
        return "Speaking rate restored to cinematic default."
    }

    private fun batteryStatus(): String {
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return if (level >= 0) "Battery level is $level percent." else "I couldn't read the battery level."
    }

    private fun chargingStatus(): String {
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> "Yes. The phone is connected to power."
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No. The phone is not currently charging."
            else -> "I couldn't determine the charging status."
        }
    }

    private fun storageStatus(): String {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024L * 1024L * 1024L)
        val total = stat.totalBytes / (1024L * 1024L * 1024L)
        return "Approximately $free gigabytes are free out of $total gigabytes of internal storage."
    }

    private fun networkStatus(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "The phone is not currently connected to a network."
        val caps = manager.getNetworkCapabilities(network)
        return if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) "Wi-Fi appears to be connected." else "The phone is connected, but not through Wi-Fi."
    }

    private fun diagnostics(): String {
        val mic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val recognition = android.speech.SpeechRecognizer.isRecognitionAvailable(context)
        val apps = listOf("Discord", "YouTube", "Spotify", "Gmail", "Snapchat", "Minecraft").count { isAppInstalled(it) }
        val network = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork != null
        return "Diagnostics complete. Command engine online. Microphone permission ${if (mic) "available" else "missing"}. Speech recognition ${if (recognition) "available" else "unavailable"}. Network ${if (network) "connected" else "offline"}. $apps of 6 common apps detected."
    }

    private fun systemStatus(): String {
        val mic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return "JARVIS system status: command engine online, microphone ${if (mic) "ready" else "not granted"}, ${batteryStatus()}"
    }

    private fun gamingMode(): String {
        openAppByName("Discord")
        openAppByName("Spotify")
        openAppByName("Minecraft")
        return "Gaming mode activated. Discord, Spotify, and Minecraft have been launched where available."
    }

    private fun workMode(): String {
        openAppByName("Gmail")
        openUrl("https://www.google.com", "Google")
        return "Work mode activated. Gmail and Google are ready."
    }

    private fun help(): String = "I can launch installed apps by name, text or call, search Google, YouTube and Reddit, control flashlight, volume and supported brightness, open connectivity settings, control media, set timers, alarms and reminders, report battery and storage, run diagnostics, change speaking speed, answer basic conversation, remember the last response and search, and activate gaming or work mode. Phone Access adds supported screen controls."
}
