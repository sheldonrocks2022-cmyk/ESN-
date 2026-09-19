package com.esn.jarvis
import android.content.Context
import java.util.Locale
object JarvisIntelligenceCore {
 private const val PREFS="jarvis_intelligence_core"
 fun resolve(context:Context,raw:String):String{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  var text=raw.trim().lowercase(Locale.US)
  val contact=JarvisContext.recall(context,"contact")
  val app=p.getString("last_app","").orEmpty()
  if(contact.isNotBlank()) text=text.replace(Regex("\\b(him|her|them)\\b"),contact)
  if(app.isNotBlank()) text=text.replace("that app",app).replace("the app",app)
  return text
 }
 fun observe(context:Context,command:String,result:String){
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val lower=command.lowercase(Locale.US)
  val app=Regex("(?:open|launch|start) ([a-z0-9 ]{2,30})").find(lower)?.groupValues?.getOrNull(1)?.trim()
  val e=p.edit().putString("last_command",command).putString("last_result",result).putLong("updated",System.currentTimeMillis())
  if(!app.isNullOrBlank())e.putString("last_app",app)
  e.apply()
 }
 fun route(context:Context,text:String):String?{
  val t=text.trim().lowercase(Locale.US)
  if(t.startsWith("accomplish ")||t.startsWith("handle ")||t.startsWith("take care of "))return JarvisAgent.execute(context,t.substringAfter(" "))
  if(t in setOf("what did that do","did that work","did it work")){
   val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
   val r=p.getString("last_result","").orEmpty()
   return if(r.isBlank())"I don't have a recent result to verify." else "My last result was: $r Current screen: "+JarvisScreenInspector.screenState()
  }
  return null
 }
 fun status(context:Context):String{val p=context.getSharedPreferences(PREFS,0);return "Intelligence core ready. Last command: "+p.getString("last_command","none").orEmpty()+". "+JarvisAgent.status(context)}
 fun clear(context:Context):String{context.getSharedPreferences(PREFS,0).edit().clear().apply();return "Intelligence core context cleared."}
}
