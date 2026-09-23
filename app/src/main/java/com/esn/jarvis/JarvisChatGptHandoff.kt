package com.esn.jarvis

import android.content.Context
import android.content.Intent

object JarvisChatGptHandoff {
    private const val PACKAGE = "com.openai.chatgpt"

    fun handoff(context: Context, request: String): String {
        val task = request.trim()
        if (task.isBlank()) return "Tell me what you want me to hand off to ChatGPT."
        val payload = "JARVIS handoff request: $task"
        return try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(PACKAGE)
                putExtra(Intent.EXTRA_TEXT, payload)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(share)
            context.getSharedPreferences("jarvis_chatgpt_handoff", Context.MODE_PRIVATE).edit()
                .putString("last_request", task)
                .putLong("last_handoff", System.currentTimeMillis())
                .apply()
            "I opened ChatGPT with your request ready to hand off. Review and send it in the logged-in ChatGPT app."
        } catch (_: Throwable) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                    ?: return "I couldn't find the ChatGPT app installed on this phone."
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "I opened ChatGPT, but Android could not pass the request directly. Your ChatGPT login remains controlled by the ChatGPT app."
            } catch (_: Throwable) {
                "I couldn't open ChatGPT."
            }
        }
    }

    fun status(context: Context): String {
        val p = context.getSharedPreferences("jarvis_chatgpt_handoff", Context.MODE_PRIVATE)
        val last = p.getString("last_request", "").orEmpty()
        return if (last.isBlank()) "No ChatGPT handoff has been prepared yet."
        else "The most recent ChatGPT handoff was: $last"
    }
}
