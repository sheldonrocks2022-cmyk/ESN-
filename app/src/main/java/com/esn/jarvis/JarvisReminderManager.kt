package com.esn.jarvis
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
object JarvisReminderManager{
 private const val PREFS="jarvis_reminder_manager"
 fun record(c:Context,id:Int,m:String,at:Long){c.getSharedPreferences(PREFS,0).edit().putString(id.toString(),"$at|$m").apply()}
 fun list(c:Context):String{val now=System.currentTimeMillis();val e=c.getSharedPreferences(PREFS,0).all.mapNotNull{(id,v)->val p=(v as? String)?.split("|",limit=3)?:return@mapNotNull null;val at=p[0].toLongOrNull()?:return@mapNotNull null;if(at<=now)return@mapNotNull null;Triple(id,at,p.getOrElse(1){"Reminder"})}.sortedBy{it.second};return if(e.isEmpty())"You have no upcoming JARVIS reminders." else "Upcoming reminders. "+e.take(8).joinToString(" "){(_,at,m)->"$m in "+format(at-now)+"."}}
 fun cancelMatching(c:Context,q0:String):String{val p=c.getSharedPreferences(PREFS,0);val ids=p.all.mapNotNull{(id,v)->if((v as? String)?.substringAfter("|")?.contains(q0,true)==true)id.toIntOrNull() else null};if(ids.isEmpty())return "I couldn\'t find a reminder matching $q0.";ids.forEach{cancelId(c,it);p.edit().remove(it.toString()).apply()};return "Reminder cancelled."}
 fun cancelAll(c:Context):String{val p=c.getSharedPreferences(PREFS,0);p.all.keys.mapNotNull{it.toIntOrNull()}.forEach{cancelId(c,it)};p.edit().clear().apply();return "All JARVIS reminders cancelled."}
 fun completed(c:Context,id:Int){c.getSharedPreferences(PREFS,0).edit().remove(id.toString()).apply()}
 fun recordRecurring(c:Context,id:Int,m:String,at:Long,interval:Long){c.getSharedPreferences(PREFS,0).edit().putString(id.toString(),"$at|$m|$interval").apply();schedule(c,id,m,at,interval)}
 fun advanceRecurring(c:Context,id:Int,interval:Long){val p=c.getSharedPreferences(PREFS,0);val old=p.getString(id.toString(),null)?:return;val a=old.split("|",limit=3);val m=a.getOrElse(1){"Reminder"};var next=a.firstOrNull()?.toLongOrNull()?:System.currentTimeMillis();while(next<=System.currentTimeMillis())next+=interval;p.edit().putString(id.toString(),"$next|$m|$interval").apply();schedule(c,id,m,next,interval)}
 fun restore(c:Context){val p=c.getSharedPreferences(PREFS,0);val now=System.currentTimeMillis();p.all.forEach{(k,v)->val id=k.toIntOrNull()?:return@forEach;val a=(v as? String)?.split("|",limit=3)?:return@forEach;var at=a[0].toLongOrNull()?:return@forEach;val m=a.getOrElse(1){"Reminder"};val interval=a.getOrNull(2)?.toLongOrNull()?:0L;if(interval>0){while(at<=now)at+=interval;p.edit().putString(k,"$at|$m|$interval").apply();schedule(c,id,m,at,interval)}else if(at>now)schedule(c,id,m,at,0)}}
 private fun schedule(c:Context,id:Int,m:String,at:Long,interval:Long){val i=Intent(c,JarvisReminderReceiver::class.java).putExtra(JarvisReminderReceiver.EXTRA_MESSAGE,m).putExtra("reminder_id",id).putExtra("recurring",interval>0).putExtra("interval",interval);val pi=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager;am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
 private fun cancelId(c:Context,id:Int){val pi=PendingIntent.getBroadcast(c,id,Intent(c,JarvisReminderReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE);if(pi!=null){(c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi);pi.cancel()}}
 private fun format(ms:Long):String{val min=(ms/60000).coerceAtLeast(1);return if(min<60)"$min minutes" else ""+(min/60)+" hours"}
}