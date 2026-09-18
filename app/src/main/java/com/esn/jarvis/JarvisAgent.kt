package com.esn.jarvis
import android.content.Context
object JarvisAgent {
 private const val MAX_STEPS=8
 fun execute(context:Context,goal:String):String{
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access so I can carry out multi-step tasks."
  val trace=mutableListOf<String>()
  repeat(MAX_STEPS){n->
   val screen=JarvisScreenInspector.visibleText().take(80)
   val action=plan(goal,screen,trace)
   if(action is Action.Done)return action.message
   val result=run(action)
   trace+="step="+(n+1)+"; action="+action+"; result="+result
   if(result.startsWith("FAILED:"))return result.removePrefix("FAILED:")
  }
  return "I reached my safe step limit before finishing. I stopped instead of guessing."
 }
 private sealed interface Action{data class Tap(val label:String):Action;data class Type(val text:String):Action;data object Back:Action;data class Done(val message:String):Action}
 private fun plan(goal:String,screen:List<String>,trace:List<String>):Action{
  val g=goal.lowercase()
  if(trace.isEmpty()){
   val quoted=Regex("[\"']([^\"']+)[\"']").find(goal)?.groupValues?.getOrNull(1)
   if(g.startsWith("tap ")||g.startsWith("click "))return Action.Tap(goal.substringAfter(" ").trim())
   if(g.startsWith("type ")||g.startsWith("enter "))return Action.Type(goal.substringAfter(" ").trim())
   if(quoted!=null&&screen.any{it.contains(quoted,true)})return Action.Tap(quoted)
   val target=screen.firstOrNull{label->g.contains(label.lowercase())&&label.length in 2..50}
   if(target!=null)return Action.Tap(target)
  }
  return Action.Done(if(trace.isEmpty())"I can see the screen, but I don't have a safe plan for that goal yet." else "I completed the available safe steps for that goal.")
 }
 private fun run(a:Action):String=when(a){
  is Action.Tap->if(JarvisAccessibilityService.clickText(a.label))"OK" else "FAILED:I couldn't find "+a.label+" on the current screen."
  is Action.Type->if(JarvisAccessibilityService.typeText(a.text))"OK" else "FAILED:I couldn't find an editable field."
  Action.Back->if(JarvisAccessibilityService.back())"OK" else "FAILED:I couldn't go back."
  is Action.Done->a.message
 }
}