package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri

object JarvisCommandEngine {
    fun execute(context: Context, raw: String): String {
        val command = raw.trim().lowercase()
        if (command.isBlank()) return "I didn't catch that."
        return when {
            command.contains("code 101") -> "Code 101 is handled by the voice service."
            command.contains("open discord") -> { launchPackage(context, "com.discord"); "Opening Discord." }
            command.contains("open youtube") -> { launchPackage(context, "com.google.android.youtube"); "Opening YouTube." }
            command.startsWith("search youtube for ") -> { val q = raw.substringAfter("search youtube for ").trim(); openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Searching YouTube for $q." }
            command.startsWith("search the web for ") -> { val q = raw.substringAfter("search the web for ").trim(); openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching the web for $q." }
            command.startsWith("search for ") -> { val q = raw.substringAfter("search for ").trim(); openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching the web for $q." }
            command.contains("volume up") || command.contains("turn the volume up") -> { audio(context).adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Volume increased." }
            command.contains("volume down") || command.contains("turn the volume down") -> { audio(context).adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Volume decreased." }
            command.contains("open settings") -> { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening settings." }
            command.contains("go back") || command == "back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access isn't enabled, so I can't control the screen yet."
            command.contains("go home") || command == "home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access isn't enabled, so I can't control the screen yet."
            command.contains("show recent apps") || command == "recents" -> if (JarvisAccessibilityService.recents()) "Showing recent apps." else "Phone Access isn't enabled, so I can't control the screen yet."
            command == "scroll down" -> if (JarvisAccessibilityService.scrollForward()) "Scrolling down." else "I can't control scrolling without Phone Access."
            command == "scroll up" -> if (JarvisAccessibilityService.scrollBackward()) "Scrolling up." else "I can't control scrolling without Phone Access."
            command.startsWith("click ") -> { val target = raw.substringAfter("click ").trim(); if (JarvisAccessibilityService.clickText(target)) "Clicked $target." else "I can't click that without Phone Access enabled." }
            command.startsWith("type ") -> { val text = raw.substringAfter("type ").trim(); if (JarvisAccessibilityService.typeText(text)) "Text entered." else "I can't type into the screen without Phone Access enabled." }
            else -> "I heard you, but I don't have that command yet. Try open Discord, open YouTube, search the web for something, or volume up."
        }
    }

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun openUrl(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun launchPackage(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else openUrl(context, "market://details?id=$packageName")
    }
}
