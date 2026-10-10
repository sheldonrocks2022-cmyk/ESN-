package com.esn.jarvis

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** One pending approval; requests expire after five minutes.
 * This component NEVER executes external actions by itself.
 */
class JarvisMaxApprovals(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_max_approvals", Context.MODE_PRIVATE)
    private val key = "pending"
    @Synchronized fun request(description: String): String {
        val detail = description.trim()
        if (detail.isBlank() || detail.length > 300) return "Describe an action in 1 to 300 characters."
        val id = UUID.randomUUID().toString().take(8)
        val record = JSONObject().put("id", id).put("description", detail)
            .put("expires", System.currentTimeMillis() + 300_000L)
        prefs.edit().putString(key, record.toString()).apply()
        return "Approval request $id pending: $detail. This is a review-only request. No action will run."
    }
    @Synchronized fun pending(): String {
        val record = current() ?: return "No active approval request."
        return "Pending ${record.optString("id")}: ${record.optString("description")}. No action will run."
    }
    @Synchronized fun decide(id: String, approve: Boolean): String {
        val record = current() ?: return "No active approval request."
        if (record.optString("id") != id.trim()) return "Approval ID does not match."
        prefs.edit().remove(key).apply()
        return if (approve) "Request approved for review only. External execution is not connected."
            else "Request denied and cleared."
    }
    private fun current(): JSONObject? {
        val record = try { JSONObject(prefs.getString(key, "{}") ?: "{}") } catch (_: Exception) { return null }
        if (record.optLong("expires") <= System.currentTimeMillis()) {
            prefs.edit().remove(key).apply()
            return null
        }
        return record
    }
}
