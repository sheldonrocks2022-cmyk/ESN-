package com.esn.jarvis

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils

class JarvisNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val PREFS="jarvis_notifications"
        private const val KEY_ITEMS="items"
        private const val MAX_ITEMS=25
        private const val KEY_FOCUS="focus_key"
        @Volatile private var instance:JarvisNotificationListenerService?=null
        const val ACTION_SPEAK_NOTIFICATION="com.esn.jarvis.SPEAK_NOTIFICATION"
        const val EXTRA_NOTIFICATION_TEXT="notification_text"

        fun readLatestMessages(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();if(raw.isBlank())return "I don't have any recent message notifications to read.";val items=raw.split("\n---\n").filter{it.isNotBlank()}.takeLast(5).reversed();return "Here are your most recent messages. "+items.joinToString(" ")}
        fun readMatching(context:Context,query:String):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val q=query.trim().lowercase();val items=raw.split("\n---\n").filter{it.isNotBlank()&&it.lowercase().contains(q)}.takeLast(5).reversed();return if(items.isEmpty())"I couldn't find recent notifications matching $query." else "Recent notifications matching $query. "+items.joinToString(" ")}
        fun dismissMatching(query:String):String{val svc=instance?:return "Notification Access is not active.";val q=query.trim().lowercase();val match=svc.activeNotifications?.filter{it.packageName!=svc.packageName}?.sortedByDescending{it.postTime}?.firstOrNull{sbn->val e=sbn.notification.extras;val title=e?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty();val text=e?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty();("$title $text ${sbn.packageName}").lowercase().contains(q)}?:return "I couldn't find a notification matching $query.";return try{svc.cancelNotification(match.key);"Matching notification dismissed."}catch(_:Exception){"I couldn't dismiss that notification."}}
        fun latestSender(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val latest=raw.split("\n---\n").filter{it.isNotBlank()}.lastOrNull()?:return "I don\'t have a recent message.";val sender=latest.substringBefore(" says:").trim();return if(sender.isBlank())"I couldn\'t determine the sender." else "That was from $sender."}
        fun readLatestMessage(context:Context):String{instance?.activeNotifications?.filter{it.packageName!=instance?.packageName}?.maxByOrNull{it.postTime}?.key?.let{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_FOCUS,it).apply()};val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val latest=raw.split("\n---\n").filter{it.isNotBlank()}.lastOrNull();return latest?.let{"The latest message is: $it"}?:"I don't have a recent message notification."}
        fun dismissLatest():String{val svc=instance?:return "Notification Access is not active.";val focus=svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_FOCUS,"");val latest=svc.activeNotifications?.firstOrNull{it.key==focus}?:svc.activeNotifications?.filter{it.packageName!=svc.packageName}?.maxByOrNull{it.postTime}?:return "I couldn't find a notification to dismiss.";return try{svc.cancelNotification(latest.key);"Latest notification dismissed."}catch(_:Exception){"I couldn't dismiss that notification."}}
        fun replyLatest(text:String):String{if(text.isBlank())return "Tell me what you want to reply.";val svc=instance?:return "Notification Access is not active.";val focus=svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_FOCUS,"");val notifications=svc.activeNotifications?.sortedWith(compareByDescending<StatusBarNotification>{it.key==focus}.thenByDescending{it.postTime}).orEmpty();for(sbn in notifications){for(action in sbn.notification.actions.orEmpty()){val inputs=action.remoteInputs?:continue;if(inputs.isEmpty())continue;try{val intent=Intent();val bundle=android.os.Bundle();inputs.forEach{bundle.putCharSequence(it.resultKey,text)};RemoteInput.addResultsToIntent(inputs,intent,bundle);action.actionIntent.send(svc,0,intent);svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("last_reply",text).putString(KEY_FOCUS,sbn.key).apply();return "Reply sent."}catch(_:Exception){}}};return "I couldn't find a notification that supports direct reply."}
        fun prioritySummary(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val items=raw.split("\n---\n").filter{it.isNotBlank()}.takeLast(10).filter{val title=it.substringBefore(" says:");JarvisProactiveIntelligence.score(context,title,it)>=3};return if(items.isEmpty())"No high-priority recent notifications." else "Priority notifications: "+items.takeLast(5).joinToString(" ")}
        fun summary(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val items=raw.split("\n---\n").filter{it.isNotBlank()}.takeLast(10);return if(items.isEmpty())"You have no recent notifications saved." else "You missed "+items.size+" recent notifications. "+items.takeLast(5).joinToString(" ")}
        fun muteSource(context:Context,name:String):String{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val set=p.getStringSet("muted_sources",emptySet())!!.toMutableSet();set.add(name.lowercase());p.edit().putStringSet("muted_sources",set).apply();return "I will stop announcing notifications matching $name."}
        fun notificationAccessSettings(context:Context):String=try{context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));"Opening notification access settings. Enable JARVIS so I can manage notifications."}catch(_:Exception){"I couldn't open notification access settings."}
    }

    override fun onListenerConnected(){super.onListenerConnected();instance=this}
    override fun onListenerDisconnected(){instance=null;super.onListenerDisconnected()}
    override fun onDestroy(){if(instance===this)instance=null;super.onDestroy()}

    override fun onNotificationPosted(sbn:StatusBarNotification){
        if(sbn.packageName==packageName)return
        val extras=sbn.notification.extras?:return
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if(text.isBlank()||TextUtils.isEmpty(title))return
        val lower="$title $text".lowercase()
        val muted=getSharedPreferences(PREFS,MODE_PRIVATE).getStringSet("muted_sources",emptySet()).orEmpty()
        if(muted.any{lower.contains(it)})return
        if(listOf("download","update available","charging","battery","silent notifications").any{lower.contains(it)})return
        val prefs=getSharedPreferences(PREFS,MODE_PRIVATE)
        val existing=prefs.getString(KEY_ITEMS,"").orEmpty().split("\n---\n").filter{it.isNotBlank()}.toMutableList()
        val entry="$title says: $text"
        existing.remove(entry);existing.add(entry);while(existing.size>MAX_ITEMS)existing.removeAt(0)
        prefs.edit().putString(KEY_ITEMS,existing.joinToString("\n---\n")).apply()
        // Feed real notification events into the automation engine. Specific rules win naturally
        // because each event is independently looked up and protected by engine cooldown/safety.
        JarvisAutomationEngine.fire(this,"notification")
        JarvisAutomationEngine.fire(this,"notification from "+title.lowercase())
        JarvisAutomationEngine.fire(this,"message from "+title.lowercase())
        val score=JarvisProactiveIntelligence.score(this,title,text)
        val readAll=getSharedPreferences("jarvis",MODE_PRIVATE).getBoolean("read_all_notifications",true)
        if(score>=3||readAll){
            if(score>=3)JarvisProactiveIntelligence.recordAlert(this,if(JarvisProactiveIntelligence.isPriority(this,title))"$title is a priority contact" else "the notification appeared important")
            try{startService(Intent(this,JarvisVoiceService::class.java).setAction(ACTION_SPEAK_NOTIFICATION).putExtra(EXTRA_NOTIFICATION_TEXT,"Notification from $title. $text"))}catch(_:Throwable){}
        }
    }
}
