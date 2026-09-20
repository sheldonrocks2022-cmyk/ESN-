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
import java.util.Calendar
import java.util.concurrent.TimeUnit

object JarvisCommandEngine {
    fun execute(context: Context, raw: String): String {
        val original = raw.trim()
        val command = original.lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()
        if (command.isBlank()) return "I didn't catch that."
        if (command in setOf("wake my phone","wake phone","show lock screen","unlock phone")) return JarvisSecurity.wakeLockScreen(context)
        if (command in setOf("security status","security center","check security")) return JarvisSecurity.status(context).joinToString(". ") { "${it.first}: ${it.second}" }

        return when {
            command == "stop" || command == "cancel" || command == "be quiet" || command == "shut up" -> "Standing by."
            command.matches(Regex("(open|launch|start|run) (.+)")) -> {
                val target = command.replaceFirst(Regex("^(open|launch|start|run) "), "").trim()
                if (tryLaunchKnownApp(context, target)) "Opening ${displayName(target)}." else launchByAppLabel(context, target)
            }
            command.startsWith("play youtube ") -> { val q=original.replaceFirst(Regex("(?i)^play youtube\\s+"),"").trim(); if(q.isBlank()) "Tell me what to play on YouTube." else { openUrl(context,"https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Opening YouTube results for $q." } }
            command.startsWith("navigate to ") || command.startsWith("directions to ") -> { val q=original.replaceFirst(Regex("(?i)^(navigate to|directions to)\\s+"),"").trim(); if(q.isBlank()) "Tell me where to navigate." else { openUrl(context,"google.navigation:q=${Uri.encode(q)}"); "Starting navigation to $q." } }
            command.startsWith("search youtube") || command.startsWith("find on youtube") -> {
                val q = command.replaceFirst(Regex("^(search youtube( for)?|find on youtube) "), "").trim()
                if (q.isBlank()) "Tell me what to search for on YouTube." else { openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Searching YouTube for $q." }
            }
            command.startsWith("search the web") || command.startsWith("search for ") || command.startsWith("google ") || command.startsWith("look up ") -> {
                val q = command.replaceFirst(Regex("^(search the web( for)?|search for|google|look up) "), "").trim()
                if (q.isBlank()) "Tell me what to search for." else { openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching for $q." }
            }
            command.startsWith("play playlist ") || command.startsWith("play album ") || command.startsWith("play artist ") || command.startsWith("play song ") -> playOnSpotify(context, original)
            command == "shuffle spotify" -> "Shuffle can be changed in Spotify."
            command == "repeat spotify" -> "Repeat can be changed in Spotify."
            command.matches(Regex("^(play|put on) .+( on spotify)?$")) && command != "play music" && command != "play it" -> playOnSpotify(context, original)
            command == "pause it" || command.contains("pause") && !command.contains("pause timer") -> media(context,android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,"Media paused.")
            command == "skip this" || command == "skip it" -> media(context,android.view.KeyEvent.KEYCODE_MEDIA_NEXT,"Skipping.")
            command == "go back a song" || command == "go back a track" -> media(context,android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,"Previous track.")
            command.contains("resume") || command.contains("play music") || command == "play" || command=="play it" -> media(context,android.view.KeyEvent.KEYCODE_MEDIA_PLAY,"Media playback started.")
            command.contains("next song") || command.contains("next track") || command == "next" -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "Next track.")
            command.contains("previous song") || command.contains("previous track") || command == "previous" -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track.")
            command.contains("volume up") || command.contains("turn the volume up") || command.contains("increase volume") || command.contains("louder") -> changeVolume(context, AudioManager.ADJUST_RAISE, "Volume increased.")
            command.contains("volume down") || command.contains("turn the volume down") || command.contains("decrease volume") || command.contains("quieter") -> changeVolume(context, AudioManager.ADJUST_LOWER, "Volume decreased.")
            command.contains("unmute") -> changeVolume(context, AudioManager.ADJUST_UNMUTE, "Volume unmuted.")
            command.contains("mute") || command.contains("silence volume") -> changeVolume(context, AudioManager.ADJUST_MUTE, "Volume muted.")
            command.contains("flashlight") || command.contains("flash light") || command.contains("torch") -> toggleFlashlight(context, command.contains("off"))
            command == "make it brighter" -> adjustBrightness(context,25)
            command == "make it dimmer" || command == "dim it" -> adjustBrightness(context,-25)
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
            command.contains("notification settings") -> openSettings(context, "android.settings.APP_NOTIFICATION_SETTINGS", "Notification settings")
            command.contains("app settings") -> openSettings(context, Settings.ACTION_APPLICATION_SETTINGS, "App settings")
            command == "settings" || command.contains("open settings") -> openSettings(context, Settings.ACTION_SETTINGS, "Settings")
            command == "back" || command.contains("go back") -> if (JarvisAccessibilityService.back()) "Going back." else phoneAccessRequired()
            command == "home" || command.contains("go home") -> if (JarvisAccessibilityService.home()) "Going home." else phoneAccessRequired()
            command.contains("recent apps") || command == "recents" -> if (JarvisAccessibilityService.recents()) "Showing recent apps." else phoneAccessRequired()
            command.startsWith("scroll down") -> if (JarvisAccessibilityService.scrollForward()) "Scrolling down." else phoneAccessRequired()
            command.startsWith("scroll up") -> if (JarvisAccessibilityService.scrollBackward()) "Scrolling up." else phoneAccessRequired()
            command.startsWith("click ") -> { val target = original.substringAfter("click ").trim(); if (JarvisAccessibilityService.clickText(target)) "Clicked $target." else phoneAccessRequired() }
            command.startsWith("tap ") -> { val target = original.substringAfter("tap ").trim(); if (JarvisAccessibilityService.clickText(target)) "Tapped $target." else phoneAccessRequired() }
            command.startsWith("type ") || command.startsWith("enter ") -> { val text = original.replaceFirst(Regex("^(type|enter) "), ""); if (JarvisAccessibilityService.typeText(text)) "Text entered." else phoneAccessRequired() }
            command.contains("what time") || command == "time" || command.contains("current time") -> "The current time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            command.contains("what date") || command == "date" || command.contains("today's date") || command.contains("what day") -> "Today is ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())}."
            command == "list reminders" || command == "show reminders" || command == "what are my reminders" -> JarvisReminderManager.list(context)
            command == "cancel all reminders" || command == "clear all reminders" -> JarvisReminderManager.cancelAll(context)
            command.startsWith("cancel reminder ") -> JarvisReminderManager.cancelMatching(context,command.removePrefix("cancel reminder ").trim())
            command.contains("set a timer") || command.contains("start a timer") || command.matches(Regex("timer for .+")) -> setTimer(context, command)
            command.contains("remind me") || command.contains("set a reminder") -> setReminder(context, command)
            command.contains("set an alarm") || command.contains("set alarm") || command.contains("alarm for") -> setAlarm(context, command)
            command.startsWith("text ") || command.startsWith("send a text") || command.startsWith("send sms") || command.startsWith("sms ") -> handleSms(context, original)
            command.startsWith("call ") || command.startsWith("phone ") || command.startsWith("dial ") -> handleCall(context, original)
            command.contains("email") || command.contains("compose an email") -> openSystemIntent(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), "email")
            command.contains("camera") || command == "take a picture" || command == "take a photo" -> openSystemIntent(context, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera")
            command.contains("clock") || command.contains("alarm app") -> openSystemIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), "clock")
            command.contains("calendar") -> openSystemIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), "calendar")
            command.contains("contacts") -> openSystemIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS), "contacts")
            command.contains("downloads") -> openSystemIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.providers.downloads.documents/root/downloads")), "downloads")
            command.contains("gaming mode") -> setMode(context, true)
            command.contains("work mode") -> setMode(context, false)
            command.contains("speak slower") || command.contains("talk slower") -> { changeSpeechRate(context, -0.1f); "Speech speed reduced." }
            command.contains("speak faster") || command.contains("talk faster") -> { changeSpeechRate(context, 0.1f); "Speech speed increased." }
            command.contains("normal speech") || command.contains("normal speed") -> { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putFloat("speech_rate", 0.82f).apply(); "Speech speed restored to normal." }
            command.startsWith("calculate ") || command.startsWith("what is ") && containsMath(command) -> calculate(command)
            command.contains("who are you") || command.contains("what are you") -> "I am JARVIS, your Android control system."
            command.contains("hello") || command.contains("hi jarvis") || command == "hey jarvis" -> "Good to hear from you. Systems are online."
            command.contains("thank you") || command.contains("thanks") -> "You're welcome."
            else -> "I can't complete that one yet. Try saying it another way, or ask me what I can do."
        }
    }

    private fun playOnSpotify(context:Context,original:String):String {
        val query=original.replaceFirst(Regex("(?i)^(play|put on)\\s+"),"").replaceFirst(Regex("(?i)\\s+on spotify$"),"").trim()
        if(query.isBlank())return "Tell me what song to play on Spotify."
        return try{
            val intent=Intent(Intent.ACTION_VIEW,Uri.parse("spotify:search:${Uri.encode(query)}")).setPackage("com.spotify.music").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent);JarvisContext.remember(context,"media","spotify");"Opening $query in Spotify."
        }catch(_:Exception){"I couldn't open Spotify."}
    }
    private fun displayName(target: String) = target.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    private fun tryLaunchKnownApp(context: Context, target: String): Boolean {
        val known = mapOf("discord" to "com.discord", "youtube" to "com.google.android.youtube", "minecraft" to "com.mojang.minecraftpe", "spotify" to "com.spotify.music", "snapchat" to "com.snapchat.android", "gmail" to "com.google.android.gm", "chrome" to "com.android.chrome", "google chrome" to "com.android.chrome", "reddit" to "com.reddit.frontpage", "facebook" to "com.facebook.katana", "instagram" to "com.instagram.android", "tiktok" to "com.zhiliaoapp.musically", "capcut" to "com.lemon.lvoverseas", "x" to "com.twitter.android", "twitter" to "com.twitter.android", "maps" to "com.google.android.apps.maps", "google maps" to "com.google.android.apps.maps", "photos" to "com.google.android.apps.photos", "google photos" to "com.google.android.apps.photos", "drive" to "com.google.android.apps.docs", "google drive" to "com.google.android.apps.docs", "files" to "com.google.android.documentsui", "calculator" to "com.google.android.calculator", "play store" to "com.android.vending", "settings" to "com.android.settings")
        val pkg = known[target] ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }
    private fun launchByAppLabel(context: Context, target: String): String {
        val wanted = target.replace(Regex("[^a-z0-9 ]"), "").trim()
        val apps = context.packageManager.getInstalledApplications(0)
        val match = apps.firstOrNull { label(it, context).lowercase(Locale.getDefault()) == wanted } ?: apps.firstOrNull { label(it, context).lowercase(Locale.getDefault()).contains(wanted) }
        if (match != null) { val intent = context.packageManager.getLaunchIntentForPackage(match.packageName); if (intent != null) { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Opening ${label(match, context)}." } }
        return "I couldn't find an installed app called ${displayName(target)}."
    }
    private fun label(info: android.content.pm.ApplicationInfo, context: Context) = context.packageManager.getApplicationLabel(info).toString()
    private fun openSettings(context: Context, action: String, name: String): String = try { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening $name." } catch (_: Exception) { "I couldn't open $name." }
    private fun openSystemIntent(context: Context, intent: Intent, name: String): String = try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening $name." } catch (_: Exception) { "I couldn't open $name." }
    private fun openUrl(context: Context, url: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun media(context:Context,key:Int,response:String):String{JarvisContext.remember(context,"media","active");return mediaKey(context,key,response)}
    private fun mediaKey(context: Context, key: Int, response: String): String { try { audio(context).dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)); audio(context).dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key)) } catch (_: Exception) {}; return response }
    private fun changeVolume(context: Context, direction: Int, response: String): String { audio(context).adjustVolume(direction, AudioManager.FLAG_SHOW_UI); return response }
    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun batteryStatus(context: Context): String { val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)); val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1; val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL; return if (level >= 0) "Battery is at $level percent${if (charging) ", and the phone is charging" else ""}." else "I couldn't read the battery level." }
    private fun systemStatus(context: Context): String = "${batteryStatus(context)} Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}. JARVIS command systems are online."
    private fun diagnostics(context: Context): String { val missing=mutableListOf<String>();if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing+="microphone permission";if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing+="notification permission";if(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing+="contacts permission";if(context.checkSelfPermission(android.Manifest.permission.SEND_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing+="SMS permission";val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty();if(!enabled.contains(context.packageName,ignoreCase=true))missing+="Phone Access accessibility service";val listeners=Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty();if(!listeners.contains(context.packageName,ignoreCase=true))missing+="Notification Access";val speech=if(android.speech.SpeechRecognizer.isRecognitionAvailable(context))"speech recognition available" else "speech recognition unavailable";return if(missing.isEmpty())"Diagnostics complete. $speech. Required JARVIS permissions and services appear enabled. ${batteryStatus(context)}" else "Diagnostics found: "+missing.joinToString(", ")+". $speech." }
    private fun storageStatus(): String { val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path); val free = stat.availableBytes / (1024L * 1024L * 1024L); val total = stat.totalBytes / (1024L * 1024L * 1024L); return "Storage has approximately $free GB free of $total GB." }
    private fun toggleFlashlight(context: Context, forceOff: Boolean): String = try { val camera = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager; val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone doesn't appear to have a flashlight."; val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE); val current = prefs.getBoolean("flashlight", false); val next = if (forceOff) false else !current; camera.setTorchMode(id, next); prefs.edit().putBoolean("flashlight", next).apply(); if (next) "Flashlight on." else "Flashlight off." } catch (_: Exception) { "I couldn't control the flashlight on this phone." }
    private fun adjustBrightness(context:Context,delta:Int):String { if(!Settings.System.canWrite(context)) return "Please allow JARVIS to modify system settings."; return try{val current=Settings.System.getInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS,128);val next=(current+delta).coerceIn(1,255);Settings.System.putInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS,next);"Brightness adjusted."}catch(_:Exception){"I couldn't change the brightness."} }
    private fun setBrightness(context: Context, command: String): String { val match = Regex("(\\d{1,3})\\s*(percent|%)?").find(command.substringAfter("brightness")) ?: return "Tell me a brightness percentage, for example: set brightness to 50 percent."; val percent = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return "That brightness value isn't valid."; return try { if (!Settings.System.canWrite(context)) return "Please allow JARVIS to modify system settings."; Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255) / 100); "Brightness set to $percent percent." } catch (_: Exception) { "I couldn't change the brightness." } }
    private fun setTimer(context: Context, command: String): String { val match = Regex("(\\d+)\\s*(seconds?|minutes?|hours?)").find(command) ?: return "Tell me how long, for example: set a timer for 5 minutes."; val amount = match.groupValues[1].toLong(); val unit = match.groupValues[2]; val millis = when { unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(amount); unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(amount); else -> TimeUnit.HOURS.toMillis(amount) }; val intent = Intent(context, JarvisReminderReceiver::class.java).putExtra("message", "Timer finished."); val pi = PendingIntent.getBroadcast(context, (System.currentTimeMillis() and 0x7fffffff).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millis, pi); return "Timer set for $amount $unit." }
    private fun setReminder(context:Context,command:String):String {
        val r=Regex("\\bevery\\s+(day|daily|week|weekly|hour|hourly)\\b").find(command)
        if(r!=null){val msg=command.replaceFirst(Regex("^(remind me|set a reminder)( to)? "),"").replace(r.value,"").trim().ifBlank{"Reminder."};val interval=if(r.groupValues[1].startsWith("hour"))TimeUnit.HOURS.toMillis(1) else if(r.groupValues[1].startsWith("week"))TimeUnit.DAYS.toMillis(7) else TimeUnit.DAYS.toMillis(1);val id=(System.currentTimeMillis() and 0x7fffffff).toInt();JarvisReminderManager.recordRecurring(context,id,msg,System.currentTimeMillis()+interval,interval);return "Recurring reminder set."}
        val absolute=parseNaturalReminderTime(command)
        if(absolute!=null){val clean=command.replaceFirst(Regex("^(remind me|set a reminder)( to)? "),"").replace(Regex("\\s+(today|tomorrow|tonight)(\\s+at\\s+.+)?$"),"").trim().ifBlank{"Reminder."};val id=(System.currentTimeMillis() and 0x7fffffff).toInt();val intent=Intent(context,JarvisReminderReceiver::class.java).putExtra("message",clean).putExtra("reminder_id",id);val pi=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,absolute,pi);JarvisReminderManager.record(context,id,clean,absolute);return "Reminder set for ${SimpleDateFormat("EEE h:mm a",Locale.getDefault()).format(Date(absolute))}."}
        val m=Regex("in (\\d+)\\s*(seconds?|minutes?|hours?|days?)").find(command)?:return "Tell me when, for example in 10 minutes, tomorrow at 8 AM, tonight at 9, every day, every hour, or every week."
        val amount=m.groupValues[1].toLong();val unit=m.groupValues[2];val ms=when{unit.startsWith("second")->TimeUnit.SECONDS.toMillis(amount);unit.startsWith("minute")->TimeUnit.MINUTES.toMillis(amount);unit.startsWith("hour")->TimeUnit.HOURS.toMillis(amount);else->TimeUnit.DAYS.toMillis(amount)}
        val clean=command.replaceFirst(Regex("^(remind me|set a reminder)( to)? "),"").replace(m.value,"").trim().ifBlank{"Reminder."};val id=(System.currentTimeMillis() and 0x7fffffff).toInt();val due=System.currentTimeMillis()+ms;val intent=Intent(context,JarvisReminderReceiver::class.java).putExtra("message",clean).putExtra("reminder_id",id);val pi=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pi);JarvisReminderManager.record(context,id,clean,due);return "Reminder set."
    }
    private fun parseNaturalReminderTime(command:String):Long? {
        val cal=Calendar.getInstance()
        val dayWord=Regex("\\b(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").find(command)?.groupValues?.get(1)?:return null
        val tm=Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(command)
        if(dayWord=="tomorrow")cal.add(Calendar.DAY_OF_YEAR,1)
        val weekdays=mapOf("sunday" to Calendar.SUNDAY,"monday" to Calendar.MONDAY,"tuesday" to Calendar.TUESDAY,"wednesday" to Calendar.WEDNESDAY,"thursday" to Calendar.THURSDAY,"friday" to Calendar.FRIDAY,"saturday" to Calendar.SATURDAY)
        weekdays[dayWord]?.let{target->var add=(target-cal.get(Calendar.DAY_OF_WEEK)+7)%7;if(command.contains("next $dayWord")||add==0)add+=7;cal.add(Calendar.DAY_OF_YEAR,add)}
        var hour=tm?.groupValues?.get(1)?.toIntOrNull()?:if(dayWord=="tonight")21 else return null
        val minute=tm?.groupValues?.get(2)?.toIntOrNull()?:0;val ap=tm?.groupValues?.get(3).orEmpty()
        if(ap=="pm"&&hour<12)hour+=12;if(ap=="am"&&hour==12)hour=0
        cal.set(Calendar.HOUR_OF_DAY,hour.coerceIn(0,23));cal.set(Calendar.MINUTE,minute.coerceIn(0,59));cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0)
        if(dayWord=="today"&&cal.timeInMillis<=System.currentTimeMillis())return null
        return cal.timeInMillis
    }
    private fun setAlarm(context: Context, command: String): String = openSystemIntent(context, Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "JARVIS alarm"), "alarm setup")
    private fun handleSms(context: Context, original: String): String { val text = original.replaceFirst(Regex("^(text|send a text|send sms|sms) "), "").trim(); if (text.isBlank()) return "Tell me what you'd like to text."; return openSystemIntent(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text), "text message") }
    private fun handleCall(context: Context, original: String): String {
        val who=original.replaceFirst(Regex("^(call|phone|dial) ",RegexOption.IGNORE_CASE),"").trim()
        if(who.isBlank())return "Tell me who you'd like to call."
        val digits=who.filter{it.isDigit()}
        if(digits.length>=7)return openSystemIntent(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(who)}")),"dialer")
        val type=Regex("\\b(mobile|cell|home|work)$",RegexOption.IGNORE_CASE).find(who)?.groupValues?.get(1).orEmpty();val contactName=if(type.isBlank())who else who.removeSuffix(type).trim();val resolved=if(type.isBlank())JarvisMessaging.resolveContact(context,contactName) else JarvisMessaging.resolveContactByType(context,contactName,type)
        if(resolved==null){val choices=JarvisMessaging.contactChoices(context,contactName).map{it.first}.distinct();return if(choices.size>1)"I found multiple contacts: ${choices.take(4).joinToString(", ")}. Say the full name you want." else "I couldn't find one clear contact named $who. I won't dial a guessed number."}
        JarvisContext.remember(context,"contact",resolved.first)
        return openSystemIntent(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(resolved.second)}")),resolved.first)
    }
    private fun setMode(context: Context, gaming: Boolean): String { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putBoolean("gaming_mode", gaming).apply(); return if (gaming) "Gaming Mode enabled." else "Work Mode enabled." }
    private fun changeSpeechRate(context: Context, delta: Float) { val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE); val current = prefs.getFloat("speech_rate", 0.82f); prefs.edit().putFloat("speech_rate", (current + delta).coerceIn(0.55f, 1.2f)).apply() }
    private fun containsMath(command: String) = command.any { it.isDigit() } && command.any { it in "+-*/" }
    private fun calculate(command: String): String { val expr = command.replaceFirst(Regex("^(calculate|what is) "), "").replace(" ", ""); return try { val parts = Regex("(-?\\d+(?:\\.\\d+)?)([+*/-])(-?\\d+(?:\\.\\d+)?)").matchEntire(expr) ?: return "I can calculate simple expressions such as 12 plus 7."; val a = parts.groupValues[1].toDouble(); val op = parts.groupValues[2]; val b = parts.groupValues[3].toDouble(); val value = when (op) { "+" -> a+b; "-" -> a-b; "*" -> a*b; else -> if (b == 0.0) return "Division by zero isn't allowed." else a/b }; "The answer is ${if (value % 1.0 == 0.0) value.toLong() else value}." } catch (_: Exception) { "I couldn't calculate that." } }
    private fun phoneAccessRequired() = "Phone Access is not enabled. Enable the JARVIS accessibility service to control the screen."
}
