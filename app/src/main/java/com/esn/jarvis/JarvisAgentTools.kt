package com.esn.jarvis
import android.content.Context
data class AgentSnapshot(val packageName:String,val text:List<String>){val signature:String get()=packageName+"|"+text.joinToString("|")}
sealed interface AgentStep{
 val requiresChange:Boolean
 fun describe():String
 data class Tap(val label:String):AgentStep{override val requiresChange=true;override fun describe()="tap "+label}
 data class Type(val value:String):AgentStep{override val requiresChange=false;override fun describe()="type text"}
 data object Back:AgentStep{override val requiresChange=true;override fun describe()="go back"}
 data object Scroll:AgentStep{override val requiresChange=true;override fun describe()="scroll"}
}
data class AgentResult(val ok:Boolean,val message:String)
object JarvisAgentTools{
 fun snapshot()=AgentSnapshot(JarvisAccessibilityService.activePackage(),JarvisScreenInspector.visibleText().take(80))
 fun execute(context:Context,step:AgentStep):AgentResult=when(step){
  is AgentStep.Tap->result(JarvisAccessibilityService.clickText(step.label),"Tapped "+step.label,"I couldn't find "+step.label+".")
  is AgentStep.Type->result(JarvisAccessibilityService.typeText(step.value),"Entered text.","I couldn't find an editable field.")
  AgentStep.Back->result(JarvisAccessibilityService.back(),"Went back.","I couldn't go back.")
  AgentStep.Scroll->result(JarvisAccessibilityService.scrollForward(),"Scrolled.","I couldn't scroll farther.")
 }
 private fun result(ok:Boolean,yes:String,no:String)=AgentResult(ok,if(ok)yes else no)
}