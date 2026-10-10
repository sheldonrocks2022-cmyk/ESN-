package com.esn.jarvis

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.content.Intent
import android.content.IntentFilter
import java.util.Locale

/**
 * JARVIS MAX native command layer. All commands run locally.
 * No network requests, account access or autonomous external actions.
 */
object JarvisMaxCommands {
    fun execute(context: Context, raw: String): String? {
        val original = raw.trim().replace(Regex("^(?i:jarvis)[,\\s]+"), "")
        val command = original.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (!command.startsWith("max ")) return null
        val memory = JarvisMaxMemory(context)
        return when {
            command == "max help" || command == "max commands" ->
                "JARVIS MAX: status, diagnostics, battery, memories, memory stats, find memory followed by keywords, remember followed by text, forget followed by number, and forget all confirm."
            command.startsWith("max remember ") -> {
                val note = original.substringAfter(" ", "").substringAfter(" ", "").trim()
                if (memory.remember(note)) "Memory saved locally." else "Unable to save memory. Limit 500 characters."
            }
            command == "max memories" || command == "max memory list" -> {
                val items = memory.list()
                if (items.isEmpty()) "No JARVIS MAX memories."
                else items.mapIndexed { index, note -> "${index + 1}. $note" }.joinToString(". ").take(2200)
            }
            command == "max memory stats" -> {
                val items = memory.list()
                "Memory core: ${items.size} of 100 notes used. ${items.sumOf { it.length }} characters stored on this device."
            }
            command.startsWith("max find memory ") -> {
                val query = original.substringAfter("find memory ", "").trim()
                if (query.isBlank()) "Tell me what to search for."
                else {
                    val matches = memory.list().mapIndexedNotNull { index, note ->
                        if (note.contains(query, ignoreCase = true)) "${index + 1}. $note" else null
                    }
                    if (matches.isEmpty()) "No local memories matched."
                    else matches.take(10).joinToString(". ").take(2200)
                }
            }
            command == "max forget all confirm" -> {
                if (memory.forgetAll()) "All JARVIS MAX local notes deleted." else "Unable to clear local notes."
            }
            command == "max forget all" || command == "max clear memories" ->
                "This permanently deletes all JARVIS MAX notes on this phone. Say max forget all confirm to proceed."
            command.startsWith("max forget ") -> {
                val number = command.removePrefix("max forget ").trim().toIntOrNull()
                if (number != null && memory.forget(number - 1)) "Memory deleted." else "Invalid memory number."
            }
            command == "max battery" -> {
                val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                if (level < 0) "Battery level unavailable." else "Battery at $level percent."
            }
            command == "max diagnostics" -> {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                "Android ${Build.VERSION.RELEASE}; device ${Build.MANUFACTURER} ${Build.MODEL}; power saving ${if (power.isPowerSaveMode) "enabled" else "disabled"}; local memories ${memory.list().size}. Remote AI backend not connected."
            }
            command == "max status" ->
                "JARVIS MAX native command layer online. Memory and device diagnostics ready. AI backend not connected."
            else -> "Unknown MAX command. Say max help."
        }
    }
}
