package com.esn.jarvis
import android.content.Context
object JarvisCommunicationManager{
 private const val PREFS="jarvis_communication"
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun draft(c:Context,person:String,body:String):String{if(person.isBlank()||body.isBlank())return "Tell me who and what to say.";JarvisContext.remember(c,"contact",person);JarvisContext.saveDraft(c,person,body);p(c).edit().putString("active_person",person).apply();return "Draft saved for $person: $body"}
 fun send(c:Context,person:String,body:String):String{val r=JarvisMessaging.execute(c,"send a message to $person saying $body");if(r.startsWith("Message sent")){p(c).edit().putString("active_person",person).putString("last_sent",body).putLong("last_sent_time",System.currentTimeMillis()).apply();JarvisContext.remember(c,"contact",person)};return r}
 fun tellCurrent(c:Context,body:String):String{val person=JarvisContext.recall(c,"contact").ifBlank{p(c).getString("active_person","").orEmpty()};return if(person.isBlank())"I don't have an active person in context." else send(c,person,body)}
 fun sendDraft(c:Context):String{val person=JarvisContext.draftRecipient(c);val body=JarvisContext.draftBody(c);if(person.isBlank()||body.isBlank())return "There is no message draft.";val r=send(c,person,body);if(r.startsWith("Message sent"))JarvisContext.clearDraft(c);return r}
 fun replyToLatest(c:Context,body:String):String{val r=JarvisNotificationListenerService.replyLatest(body);return if(r=="Reply sent.")r else tellCurrent(c,body)}
 fun verify(c:Context):String{val person=p(c).getString("active_person","").orEmpty();val body=p(c).getString("last_sent","").orEmpty();return if(person.isBlank()||body.isBlank())"I don't have a recent sent message to verify." else "The last SMS send request completed for $person: $body. Delivery confirmation is not available yet."}
 fun status(c:Context):String{val person=JarvisContext.recall(c,"contact").ifBlank{p(c).getString("active_person","").orEmpty()};return if(person.isBlank())"No active conversation context." else "Active conversation: $person."}
}
