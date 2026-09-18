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
  val plan=JarvisLocalPlanner.plan(goal,state)
  if(plan.isNotEmpty())return plan
  val g=goal.lowercase()
  val candidates=state.text.filter{it.length in 2..60}
  val words=g.split(Regex("\\W+")).filter{it.length>2}.toSet()
  val target=candidates.maxByOrNull{label->label.lowercase().split(Regex("\\W+")).count{it in words}} ?: return emptyList()
  val score=target.lowercase().split(Regex("\\W+")).count{it in words}
  return if(score>=2)listOf(AgentStep.Tap(target)) else emptyList()
 }
}
object JarvisReasoning{
 @Volatile var engine:JarvisReasoningEngine=JarvisOnDeviceReasoner
 fun plan(context:Context,goal:String,state:AgentSnapshot):List<AgentStep>{
  val history=context.getSharedPreferences("jarvis_agent",Context.MODE_PRIVATE).getString("history","").orEmpty().takeLast(3000)
  return engine.plan(context,goal,state,history)
 }
}
