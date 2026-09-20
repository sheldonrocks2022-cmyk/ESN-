package com.esn.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
        fun currentRoot(): AccessibilityNodeInfo? = instance?.rootInActiveWindow
        fun clickText(text: String) = instance?.clickTextInternal(text) == true
        fun typeText(text: String) = instance?.typeTextInternal(text) == true
        fun setTextByLabel(label:String,text:String)=instance?.setTextByLabelInternal(label,text)==true
        fun back() = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun recents() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
        fun notifications() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun quickSettings() = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true
        fun scrollForward() = instance?.scrollInternal(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        fun scrollBackward() = instance?.scrollInternal(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        fun focusText(text:String)=instance?.focusTextInternal(text)==true
        fun isEnabled(context:Context):Boolean { val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty(); val mine=ComponentName(context,JarvisAccessibilityService::class.java).flattenToString(); return enabled.split(":").any{it.equals(mine,true)} }
        fun hasAccess()=instance!=null
        fun hasAccess(context:Context)=instance!=null || isEnabled(context)
        fun activePackage()=instance?.rootInActiveWindow?.packageName?.toString().orEmpty()
        fun clickByIndex(index:Int)=instance?.clickByIndexInternal(index)==true
        fun deviceControlStatus(context:Context?=null)=when { instance!=null -> "Phone Access ready in "+activePackage()+". Screen: "+JarvisScreenInspector.screenState(); context!=null && isEnabled(context) -> "Phone Access is enabled, but Android has not connected the service yet. Reopen Phone Access or restart JARVIS."; else -> "Phone Access is off." }
    }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    private fun clickByIndexInternal(index:Int):Boolean{val root=rootInActiveWindow?:return false;val nodes=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo){if(n.isClickable&&n.isEnabled)nodes+=n;for(i in 0 until n.childCount)n.getChild(i)?.let(::walk)};walk(root);return nodes.getOrNull(index-1)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true}
    private fun clickTextInternal(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isClickable && node.isEnabled) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.isEnabled) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
        }
        return false
    }
    private fun focusTextInternal(text:String):Boolean{val root=rootInActiveWindow?:return false;for(node in root.findAccessibilityNodeInfosByText(text)){if(node.isFocusable&&node.isEnabled)return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)};return false}
    private fun setTextByLabelInternal(label:String,text:String):Boolean{val root=rootInActiveWindow?:return false;val q=label.lowercase();fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo?{val v=(n.text?.toString().orEmpty()+" "+n.contentDescription?.toString().orEmpty()).lowercase();if(n.isEditable&&(q.isBlank()||v.contains(q)))return n;for(i in 0 until n.childCount)n.getChild(i)?.let{find(it)?.let{return it}};return null};val node=find(root)?:findEditable(root)?:return false;return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)})}
    private fun typeTextInternal(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findEditable(root) ?: return false
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
    }
    private fun scrollInternal(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollable(root)?.performAction(action) == true
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
