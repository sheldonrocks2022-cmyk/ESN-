package com.esn.jarvis

import android.view.accessibility.AccessibilityNodeInfo

object JarvisScreenInspector {
    fun describeScreen(): String {
        val root = JarvisAccessibilityService.currentRoot() ?: return "Phone Access is not enabled or there is no active screen."
        val items = mutableListOf<String>()
        collect(root, items, 0)
        if (items.isEmpty()) return "I can access the screen, but I can't find readable controls on it."
        val shown = items.take(18)
        return "The current screen contains: " + shown.joinToString("; ") + if (items.size > shown.size) ". There are more controls below." else "."
    }
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size >= 40) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val value = if (text.isNotBlank()) text else desc
        if (value.isNotBlank() && value.length <= 120 && value !in out) out += value
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, depth + 1) }
    }
}
