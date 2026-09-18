package com.esn.jarvis
import android.content.Context
/**
 * Stable boundary for an on-device reasoning model.
 * The current backend is deterministic/local; a native quantized model can
 * replace it without giving the model direct Android privileges.
 */
interface JarvisReasoningEngine{
 fun plan(context:Context,goal:String,state:AgentSnapshot,history:String):List<AgentStep>
}
object JarvisOnDeviceReasoner:JarvisReasoningEngine{
 override fun plan(context:Context,goal:String,state:AgentSnapshot,history:String):List<AgentStep>{
  val modelPlan=JarvisModelRuntime.complete(context,buildPrompt(goal,state,history))?.let(::parsePlan).orEmpty()
  if(modelPlan.isNotEmpty())return modelPlan
  val plan=JarvisLocalPlanner.plan(goal,state)
  if(plan.isNotEmpty())return plan
  val g=goal.lowercase()
  val candidates=state.text.filter{it.length in 2..60}
  val words=g.split(Regex("\\W+")).filter{it.length>2}.toSet()
  val target=candidates.maxByOrNull{label->label.lowercase().split(Regex("\\W+")).count{it in words}} ?: return emptyList()
  val score=target.lowercase().split(Regex("\\W+")).count{it in words}
  return if(score>=2)listOf(AgentStep.Tap(target)) else emptyList()
 }
 private fun buildPrompt(goal:String,state:AgentSnapshot,history:String)= "Goal: "+goal+"\nPackage: "+state.packageName+"\nVisible: "+state.text.joinToString(" | ").take(5000)+"\nRecent: "+history.takeLast(1500)+"\nReturn only lines: TAP <label>, TYPE <text>, BACK, or SCROLL."
 private fun parsePlan(raw:String):List<AgentStep> = raw.lineSequence().mapNotNull { line ->\n  val s=line.trim()\n  when {\n   s.startsWith("TAP ",true) -> AgentStep.Tap(s.substring(4).trim())\n   s.startsWith("TYPE ",true) -> AgentStep.Type(s.substring(5).trim())\n   s.equals("BACK",true) -> AgentStep.Back\n   s.equals("SCROLL",true) -> AgentStep.Scroll\n   else -> null\n  }\n }.take(12).toList()
}
object JarvisReasoning{
 @Volatile var engine:JarvisReasoningEngine=JarvisOnDeviceReasoner
 fun plan(context:Context,goal:String,state:AgentSnapshot):List<AgentStep>{
  val history=context.getSharedPreferences("jarvis_agent",Context.MODE_PRIVATE).getString("history","").orEmpty().takeLast(3000)
  return engine.plan(context,goal,state,history)
 }
}
