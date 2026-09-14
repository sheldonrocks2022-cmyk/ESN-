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
            command.contains("open discord") -> { launchPackage(context, "com.discord"); "Opening Discord." }
            command.contains("open youtube") -> { launchPackage(context, "com.google.android.youtube"); "Opening YouTube." }
            command.startsWith("search youtube for ") -> { val q = raw.substringAfter("search youtube for ").trim(); openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(q)}"); "Searching YouTube for $q." }
            command.startsWith("search the web for ") || command.startsWith("search for ") -> { val q = raw.substringAfter("for ").trim(); openUrl(context, "https://www.google.com/search?q=${Uri.encode(q)}"); "Searching the web for $q." }
            command.contains("volume up") || command.contains("turn the volume up") -> { audio(context).adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Volume increased." }
            command.contains("volume down") || command.contains("turn the volume down") -> { audio(context).adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Volume decreased." }
            command.contains("go back") || command == "back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone access is not enabled."
            command.contains("go home") || command == "home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone access is not enabled."
            command.contains("show recent apps") || command == "recents" -> if (JarvisAccessibilityService.recents()) "Showing recent apps." else "Phone access is not enabled."
            command == "scroll down" || command == "scroll down" -> if (JarvisAccessibilityService.scrollForward()) "Scrolling down." else "I couldn't scroll the current screen."
            command == "scroll up" -> if (JarvisAccessibilityService.scrollBackward()) "Scrolling up." else "I couldn't scroll the current screen."
            command.startsWith("click ") -> { val target = raw.substringAfter("click ").trim(); if (JarvisAccessibilityService.clickText(target)) "Clicked $target." else "I couldn't find a clickable $target on the current screen." }
            command.startsWith("type ") -> { val text = raw.substringAfter("type ").trim(); if (JarvisAccessibilityService.typeText(text)) "Text entered." else "I couldn't find an editable field on the current screen." }
            command.contains("open settings") -> { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)); "Opening settings." }
            else -> "I can hear you, but I don't have an action for that yet. Try commands like open Discord, click Settings, type hello, scroll down, go back, or go home."
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
