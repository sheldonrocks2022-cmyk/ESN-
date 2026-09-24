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
    fun tapClickableNumber(number:Int):String=if(JarvisAccessibilityService.clickByIndex(number))"Tapped clickable item $number." else "I could not tap clickable item $number."
    fun tapNumber(number:Int):String{val root=JarvisAccessibilityService.currentRoot()?:return "Phone Access is not enabled.";val items=mutableListOf<String>();collect(root,items,0);val target=items.getOrNull(number-1)?:return "I cannot find item $number.";return tapText(target)}
    fun scrollTo(target:String):String{repeat(5){if(hasText(target))return tapText(target);if(!JarvisAccessibilityService.scrollForward())return "I could not scroll farther."};return if(hasText(target))tapText(target) else "I could not find $target after scrolling."}
    fun typeInto(label:String,value:String):String=if(JarvisAccessibilityService.setTextByLabel(label,value))"Entered text in $label." else "I could not find an editable field for $label."
    fun tapAny(vararg labels:String):Boolean { for(label in labels) if(hasText(label) && JarvisAccessibilityService.clickText(label)) return true; return false }
    fun visibleText():List<String>{val root=JarvisAccessibilityService.currentRoot()?:return emptyList();val items=mutableListOf<String>();collect(root,items,0);return items}
    fun semanticControls():List<JarvisScreenControl>{val root=JarvisAccessibilityService.currentRoot()?:return emptyList();val out=mutableListOf<JarvisScreenControl>();collectSemantic(root,out,0);return out.take(80)}
    fun screenState():String{val root=JarvisAccessibilityService.currentRoot()?:return "unavailable";val controls=semanticControls();val dialogs=controls.filter{it.role=="button"&&it.text.lowercase() in setOf("allow","deny","ok","cancel","confirm","continue","not now")};val fields=controls.count{it.editable};val toggles=controls.count{it.role=="toggle"};val scrolls=controls.count{it.role=="scroll area"};return "package="+JarvisAccessibilityService.activePackage()+" fields=$fields toggles=$toggles scrollAreas=$scrolls"+if(dialogs.isNotEmpty())" dialogActions="+dialogs.joinToString(","){it.text} else ""}
    fun tapRelative(reference:String,below:Boolean):String{val root=JarvisAccessibilityService.currentRoot()?:return "Phone Access is not enabled.";val items=mutableListOf<String>();collect(root,items,0);val i=items.indexOfFirst{it.contains(reference,true)};if(i<0)return "I couldn't find $reference.";val target=items.getOrNull(if(below)i+1 else i-1)?:return "There isn't another visible item there.";return tapText(target)}
    fun semanticSummary():String{val c=semanticControls();return if(c.isEmpty()) "No semantic controls detected." else c.take(30).joinToString("; "){it.role+": "+it.text+(if(!it.enabled)" [disabled]" else "")}}
    fun intelligenceSummary():String{val c=semanticControls();if(c.isEmpty())return "Screen Intelligence 10.0: no semantic controls available.";val enabled=c.count{it.enabled};val actionable=c.count{it.enabled&&(it.clickable||it.editable)};val roles=c.groupingBy{it.role}.eachCount().entries.sortedByDescending{it.value}.take(6).joinToString(","){it.key+"="+it.value};return "Screen Intelligence 10.0: package="+JarvisAccessibilityService.activePackage()+" controls="+c.size+" enabled="+enabled+" actionable="+actionable+" roles="+roles+"."}
    private fun synonyms(w:String)=when(w){ "play","start","listen"->setOf("play","start","listen","resume");"song","track","music"->setOf("song","track","music","audio");"send","submit"->setOf("send","submit","post");"close","dismiss"->setOf("close","dismiss","cancel");else->setOf(w)}
    fun bestMatch(target:String):JarvisScreenControl?{val words=target.lowercase().split(Regex("\\W+")).filter{it.length>1}.toSet();if(words.isEmpty())return null;return semanticControls().filter{it.enabled}.map{control->val labelWords=control.text.lowercase().split(Regex("\\W+")).toSet();val overlap=words.count{w->synonyms(w).any{it in labelWords}};val exact=if(control.text.equals(target,true))5 else if(control.text.contains(target,true)||target.contains(control.text,true))2 else 0;val role=if(control.clickable||control.editable)1 else 0;control to (overlap*2+exact+role)}.filter{it.second>0}.maxByOrNull{it.second}?.first}
    fun tapBestMatch(target:String):String{val m=bestMatch(target)?:return "I could not find a confident screen match for $target.";if(!m.enabled)return "I found "+m.text+" but it is disabled.";return if(JarvisAccessibilityService.clickText(m.text))"Tapped "+m.text+" using semantic matching." else "I found "+m.text+" but could not safely activate it."}
    fun verifyChange(before:String):String{val after=JarvisAgentTools.snapshot().signature;return if(before!=after)"Screen change verified." else "No screen change detected."}
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
