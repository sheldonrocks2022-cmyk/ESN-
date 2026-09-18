package com.esn.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object JarvisReminderManager {
    private const val PREFS="jarvis_reminder_manager"
    fun record(context:Context,id:Int,message:String,whenMillis:Long){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(id.toString(),"${whenMillis}|${message}").apply()}
    fun list(context:Context):String{val now=System.currentTimeMillis();val entries=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).all.mapNotNull{(id,v)->val s=v as? String?:return@mapNotNull null;val p=s.split("|",limit=2);val at=p.firstOrNull()?.toLongOrNull()?:return@mapNotNull null;if(at<=now)return@mapNotNull null;Triple(id,at,p.getOrElse(1){"Reminder"})}.sortedBy{it.second};return if(entries.isEmpty())"You have no upcoming JARVIS reminders." else "Upcoming reminders. "+entries.take(8).joinToString(" "){(_,at,msg)->"$msg in ${format(at-now)}."}}
    fun cancelMatching(context:Context,query:String):String {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val q=query.lowercase().trim()
        val matches=prefs.all.mapNotNull{(id,v)->val s=v as? String?:return@mapNotNull null;if(!s.substringAfter("|").lowercase().contains(q))return@mapNotNull null;id.toIntOrNull()}
        if(matches.isEmpty())return "I couldn't find a reminder matching $query."
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        matches.forEach{id->val pi=PendingIntent.getBroadcast(context,id,Intent(context,JarvisReminderReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE);if(pi!=null){alarm.cancel(pi);pi.cancel()};prefs.edit().remove(id.toString()).apply()}
        return if(matches.size==1)"Reminder cancelled." else "${matches.size} matching reminders cancelled."
    }
    fun cancelAll(context:Context):String{val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;prefs.all.keys.forEach{id->val request=id.toIntOrNull()?:return@forEach;val pi=PendingIntent.getBroadcast(context,request,Intent(context,JarvisReminderReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE);if(pi!=null){alarm.cancel(pi);pi.cancel()}};prefs.edit().clear().apply();return "All JARVIS reminders cancelled."}
    fun completed(context:Context,id:Int){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(id.toString()).apply()}
    private fun format(ms:Long):String{val minutes=(ms/60000).coerceAtLeast(1);return if(minutes<60)"$minutes minutes" else "${minutes/60} hours"}
}
