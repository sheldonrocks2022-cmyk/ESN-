package com.esn.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
        fun currentRoot(): AccessibilityNodeInfo? = instance?.rootInActiveWindow
        fun clickText(text: String) = instance?.clickTextInternal(text) == true
        fun typeText(text: String) = instance?.typeTextInternal(text) == true
        fun back() = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun recents() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
        fun scrollForward() = instance?.scrollInternal(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        fun scrollBackward() = instance?.scrollInternal(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        fun notifications() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun quickSettings() = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    private fun clickTextInternal(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable && node.isEnabled) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.isEnabled) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
        }
        return false
    }

    private fun typeTextInternal(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun scrollInternal(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findScrollable(root) ?: return false
        return node.performAction(action)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findEditable(it)?.let { result -> return result } }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isEnabled) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findScrollable(it)?.let { result -> return result } }
        return null
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() { instance = null; super.onDestroy() }
}
