package com.esn.jarvis
import android.content.Context
object JarvisAgent {
 private const val MAX_STEPS=12
 fun execute(context:Context,goal:String):String{
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access so I can carry out multi-step tasks."
  val plan=JarvisLocalPlanner.plan(goal,JarvisAgentTools.snapshot())
  if(plan.isEmpty())return "I can see the screen, but I don't have a safe plan for that goal yet."
  val trace=mutableListOf<String>()
  for((index,step) in plan.take(MAX_STEPS).withIndex()){
   val before=JarvisAgentTools.snapshot()
   val result=JarvisAgentTools.execute(context,step)
   val after=JarvisAgentTools.snapshot()
   trace+="step="+(index+1)+" "+step+" => "+result
   if(!result.ok)return "I stopped at step "+(index+1)+": "+result.message
   if(step.requiresChange && before.signature==after.signature)return "I stopped because the screen did not change after "+step.describe()+"."
  }
  JarvisAgentMemory.record(context,goal,trace)
  return "Goal completed through "+plan.size.coerceAtMost(MAX_STEPS)+" verified agent step"+if(plan.size==1) "." else "s."
 }
}