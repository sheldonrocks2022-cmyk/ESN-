package com.esn.jarvis
import android.content.Context
object JarvisUnifiedAgent {
 private const val PREFS="jarvis_unified_agent"
 fun execute(c:Context,raw:String):String{
  val goal=JarvisIntelligenceCore.resolve(c,raw).trim()
  if(goal.isBlank())return "Tell me what you want me to accomplish."
  val p=c.getSharedPreferences(PREFS,0)
  p.edit().putString("goal",goal).putLong("started",System.currentTimeMillis()).putString("state","routing").apply()
  val direct=JarvisNaturalCommandRouter.execute(c,goal)
  if(direct!=null&&!looksUnresolved(direct)){record(c,goal,"direct",direct);return direct}
  val result=JarvisAgent.execute(c,goal)
  record(c,goal,"agent",result)
  return result
 }
 fun status(c:Context):String{val p=c.getSharedPreferences(PREFS,0);val g=p.getString("goal","").orEmpty();return if(g.isBlank())"Unified agent is ready." else "Unified agent: "+p.getString("state","ready").orEmpty()+". Goal: $g. "+JarvisAgent.status(c)}
 fun explain(c:Context):String{val p=c.getSharedPreferences(PREFS,0);return p.getString("last_explanation","No unified agent action has run yet.").orEmpty()}
 fun clear(c:Context):String{c.getSharedPreferences(PREFS,0).edit().clear().apply();return "Unified agent context cleared."}
 private fun record(c:Context,g:String,route:String,r:String){c.getSharedPreferences(PREFS,0).edit().putString("state","completed").putString("last_route",route).putString("last_result",r).putString("last_explanation","I routed '$g' through $route. Result: "+r.take(240)).putLong("completed",System.currentTimeMillis()).apply();JarvisIntelligenceCore.observe(c,g,r);JarvisPersonalMemory.observe(c,g)}
 private fun looksUnresolved(r:String)=r.startsWith("I don't know how",true)||r.startsWith("I couldn't understand",true)||r.startsWith("I didn't catch",true)
}
