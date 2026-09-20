package com.esn.jarvis
import android.content.Context
object JarvisAutomationEngine {
 private const val PREFS="jarvis_automation"
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun setTrigger(c:Context,event:String,command:String):String{if(event.isBlank()||command.isBlank())return "I need both a trigger and an action.";p(c).edit().putString("rule:"+event.lowercase(),command).apply();return "Automation saved for $event."}
 fun fire(c:Context,event:String):String?{
  val prefs=p(c);if(prefs.getBoolean("paused",false)||!c.getSharedPreferences("jarvis",0).getBoolean("active",false))return null
  val key="rule:"+event.lowercase();val command=prefs.getString(key,"").orEmpty();if(command.isBlank())return null
  val now=System.currentTimeMillis();val last=prefs.getLong("fired:"+key,0);if(now-last<60000)return null
  if(isSensitive(command)){prefs.edit().putString("pending_event",event).putString("pending_command",command).putString("last_reason","$event requires confirmation").apply();return "Automation $event is ready but requires confirmation."}
  val result=JarvisNaturalCommandRouter.execute(c,command)?:JarvisCommandEngine.execute(c,command)
  record(c,event,command,result);prefs.edit().putLong("fired:"+key,now).apply();return result
 }
 fun confirm(c:Context):String{val prefs=p(c);val cmd=prefs.getString("pending_command","").orEmpty();val event=prefs.getString("pending_event","").orEmpty();if(cmd.isBlank())return "There is no pending automation.";prefs.edit().remove("pending_command").remove("pending_event").apply();val result=JarvisNaturalCommandRouter.execute(c,cmd)?:JarvisCommandEngine.execute(c,cmd);record(c,event,cmd,result);return result}
 fun pause(c:Context,on:Boolean):String{p(c).edit().putBoolean("paused",on).apply();return if(on)"Automations paused." else "Automations resumed."}
 fun why(c:Context):String=p(c).getString("last_reason","No automation has run yet.").orEmpty()
 fun history(c:Context):String{val h=p(c).getString("history","").orEmpty().lines().filter{it.isNotBlank()}.takeLast(8);return if(h.isEmpty())"No automation history yet." else "Recent automations: "+h.joinToString(" | ")}
 fun list(c:Context):String{val keys=p(c).all.keys.filter{it.startsWith("rule:")}.map{it.removePrefix("rule:")}.sorted();return if(keys.isEmpty())"No event automations are configured." else "Automation triggers: "+keys.joinToString(", ")+"."}
 private fun record(c:Context,event:String,cmd:String,result:String){val prefs=p(c);val old=prefs.getString("history","").orEmpty().lines().filter{it.isNotBlank()}.takeLast(29);val line=System.currentTimeMillis().toString()+"|"+event+"|"+cmd+"|"+result.take(160);prefs.edit().putString("history",(old+line).joinToString("\n")).putString("last_event",event).putString("last_reason","Rule '$event' ran '$cmd'.").apply()}
 private fun isSensitive(cmd:String):Boolean{val x=cmd.lowercase();return listOf("ban ","kick ","delete ","send a message","text ","call ","factory","purchase","pay ").any{x.contains(it)}}
}
