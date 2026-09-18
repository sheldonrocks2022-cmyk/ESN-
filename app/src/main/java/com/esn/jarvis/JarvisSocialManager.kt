package com.esn.jarvis
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object JarvisSocialManager {
 private const val PREFS="jarvis_social"
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun note(c:Context,person:String,note:String):String{if(person.isBlank()||note.isBlank())return "I need a person and a note.";val k="person_"+person.lowercase(Locale.US);val old=p(c).getString(k,"").orEmpty();p(c).edit().putString(k,(old.lines().filter{it.isNotBlank()}+("\${java.lang.System.currentTimeMillis()}|"+note)).takeLast(20).joinToString("\n")).apply();return "Noted for "+person+"."}
 fun about(c:Context,person:String):String{val raw=p(c).getString("person_"+person.lowercase(Locale.US),"").orEmpty();val notes=raw.lines().filter{it.isNotBlank()}.takeLast(5).map{it.substringAfter("|")};return if(notes.isEmpty())"I don't have social notes for "+person+"." else person+": "+notes.joinToString(". ")}
 fun importantDate(c:Context,person:String,label:String,date:String):String{p(c).edit().putString("date_"+person.lowercase(Locale.US)+"_"+label.lowercase(Locale.US),date).apply();return label+" saved for "+person+"."}
 fun dates(c:Context):String{val a=p(c).all.filterKeys{it.startsWith("date_")};return if(a.isEmpty())"No important social dates saved." else a.entries.take(10).joinToString(". "){it.key.removePrefix("date_").replace("_"," ")+": "+it.value}}
 fun followUp(c:Context,person:String,whenText:String):String{p(c).edit().putString("follow_"+person.lowercase(Locale.US),whenText).apply();return "Follow-up saved for "+person+": "+whenText+"."}
 fun followUps(c:Context):String{val a=p(c).all.filterKeys{it.startsWith("follow_")};return if(a.isEmpty())"No social follow-ups saved." else a.entries.take(10).joinToString(". "){it.key.removePrefix("follow_").replace("_"," ")+": "+it.value}}
 fun touch(c:Context,person:String){if(person.isNotBlank())p(c).edit().putLong("contact_"+person.lowercase(Locale.US),java.lang.System.currentTimeMillis()).apply()}
 fun recentPeople(c:Context):String{val fmt=SimpleDateFormat("MMM d",Locale.US);val a=p(c).all.filterKeys{it.startsWith("contact_")}.mapNotNull{e->(e.value as? Long)?.let{Triple(e.key.removePrefix("contact_").replace("_"," "),it,fmt.format(Date(it)))}}.sortedByDescending{it.second}.take(8);return if(a.isEmpty())"I don't have enough social activity history yet." else "Recent people: "+a.joinToString(", "){it.first+" "+it.third}}
 fun briefing(c:Context):String=JarvisNotificationListenerService.summary(c)+" "+followUps(c)+" "+dates(c)
 fun clearPerson(c:Context,person:String):String{val s=person.lowercase(Locale.US);val e=p(c).edit();p(c).all.keys.filter{it=="person_"+s||it=="contact_"+s||it.startsWith("date_"+s+"_")||it=="follow_"+s}.forEach{e.remove(it)};e.apply();return "Local social data cleared for "+person+"."}
}