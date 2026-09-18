package com.esn.jarvis

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object JarvisSecurity {
    fun status(context: Context): List<Pair<String,String>> {
        val prefs=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE)
        val accessibility=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains(context.packageName)
        val notifications=Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty().contains(context.packageName)
        return listOf(
            "OWNER VOICE" to if(OwnerVoiceProfile.isEnrolled(context)) "ENROLLED" else "NOT ENROLLED",
            "EMERGENCY LOCK" to if(prefs.getBoolean("emergency_shutdown",false)) "LOCKED" else "READY",
            "MICROPHONE" to if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) "READY" else "PERMISSION NEEDED",
            "ACCESSIBILITY" to if(accessibility) "ENABLED" else "OFF",
            "NOTIFICATIONS" to if(notifications) "ENABLED" else "OFF",
            "CONTACTS" to if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) "READY" else "PERMISSION NEEDED",
            "SMS" to if(context.checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED) "READY" else "PERMISSION NEEDED",
            "VOICE SERVICE" to if(prefs.getBoolean("active",false)) "ACTIVE" else "OFF"
        )
    }

    fun wakeLockScreen(context: Context): String {
        val pm=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if(!pm.isInteractive){
            @Suppress("DEPRECATION")
            val wl=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,"jarvis:wake")
            wl.acquire(2500)
        }
        val km=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if(km.isKeyguardLocked) "Lock screen ready. Authenticate with Android to continue." else "Your phone is already unlocked."
    }
}
