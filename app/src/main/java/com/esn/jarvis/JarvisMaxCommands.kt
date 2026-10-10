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
        val tasks = JarvisMaxTaskPlanner(context)
        val focus = JarvisMaxFocus(context)
        JarvisMaxUtilities.execute(context, command, original)?.let { return it }
        JarvisMaxTextTools.execute(command, original)?.let { return it }
        JarvisMaxConversions.execute(command, original)?.let { return it }
        JarvisMaxMath.execute(command, original)?.let { return it }
        return when {
            command == "max ai status" -> JarvisMaxAISettings.status(context)
            command == "max conversation" -> JarvisMaxConversation(context).summary()
            command == "max clear conversation confirm" -> {
                if (JarvisMaxConversation(context).clear()) "Conversation history cleared." else "Could not clear history."
            }
            command == "max clear conversation" -> "Say max clear conversation confirm to delete local conversation history."
            command.startsWith("max note conversation ") -> {
                val note = original.drop("max note conversation ".length).trim()
                if (JarvisMaxConversation(context).append("user", note)) "Conversation note saved locally." else "Note must be 1 to 2000 characters."
            }
            command == "max approvals" -> JarvisMaxApprovals(context).pending()
            command.startsWith("max request approval ") ->
                JarvisMaxApprovals(context).request(original.drop("max request approval ".length))
            command.startsWith("max approve ") ->
                JarvisMaxApprovals(context).decide(command.removePrefix("max approve "), true)
            command.startsWith("max deny ") ->
                JarvisMaxApprovals(context).decide(command.removePrefix("max deny "), false)
            command == "max briefing" || command == "max daily briefing" -> JarvisMaxBriefing.generate(context)
            command == "max focus status" -> focus.status()
            command == "max focus stop" -> focus.stop()
            command.startsWith("max focus start ") -> {
                val minutes = command.removePrefix("max focus start ").trim().toIntOrNull()
                if (minutes == null) "Specify focus minutes between 1 and 180." else focus.start(minutes)
            }
            command == "max tasks" -> tasks.list()
            command == "max task summary" -> tasks.summary()
            command.startsWith("max add task ") -> tasks.add(original.drop("max add task ".length))
            command.startsWith("max complete task ") -> {
                val number = command.removePrefix("max complete task ").trim().toIntOrNull()
                if (number == null) "Specify a task number." else tasks.complete(number)
            }
            command.startsWith("max remove task ") -> {
                val number = command.removePrefix("max remove task ").trim().toIntOrNull()
                if (number == null) "Specify a task number." else tasks.remove(number)
            }
            command == "max help" || command == "max commands" ->
                "JARVIS MAX: status, diagnostics, battery, memories, memory stats, find memory followed by keywords, remember followed by text, forget followed by number, forget all confirm, tasks, task summary, add task, complete task remove task, focus start, focus status, focus stop, briefing, ai status, conversation, note conversation, clear conversation, approvals, request approval, approve, deny, device, storage, network, volume, timezone, language, uptime, secure lock, accessibility, uuid, word count, reverse, uppercase, lowercase, sha256, and 40 additional offline text tools, 40 offline unit conversions, and 40 offline math tools."
            command.startsWith("max remember ") -> {
                val note = original.drop("max remember ".length).trim()
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
                val query = original.drop("max find memory ".length).trim()
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
