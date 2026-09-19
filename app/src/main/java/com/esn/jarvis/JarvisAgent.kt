package com.esn.jarvis
import android.content.Context
object JarvisAgent {
 private const val MAX_STEPS=12
 fun execute(context:Context,goal:String):String=executeInternal(context,goal,false)
 fun executeConfirmed(context:Context,goal:String):String=executeInternal(context,goal,true)
 private fun executeInternal(context:Context,goal:String,confirmed:Boolean):String{
  if(!confirmed){val gate=JarvisAgentSafety.authorize(context,goal);if(gate!=null)return gate}
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access so I can carry out multi-step tasks."
  val plan=JarvisReasoning.plan(context,goal,JarvisAgentTools.snapshot())
  if(plan.isEmpty())return "I can see the screen, but I don't have a safe plan for that goal yet."
  val trace=mutableListOf<String>()
  for((index,step) in plan.take(MAX_STEPS).withIndex()){
   val before=JarvisAgentTools.snapshot()
   val result=JarvisAgentTools.execute(context,step)
   Thread.sleep(350)
   val after=JarvisAgentTools.snapshot()
   trace+="step="+(index+1)+" "+step+" => "+result
   if(!result.ok){JarvisAgentMemory.record(context,goal,trace,false);return "I stopped at step "+(index+1)+": "+result.message}
   if(step.requiresChange && before.signature==after.signature){JarvisAgentMemory.record(context,goal,trace,false);return "I stopped because the screen did not change after "+step.describe()+"."}
  }
  JarvisAgentMemory.record(context,goal,trace,true)
  return "I stopped after "+MAX_STEPS+" verified steps so the task could not loop indefinitely."
 }
}