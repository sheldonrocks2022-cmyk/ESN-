package com.esn.jarvis
import android.content.Context
object JarvisAgent {
 private const val MAX_STEPS=20
 fun execute(context:Context,goal:String):String=executeInternal(context,resolveGoal(context,goal),false)
 fun executeConfirmed(context:Context,goal:String):String=executeInternal(context,resolveGoal(context,goal),true)
 private fun resolveGoal(context:Context,raw:String):String{
  val p=context.getSharedPreferences("jarvis_agent_context",Context.MODE_PRIVATE)
  val g=raw.trim()
  val previous=p.getString("goal","").orEmpty()
  val follow=g.lowercase().startsWith("then ")||g.lowercase().startsWith("now ")||g.lowercase().startsWith("after that ")||g.lowercase() in setOf("do that","continue","keep going")
  val resolved=if(follow&&previous.isNotBlank()) previous+"; "+g else g
  if(resolved.isNotBlank())p.edit().putString("goal",resolved).putLong("updated",System.currentTimeMillis()).apply()
  return resolved
 }
 private fun executeInternal(context:Context,goal:String,confirmed:Boolean):String{
  if(!confirmed){val gate=JarvisAgentSafety.authorize(context,goal);if(gate!=null)return gate}
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access so I can carry out multi-step tasks."
  val trace=mutableListOf<String>()
  var stalled=0
  for(index in 0 until MAX_STEPS){
   val before=JarvisAgentTools.snapshot()
   val plan=JarvisReasoning.plan(context,goal,before)
   if(plan.isEmpty()){val ok=trace.isNotEmpty();JarvisAgentMemory.record(context,goal,trace,ok);return if(ok)"Task completed after "+trace.size+" verified steps." else "I can see the screen, but I don't have a safe plan for that goal yet."}
   val step=plan.first()
   var result=JarvisAgentTools.execute(context,step)
   try{Thread.sleep(450)}catch(_:InterruptedException){Thread.currentThread().interrupt()}
   var after=JarvisAgentTools.snapshot()
   if(!result.ok){
    // Self-recovery: scroll once for missing controls, then back up once if still blocked.
    if(step is AgentStep.Tap && JarvisAccessibilityService.scrollForward()){
     try{Thread.sleep(350)}catch(_:InterruptedException){Thread.currentThread().interrupt()}
     result=JarvisAgentTools.execute(context,step)
     after=JarvisAgentTools.snapshot()
     trace+="recovery scroll for "+step.describe()+" => "+result.message
    }
    if(!result.ok && step is AgentStep.Tap && JarvisAccessibilityService.back()){
     try{Thread.sleep(350)}catch(_:InterruptedException){Thread.currentThread().interrupt()}
     trace+="recovery back after missing "+step.label
     continue
    }
   }
   trace+="step="+(index+1)+" "+step+" => "+result+" state="+after.signature.take(300)
   if(!result.ok){JarvisAgentMemory.record(context,goal,trace,false);return "I stopped at step "+(index+1)+": "+result.message+" I tried screen recovery first."}
   if(step.requiresChange&&before.signature==after.signature){
    stalled++
    if(stalled<=2){if(JarvisAccessibilityService.scrollForward()||JarvisAccessibilityService.back())continue}
    JarvisAgentMemory.record(context,goal,trace,false);return "I stopped because the screen did not change after "+step.describe()+", even after recovery."
   } else stalled=0
  }
  JarvisAgentMemory.record(context,goal,trace,false)
  return "I reached the "+MAX_STEPS+" step safety limit before I could verify completion."
 }
}