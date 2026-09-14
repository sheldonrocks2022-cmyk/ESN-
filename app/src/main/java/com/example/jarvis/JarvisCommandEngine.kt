package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisCommandEngine(private val context: Context) {
    fun execute(command: String): String {
        val raw = command.trim()
        val c = raw.lowercase(Locale.getDefault())

        if (c == "code 101" || c.contains("code one oh one")) {
            return "Emergency shutdown code accepted. JARVIS is going offline."
        }

        // SMS: use the system composer. JARVIS never silently sends a message.
        val smsPrefixes = listOf(
            "text ", "send a text to ", "send text to ", "send a text message to ",
            "send a text message ", "send sms to ", "sms "
        )
        for (prefix in smsPrefixes) {
            if (c.startsWith(prefix)) {
                val parsed = parseSms(raw.substring(prefix.length).trim())
                if (parsed != null) return openSmsComposer(parsed.first, parsed.second)
            }
        }

        // Calls: Dial intent requires the user to press Call.
        if (c == "call" || c.startsWith("call ")) {
            val number = raw.substringAfter("call", "").trim()
            if (number.isNotBlank()) return openDialer(number)
        }

        // Apps and common destinations.
        if (c == "open contacts" || c == "open my contacts" || c == "show contacts") return openContacts()
        if (c == "open messages" || c == "open my messages") return launchMessaging()
        if (c == "open snapchat") return launchPackage("com.snapchat.android", "Snapchat")
        if (c == "open discord") return launchPackage("com.discord", "Discord")
        if (c == "open youtube") return launchPackage("com.google.android.youtube", "YouTube")
        if (c == "open google") return openUrl("https://www.google.com", "Google")
        if (c == "open gmail") return launchPackage("com.google.android.gm", "Gmail")
        if (c == "open spotify") return launchPackage("com.spotify.music", "Spotify")
        if (c == "search google") return openUrl("https://www.google.com", "Google")
        if (c == "open settings" || c == "open phone settings") return openSettings()

        if (c.startsWith("search for ")) return searchGoogle(raw.substring(11).trim())
        if (c.startsWith("search the web for ")) return searchGoogle(raw.substring(19).trim())
        if (c.startsWith("search youtube for ")) return openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(raw.substring(19).trim()), "YouTube")
        if (c.startsWith("search reddit for ")) return openUrl("https://www.reddit.com/search/?q=" + Uri.encode(raw.substring(18).trim()), "Reddit")

        // Hardware/settings commands use supported Android APIs or open the relevant Settings panel.
        if (c == "turn on flashlight" || c == "turn the flashlight on" || c == "flashlight on") return setFlashlight(true)
        if (c == "turn off flashlight" || c == "turn the flashlight off" || c == "flashlight off") return setFlashlight(false)
        if (c.startsWith("set brightness to ")) return openDisplaySettings()
        if (c == "turn on wifi") return openSettingsPanel(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
        if (c == "turn off wifi") return openSettingsPanel(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
        if (c == "turn on bluetooth") return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
        if (c == "turn off bluetooth") return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
        if (c == "turn on airplane mode" || c == "turn off airplane mode") return openSettingsPanel(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode")

        if (c == "volume up" || c == "turn the volume up") return adjustVolume(AudioManager.ADJUST_RAISE)
        if (c == "volume down" || c == "turn the volume down") return adjustVolume(AudioManager.ADJUST_LOWER)
        if (c == "mute my phone" || c == "mute the phone") return setMute()

        // These remain supported command names, but Android requires explicit user/system authorization.
        if (c == "go back" || c == "back") return "Back control requires Phone Access to be enabled."
        if (c == "go home" || c == "home") return "Home control requires Phone Access to be enabled."
        if (c == "open recents" || c == "show recent apps") return "Recent-app control requires Phone Access to be enabled."
        if (c == "scroll up" || c == "scroll down") return "Scrolling requires Phone Access to be enabled."
        if (c.startsWith("click ")) return "Screen clicking requires Phone Access to be enabled."
        if (c.startsWith("type ")) return "Screen typing requires Phone Access to be enabled."
        if (c == "lock my phone") return "Screen locking requires Phone Access or a supported device-admin role."
        if (c == "take a screenshot" || c == "take screenshot") return "Screenshot capture requires an Android screen-capture consent flow."

        // Time/date.
        if (c == "what time is it" || c == "what's the time" || c == "what is the time") {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return "It is $time."
        }
        if (c == "what's today's date" || c == "what is today's date" || c == "what is the date" || c == "today's date") {
            val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return "Today is $date."
        }

        // Clock app intents are Play-friendly and let the user confirm the final action.
        if (c.startsWith("set a timer for ")) return setTimer(raw.substring(16).trim())
        if (c.startsWith("set an alarm for ")) return setAlarm(raw.substring(18).trim())
        if (c.startsWith("remind me in ")) return "Reminder command received. A dedicated reminder screen can be added without SMS or Accessibility permissions."

        // Media controls.
        if (c == "play music") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PLAY, "Play")
        if (c == "pause music") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause")
        if (c == "skip this song") return mediaButton(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "Skip")
        if (c == "open music" || c == "open spotify") return launchPackage("com.spotify.music", "Spotify")

        // JARVIS lifecycle/help commands.
        if (c == "jarvis help" || c == "help" || c == "what can you do") {
            return "I can text, call, open apps, search the web, control supported audio and flashlight functions, open Wi-Fi/Bluetooth settings, set timers and alarms, tell the time and date, and use Phone Access for supported screen actions."
        }
        if (c == "go to sleep" || c == "sleep" || c == "stop listening") return "JARVIS voice mode can be stopped from the notification controls."
        if (c == "wake up" || c == "jarvis wake up") return "JARVIS is already awake."
        if (c == "repeat that") return "I don't have the previous response stored yet."
        if (c == "cancel") return "Cancelled."

        return "I can hear you, but I don't have an action for that yet. Try help, text, call, open an app, search the web, set a timer, set an alarm, or ask for the time."
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

    private fun openSmsComposer(recipient: String, message: String): String = try {
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

    private fun openDialer(number: String): String = try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening the phone for $number. Review it and tap Call."
    } catch (_: Exception) { "I couldn't open the phone app." }

    private fun openContacts(): String = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening Contacts."
    } catch (_: Exception) { "I couldn't open Contacts." }

    private fun launchMessaging(): String = try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opening Messages."
    } catch (_: Exception) { "I couldn't open a messaging app." }

    private fun launchPackage(packageName: String, appName: String): String = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "$appName isn't installed on this phone."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opening $appName."
    } catch (_: Exception) { "I couldn't open $appName." }

    private fun openUrl(url: String, name: String): String = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening $name."
    } catch (_: Exception) { "I couldn't open $name." }

    private fun searchGoogle(query: String): String = openUrl("https://www.google.com/search?q=" + Uri.encode(query), "Google")

    private fun openSettings(): String = openSettingsPanel(Settings.ACTION_SETTINGS, "Settings")

    private fun openDisplaySettings(): String = openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, "Display settings")

    private fun openSettingsPanel(action: String, name: String): String = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening $name. Android requires the user to confirm that setting here."
    } catch (_: Exception) { "I couldn't open $name." }

    private fun setFlashlight(enabled: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This phone doesn't report a controllable flashlight."
            cameraManager.setTorchMode(cameraId, enabled)
            if (enabled) "Flashlight on." else "Flashlight off."
        } catch (_: SecurityException) { "Android blocked flashlight control on this device." }
        catch (_: Exception) { "I couldn't change the flashlight." }
    }

    private fun adjustVolume(direction: Int): String {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            if (direction == AudioManager.ADJUST_RAISE) "Volume increased." else "Volume decreased."
        } catch (_: Exception) { "I couldn't change the volume." }
    }

    private fun setMute(): String {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "Ringer muted."
        } catch (_: Exception) { "Android blocked the mute action on this device." }
    }

    private fun setTimer(value: String): String = try {
        val seconds = parseDurationSeconds(value)
        if (seconds <= 0) return "I couldn't understand that timer duration."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opening the Clock timer for $value. Confirm it there."
    } catch (_: Exception) { "I couldn't open the Clock timer." }

    private fun setAlarm(value: String): String = try {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(value)
            ?: return "I couldn't understand that alarm time."
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val ampm = match.groupValues[3].lowercase(Locale.getDefault())
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return "I couldn't understand that alarm time."
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opening the Clock alarm for $value. Confirm it there."
    } catch (_: Exception) { "I couldn't open the Clock alarm." }

    private fun parseDurationSeconds(value: String): Int {
        val text = value.lowercase(Locale.getDefault())
        val number = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: return 0
        return when {
            text.contains("hour") -> number * 3600
            text.contains("minute") || text.contains("min") -> number * 60
            else -> number
        }
    }

    private fun mediaButton(keyCode: Int, actionName: String): String = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
        "$actionName command sent to the active media session."
    } catch (_: Exception) { "I couldn't control the active media session." }
}
