package com.esn.jarvis

import android.content.Context

object JarvisMaxCommands {
    fun execute(context: Context, raw: String): String? {
        val command = raw.trim().lowercase()
        val memory = JarvisMaxMemory(context)
        return when {
            command.startsWith("max remember ") -> {
                val note = raw.trim().drop(13).trim()
                if (memory.remember(note)) "Saved locally." else "Memory not saved. Limit 500 characters."
            }
            command == "max memories" -> {
                val items = memory.list()
                if (items.isEmpty()) "No JARVIS MAX memories."
                else items.mapIndexed { index, note -> "${index + 1}. $note" }.joinToString(". ").take(2000)
            }
            command.startsWith("max forget ") -> {
                val number = command.removePrefix("max forget ").trim().toIntOrNull()
                if (number != null && memory.forget(number - 1)) "Memory deleted." else "Invalid memory number."
            }
            command == "max status" -> "JARVIS MAX local memory ready. AI backend not connected."
            else -> null
        }
    }
}
