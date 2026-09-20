package com.esn.jarvis
import android.content.Context
object JarvisAgent {
 private const val MAX_STEPS=24
 private const val PREFS="jarvis_agent_task"
 @Volatile private var abortRequested=false
 fun execute(context:Context,goal:String):String=executeInternal(context,resolveGoal(context,goal),false)
 fun executeConfirmed(context:Context,goal:String):String=executeInternal(context,resolveGoal(context,goal),true)
 fun resume(context:Context):String{val g=context.getSharedPreferences(PREFS,0).getString("goal","").orEmpty();return if(g.isBlank())"There is no agent task to resume." else execute(context,g)}
 fun stop(context:Context):String{abortRequested=true;context.getSharedPreferences(PREFS,0).edit().putBoolean("running",false).putString("status","stopped").apply();return "Stopped the current agent task."}
 fun status(context:Context):String{val p=context.getSharedPreferences(PREFS,0);val g=p.getString("goal","").orEmpty();if(g.isBlank())return "There is no active agent task.";val step=p.getInt("step",0);val status=p.getString("status","idle").orEmpty();return "Agent task: $g. Status: $status. Step $step."}
 private fun resolveGoal(context:Context,raw:String):String{val p=context.getSharedPreferences("jarvis_agent_context",0);val g=raw.trim();val previous=p.getString("goal","").orEmpty();val follow=g.lowercase().startsWith("then ")||g.lowercase().startsWith("now ")||g.lowercase().startsWith("after that ")||g.lowercase() in setOf("do that","continue","keep going");val resolved=if(follow&&previous.isNotBlank())previous+"; "+g else g;if(resolved.isNotBlank())p.edit().putString("goal",resolved).putLong("updated",System.currentTimeMillis()).apply();return resolved}
 private fun executeInternal(context:Context,goal:String,confirmed:Boolean):String{
  if(!confirmed){val gate=JarvisAgentSafety.authorize(context,goal);if(gate!=null)return gate}
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  if(!JarvisAccessibilityService.isEnabled(context))return "Enable Phone Access so I can carry out multi-step tasks."
  if(!JarvisAccessibilityService.hasAccess())return "Phone Access is enabled, but Android has not connected it yet. Reopen Phone Access or restart JARVIS, then try again."
  abortRequested=false
  val task=context.getSharedPreferences(PREFS,0);task.edit().putString("goal",goal).putBoolean("running",true).putString("status","planning").putInt("step",0).apply()
  val trace=mutableListOf<String>();val learned=JarvisAgentMemory.relevant(context,goal);if(learned.isNotBlank())trace+="memory guidance: "+learned.take(700);var stalled=0
  for(index in 0 until MAX_STEPS){
   if(abortRequested){JarvisAgentMemory.record(context,goal,trace,false);return "Task stopped."}
   task.edit().putInt("step",index+1).putString("status","inspecting").apply()
   val before=JarvisAgentTools.snapshot();val plan=JarvisReasoning.plan(context,goal,before)
   if(plan.isEmpty()){val ok=trace.isNotEmpty();task.edit().putBoolean("running",false).putString("status",if(ok)"completed" else "no safe plan").apply();JarvisAgentMemory.record(context,goal,trace,ok);return if(ok)"Task completed after "+trace.size+" verified steps." else "I can see the screen, but I don't have a safe plan for that goal yet."}
   val step=plan.first();task.edit().putString("status","executing: "+step.describe()).apply()
   var result=JarvisAgentTools.execute(context,step);try{Thread.sleep(450)}catch(_:InterruptedException){Thread.currentThread().interrupt()};var after=JarvisAgentTools.snapshot()
   if(!result.ok&&step is AgentStep.Tap&&JarvisAccessibilityService.scrollForward()){try{Thread.sleep(350)}catch(_:InterruptedException){Thread.currentThread().interrupt()};result=JarvisAgentTools.execute(context,step);after=JarvisAgentTools.snapshot();trace+="recovery scroll for "+step.describe()+" => "+result.message}
   if(!result.ok&&step is AgentStep.Tap&&JarvisAccessibilityService.back()){try{Thread.sleep(350)}catch(_:InterruptedException){Thread.currentThread().interrupt()};trace+="recovery back after missing "+step.label;continue}
   trace+="step="+(index+1)+" "+step+" => "+result+" state="+after.signature.take(300)
   task.edit().putString("last_step",step.describe()).putString("last_package",after.packageName).apply()
   if(!result.ok){task.edit().putBoolean("running",false).putString("status","failed: "+result.message).apply();JarvisAgentMemory.record(context,goal,trace,false);return "I stopped at step "+(index+1)+": "+result.message+" I tried screen recovery first."}
   if(step.requiresChange&&before.signature==after.signature){stalled++;if(stalled<=2&&(JarvisAccessibilityService.scrollForward()||JarvisAccessibilityService.back()))continue;task.edit().putBoolean("running",false).putString("status","stalled").apply();JarvisAgentMemory.record(context,goal,trace,false);return "I stopped because the screen did not change after "+step.describe()+", even after recovery."}else stalled=0
  }
  task.edit().putBoolean("running",false).putString("status","safety limit reached").apply();JarvisAgentMemory.record(context,goal,trace,false);return "I reached the "+MAX_STEPS+" step safety limit before I could verify completion."
 }
}