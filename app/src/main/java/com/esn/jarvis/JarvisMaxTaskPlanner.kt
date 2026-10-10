package com.esn.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local task planning. No tasks are executed automatically.
 * All data is app-private; the user explicitly marks tasks complete.
 */
class JarvisMaxTaskPlanner(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_max_tasks_v1", Context.MODE_PRIVATE)
    private val key = "tasks"
    private val limit = 50

    @Synchronized
    private fun read(): JSONArray = try {
        JSONArray(prefs.getString(key, "[]"))
    } catch (_: Exception) { JSONArray() }

    @Synchronized
    fun add(title: String): String {
        val name = title.trim()
        if (name.isEmpty() || name.length > 160) return "Task must be 1 to 160 characters."
        val tasks = read()
        if (tasks.length() >= limit) return "Task limit reached. Complete or remove existing tasks."
        tasks.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", name).put("done", false))
        return if (prefs.edit().putString(key, tasks.toString()).commit()) "Task added: $name" else "Task could not be saved."
    }

    @Synchronized
    fun list(): String {
        val tasks = read()
        if (tasks.length() == 0) return "No tasks yet."
        return (0 until tasks.length()).map { index ->
            val task = tasks.optJSONObject(index)
            "${index + 1}. ${if (task?.optBoolean("done") == true) "DONE" else "TODO"}: ${task?.optString("title").orEmpty()}"
        }.joinToString(". ").take(2500)
    }

    @Synchronized
    fun complete(number: Int): String {
        val tasks = read()
        if (number !in 1..tasks.length()) return "Invalid task number."
        tasks.getJSONObject(number - 1).put("done", true)
        return if (prefs.edit().putString(key, tasks.toString()).commit()) "Task $number completed." else "Task could not be updated."
    }

    @Synchronized
    fun remove(number: Int): String {
        val tasks = read()
        if (number !in 1..tasks.length()) return "Invalid task number."
        tasks.remove(number - 1)
        return if (prefs.edit().putString(key, tasks.toString()).commit()) "Task $number removed." else "Task could not be removed."
    }

    @Synchronized
    fun summary(): String {
        val tasks = read()
        val completed = (0 until tasks.length()).count { tasks.optJSONObject(it)?.optBoolean("done") == true }
        return "${tasks.length() - completed} open tasks, $completed completed."
    }
}
