package com.esn.jarvis
import android.content.Context
import org.json.JSONObject
import java.util.Locale
object JarvisProactiveIntelligence{
 private const val PREFS="jarvis_proactive"
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun priority(c:Context,name:String,on:Boolean):String{val n=name.trim().lowercase(Locale.US);if(n.isBlank())return "Tell me which contact.";val s=p(c).getStringSet("priority_contacts",emptySet()).orEmpty().toMutableSet();if(on)s.add(n)else s.remove(n);p(c).edit().putStringSet("priority_contacts",s).apply();return if(on)"$name is now a priority contact." else "$name is no longer a priority contact."}
 fun isPriority(c:Context,title:String)=p(c).getStringSet("priority_contacts",emptySet()).orEmpty().any{title.lowercase(Locale.US).contains(it)}
 fun score(c:Context,title:String,text:String):Int{val all="$title $text".lowercase(Locale.US);var s=0;if(isPriority(c,title))s+=5;if(listOf("urgent","asap","emergency","important","call me","missed call","verification","security").any{all.contains(it)})s+=3;if(listOf("sale","promo","deal","newsletter","recommended").any{all.contains(it)})s-=2;return s}
 fun recordAlert(c:Context,reason:String){p(c).edit().putString("last_alert_reason",reason).putLong("last_alert_time",System.currentTimeMillis()).apply()}
 fun whyAlert(c:Context):String{val r=p(c).getString("last_alert_reason","").orEmpty();return if(r.isBlank())"I haven't raised a proactive alert yet." else "I alerted you because $r."}
 fun observeSequence(c:Context,command:String){val last=p(c).getString("last_command","").orEmpty();val now=command.trim().lowercase(Locale.US);if(last.isNotBlank()&&last!=now){val key="$last -> $now";val o=try{JSONObject(p(c).getString("sequences","{}"))}catch(_:Throwable){JSONObject()};o.put(key,o.optInt(key,0)+1);p(c).edit().putString("sequences",o.toString()).apply()};p(c).edit().putString("last_command",now).apply()}
 fun suggestion(c:Context):String{val o=try{JSONObject(p(c).getString("sequences","{}"))}catch(_:Throwable){JSONObject()};var best="";var count=2;val k=o.keys();while(k.hasNext()){val x=k.next();if(o.optInt(x)>count){count=o.optInt(x);best=x}};return if(best.isBlank())JarvisPersonalMemory.suggestion(c) else "I noticed you often do $best. You've done that sequence $count times. You can save it as a routine."}
 fun briefing(c:Context):String{val task=JarvisAgent.status(c);val notes=JarvisNotificationListenerService.prioritySummary(c);val recent=JarvisContext.recent(c);return listOf("Here's the priority briefing.",notes,task,if(recent.isBlank())"" else "Recent activity: $recent.").filter{it.isNotBlank()}.joinToString(" ")}
 fun learned(c:Context):String=JarvisPersonalMemory.summary(c)+" "+suggestion(c)
 fun clear(c:Context):String{p(c).edit().clear().apply();return "Proactive learning cleared."}
}