package com.esn.jarvis

import android.content.Context

/** Safe AI integration readiness checks. Secrets must be held server-side. */
object JarvisMaxAISettings {
    fun status(context: Context): String {
        val localModel = JarvisModelManager.installedSize(context)
        return "AI readiness: local model $localModel. JARVIS MAX cloud gateway is not configured. Never embed API keys in an Android APK."
    }
}
