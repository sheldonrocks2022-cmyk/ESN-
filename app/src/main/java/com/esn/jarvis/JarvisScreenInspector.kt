package com.esn.jarvis

import android.view.accessibility.AccessibilityNodeInfo

data class JarvisScreenControl(val text:String,val role:String,val enabled:Boolean,val clickable:Boolean,val editable:Boolean,val checked:Boolean)

object JarvisScreenInspector {
    fun describeScreen(): String {
        val root = JarvisAccessibilityService.currentRoot() ?: return "Phone Access is not enabled or there is no active screen."
        val items = mutableListOf<String>()
        collect(root, items, 0)
        if (items.isEmpty()) return "I can access the screen, but I can't find readable controls on it."
        val shown = items.take(18)
        return "The current screen contains: " + shown.joinToString("; ") + if (items.size > shown.size) ". There are more controls below." else "."
    }
    fun hasText(target:String):Boolean { val root=JarvisAccessibilityService.currentRoot()?:return false;return find(root,target.lowercase(),0) }
    fun tapText(target:String):String = if(hasText(target) && JarvisAccessibilityService.clickText(target)) "Tapped $target." else "I couldn't find $target on the current screen."
    fun listControls():String{val root=JarvisAccessibilityService.currentRoot()?:return "Phone Access is not enabled.";val items=mutableListOf<String>();collect(root,items,0);return if(items.isEmpty())"I cannot find controls." else "Visible controls: "+items.take(30).mapIndexed{index,value->"${index+1}. $value"}.joinToString("; ")}
    fun tapNumber(number:Int):String{val root=JarvisAccessibilityService.currentRoot()?:return "Phone Access is not enabled.";val items=mutableListOf<String>();collect(root,items,0);val target=items.getOrNull(number-1)?:return "I cannot find item $number.";return tapText(target)}
    fun scrollTo(target:String):String{repeat(5){if(hasText(target))return tapText(target);if(!JarvisAccessibilityService.scrollForward())return "I could not scroll farther."};return if(hasText(target))tapText(target) else "I could not find $target after scrolling."}
    fun typeInto(label:String,value:String):String=if(JarvisAccessibilityService.setTextByLabel(label,value))"Entered text in $label." else "I could not find an editable field for $label."
    fun tapAny(vararg labels:String):Boolean { for(label in labels) if(hasText(label) && JarvisAccessibilityService.clickText(label)) return true; return false }
    fun visibleText():List<String>{val root=JarvisAccessibilityService.currentRoot()?:return emptyList();val items=mutableListOf<String>();collect(root,items,0);return items}
    fun semanticControls():List<JarvisScreenControl>{val root=JarvisAccessibilityService.currentRoot()?:return emptyList();val out=mutableListOf<JarvisScreenControl>();collectSemantic(root,out,0);return out.take(80)}
    fun semanticSummary():String{val c=semanticControls();return if(c.isEmpty()) "No semantic controls detected." else c.take(30).joinToString("; "){it.role+": "+it.text+(if(!it.enabled)" [disabled]" else "")}}
    private fun collectSemantic(n:AccessibilityNodeInfo,out:MutableList<JarvisScreenControl>,depth:Int){if(depth>14||out.size>=100)return;val text=(n.text?.toString()?.trim().takeUnless{it.isNullOrBlank()}?:n.contentDescription?.toString()?.trim()).orEmpty();val role=when{n.isEditable->"text field";n.isCheckable->"toggle";n.isClickable->"button";n.isScrollable->"scroll area";n.className?.toString()?.contains("Image",true)==true->"image";else->"text"};if(text.isNotBlank()&&(n.isClickable||n.isEditable||n.isCheckable||n.isScrollable))out+=JarvisScreenControl(text.take(160),role,n.isEnabled,n.isClickable,n.isEditable,n.isChecked);for(i in 0 until n.childCount)n.getChild(i)?.let{collectSemantic(it,out,depth+1)}}

    private fun find(node:AccessibilityNodeInfo,target:String,depth:Int):Boolean { if(depth>12)return false;val value=(node.text?.toString().orEmpty()+" "+node.contentDescription?.toString().orEmpty()).lowercase();if(value.contains(target))return true;for(i in 0 until node.childCount)if(node.getChild(i)?.let{find(it,target,depth+1)}==true)return true;return false }
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size >= 40) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val value = if (text.isNotBlank()) text else desc
        if (value.isNotBlank() && value.length <= 120 && value !in out) out += value
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, depth + 1) }
    }
}
