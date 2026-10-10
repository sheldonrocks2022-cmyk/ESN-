package com.esn.jarvis

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.AudioManager
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Offline utilities: no elevated permissions, no background network requests. */
object JarvisMaxUtilities {
    fun execute(context: Context, command: String, original: String): String? = when {
        command == "max device" -> "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}."
        command == "max storage" -> {
            val stats = StatFs(Environment.getDataDirectory().absolutePath)
            val free = stats.availableBytes / 1_073_741_824L
            val total = stats.totalBytes / 1_073_741_824L
            "Internal data partition: approximately $free GiB free of $total GiB."
        }
        command == "max network" -> {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            when {
                capabilities == null -> "No active network detected."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Active connection: Wi-Fi."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Active connection: cellular."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Active connection: Ethernet."
                else -> "Active network detected."
            }
        }
        command == "max volume" -> {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            "Media volume: $level of $max."
        }
        command == "max timezone" -> "Timezone: ${java.util.TimeZone.getDefault().id}."
        command == "max language" -> "Device language: ${Locale.getDefault().displayLanguage}."
        command == "max uptime" -> "Device has been running approximately ${android.os.SystemClock.elapsedRealtime() / 3_600_000L} hours since reboot."
        command == "max secure lock" -> {
            val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (manager.isDeviceSecure) "Secure device lock configured." else "Secure device lock is not configured."
        }
        command == "max accessibility" -> {
            val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (enabled) "Android accessibility services are enabled." else "Android accessibility services are disabled."
        }
        command == "max uuid" -> "Random identifier: ${UUID.randomUUID()}."
        command.startsWith("max word count ") -> {
            val text = original.drop("max word count ".length).trim()
            "Words: ${text.split(Regex("\\s+")).count { it.isNotBlank() }}. Characters: ${text.length}."
        }
        command.startsWith("max reverse ") -> original.drop("max reverse ".length).reversed().take(1500)
        command.startsWith("max uppercase ") -> original.drop("max uppercase ".length).uppercase(Locale.ROOT).take(1500)
        command.startsWith("max lowercase ") -> original.drop("max lowercase ".length).lowercase(Locale.ROOT).take(1500)
        command.startsWith("max sha256 ") -> {
            val input = original.drop("max sha256 ".length)
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            "SHA-256: ${digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }}."
        }
        else -> null
    }
}
