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
  val trace=mutableListOf<String>()
  for(index in 0 until MAX_STEPS){
   val before=JarvisAgentTools.snapshot()
   val plan=JarvisReasoning.plan(context,goal,before)
   if(plan.isEmpty()){val ok=trace.isNotEmpty();JarvisAgentMemory.record(context,goal,trace,ok);return if(ok)"Task completed after "+trace.size+" verified steps." else "I can see the screen, but I don't have a safe plan for that goal yet."}
   val step=plan.first()
   val result=JarvisAgentTools.execute(context,step)
   try{Thread.sleep(200)}catch(_:InterruptedException){Thread.currentThread().interrupt()}
   val after=JarvisAgentTools.snapshot()
   trace+="step="+(index+1)+" "+step+" => "+result+" state="+after.signature.take(300)
   if(!result.ok){JarvisAgentMemory.record(context,goal,trace,false);return "I stopped at step "+(index+1)+": "+result.message}
   if(step.requiresChange&&before.signature==after.signature){JarvisAgentMemory.record(context,goal,trace,false);return "I stopped because the screen did not change after "+step.describe()+"."}
  }
  JarvisAgentMemory.record(context,goal,trace,false)
  return "I reached the "+MAX_STEPS+" step safety limit before I could verify completion."
 }
}