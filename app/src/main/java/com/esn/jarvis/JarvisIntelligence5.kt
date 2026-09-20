package com.esn.jarvis
import android.content.Context
import java.util.Locale
object JarvisIntelligence5 {
 private const val PREFS="jarvis_intelligence_5"
 fun resolve(c:Context,raw:String):String{
  var t=raw.trim().lowercase(Locale.US)
  val p=c.getSharedPreferences(PREFS,0)
  val last=p.getString("last_goal","").orEmpty()
  if(last.isNotBlank()&&t in setOf("do it","do that","try again","retry","continue"))t=last
  return JarvisIntelligenceCore.resolve(c,t)
 }
 fun execute(c:Context,raw:String):String{
  val goal=resolve(c,raw)
  if(goal.isBlank())return "Tell me what you want me to do."
  val p=c.getSharedPreferences(PREFS,0)
  p.edit().putString("last_goal",goal).putLong("updated",System.currentTimeMillis()).apply()
  val direct=JarvisNaturalCommandRouter.execute(c,goal)
  if(direct!=null&&!unresolved(direct)){record(c,goal,"direct",direct);return direct}
  val result=JarvisAgent.execute(c,goal)
  record(c,goal,"agent",result)
  return result
 }
 fun verify(c:Context):String{val p=c.getSharedPreferences(PREFS,0);val result=p.getString("last_result","").orEmpty();return if(result.isBlank())"I have no recent Intelligence 5.0 action to verify." else "Last result: $result Current screen: "+JarvisScreenInspector.screenState()}
 fun explain(c:Context):String=c.getSharedPreferences(PREFS,0).getString("explanation","No Intelligence 5.0 decision has run yet.").orEmpty()
 fun status(c:Context):String{val p=c.getSharedPreferences(PREFS,0);return "Intelligence 5.0 ready. Last route: "+p.getString("route","none").orEmpty()+". "+JarvisUnifiedAgent.status(c)}
 private fun record(c:Context,g:String,route:String,r:String){c.getSharedPreferences(PREFS,0).edit().putString("route",route).putString("last_result",r).putString("explanation","I interpreted the goal as '$g', selected $route execution, and received: "+r.take(220)).putLong("completed",System.currentTimeMillis()).apply();JarvisIntelligenceCore.observe(c,g,r);JarvisPersonalMemory.observe(c,g)}
 private fun unresolved(r:String)=r.startsWith("I don't know how",true)||r.startsWith("I couldn't understand",true)||r.startsWith("I didn't catch",true)||r.contains("don't have a safe plan",true)
}
