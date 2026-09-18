package com.esn.jarvis
import android.content.Context
object JarvisAgentMemory{
 fun record(context:Context,goal:String,trace:List<String>){
  val prefs=context.getSharedPreferences("jarvis_agent",Context.MODE_PRIVATE)
  val old=prefs.getString("history","").orEmpty().lineSequence().filter{it.isNotBlank()}.toList().takeLast(19).toMutableList()
  old+=(System.currentTimeMillis().toString()+"|"+goal.take(180)+"|"+trace.joinToString(" > ").take(1200))
  prefs.edit().putString("history",old.joinToString("\n")).putString("last_goal",goal.take(300)).apply()
 }
 fun lastGoal(context:Context)=context.getSharedPreferences("jarvis_agent",Context.MODE_PRIVATE).getString("last_goal","").orEmpty()
}