package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline status briefing. Reads only local JARVIS MAX data and Android system status.
 * No private note contents are exposed in the briefing.
 */
object JarvisMaxBriefing {
    fun generate(context: Context): String {
        val time = SimpleDateFormat("EEEE, MMMM d, h:mm a", Locale.getDefault()).format(Date())
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryText = if (level < 0) "unknown" else "$level percent"
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val memoryCount = JarvisMaxMemory(context).list().size
        val taskSummary = JarvisMaxTaskPlanner(context).summary()
        val focus = JarvisMaxFocus(context).status()
        return "JARVIS MAX briefing for $time. Battery: $batteryText. Power saving: ${if (power.isPowerSaveMode) "on" else "off"}. $taskSummary Local notes: $memoryCount. $focus"
    }
}
