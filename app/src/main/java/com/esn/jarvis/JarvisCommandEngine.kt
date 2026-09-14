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
            command == "open discord" || command.contains("open discord") -> {
                launchPackage(context, "com.discord")
                "Opening Discord."
            }
            command == "open youtube" || command.contains("open youtube") -> {
                launchPackage(context, "com.google.android.youtube")
                "Opening YouTube."
            }
            command.startsWith("search youtube for ") -> {
                val query = raw.substringAfter("search youtube for ").trim()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")))
                "Searching YouTube for $query."
            }
            command.startsWith("search the web for ") || command.startsWith("search for ") -> {
                val query = raw.substringAfter("for ").trim()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                "Searching the web for $query."
            }
            command.contains("volume up") || command.contains("turn the volume up") -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Volume increased."
            }
            command.contains("volume down") || command.contains("turn the volume down") -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume decreased."
            }
            command.contains("open settings") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                "Opening settings."
            }
            command.contains("go home") || command == "home" -> {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Going home."
            }
            else -> "I can hear you, but I don't have a built-in action for that command yet."
        }
    }

    private fun launchPackage(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    }
}
