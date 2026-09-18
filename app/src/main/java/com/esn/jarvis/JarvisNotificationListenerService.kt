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

        fun readLatestMessages(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();if(raw.isBlank())return "I don't have any recent message notifications to read.";val items=raw.split("\n---\n").filter{it.isNotBlank()}.takeLast(5).reversed();return "Here are your most recent messages. "+items.joinToString(" ")}
        fun readMatching(context:Context,query:String):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val q=query.trim().lowercase();val items=raw.split("\n---\n").filter{it.isNotBlank()&&it.lowercase().contains(q)}.takeLast(5).reversed();return if(items.isEmpty())"I couldn't find recent notifications matching $query." else "Recent notifications matching $query. "+items.joinToString(" ")}
        fun dismissMatching(query:String):String{val svc=instance?:return "Notification Access is not active.";val q=query.trim().lowercase();val match=svc.activeNotifications?.filter{it.packageName!=svc.packageName}?.sortedByDescending{it.postTime}?.firstOrNull{sbn->val e=sbn.notification.extras;val title=e?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty();val text=e?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty();("$title $text ${sbn.packageName}").lowercase().contains(q)}?:return "I couldn't find a notification matching $query.";return try{svc.cancelNotification(match.key);"Matching notification dismissed."}catch(_:Exception){"I couldn't dismiss that notification."}}
        fun latestSender(context:Context):String{val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val latest=raw.split("\n---\n").filter{it.isNotBlank()}.lastOrNull()?:return "I don\'t have a recent message.";val sender=latest.substringBefore(" says:").trim();return if(sender.isBlank())"I couldn\'t determine the sender." else "That was from $sender."}
        fun readLatestMessage(context:Context):String{instance?.activeNotifications?.filter{it.packageName!=instance?.packageName}?.maxByOrNull{it.postTime}?.key?.let{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_FOCUS,it).apply()};val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"").orEmpty();val latest=raw.split("\n---\n").filter{it.isNotBlank()}.lastOrNull();return latest?.let{"The latest message is: $it"}?:"I don't have a recent message notification."}
        fun dismissLatest():String{val svc=instance?:return "Notification Access is not active.";val focus=svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_FOCUS,"");val latest=svc.activeNotifications?.firstOrNull{it.key==focus}?:svc.activeNotifications?.filter{it.packageName!=svc.packageName}?.maxByOrNull{it.postTime}?:return "I couldn't find a notification to dismiss.";return try{svc.cancelNotification(latest.key);"Latest notification dismissed."}catch(_:Exception){"I couldn't dismiss that notification."}}
        fun replyLatest(text:String):String{if(text.isBlank())return "Tell me what you want to reply.";val svc=instance?:return "Notification Access is not active.";val focus=svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_FOCUS,"");val notifications=svc.activeNotifications?.sortedWith(compareByDescending<StatusBarNotification>{it.key==focus}.thenByDescending{it.postTime}).orEmpty();for(sbn in notifications){for(action in sbn.notification.actions.orEmpty()){val inputs=action.remoteInputs?:continue;if(inputs.isEmpty())continue;try{val intent=Intent();val bundle=android.os.Bundle();inputs.forEach{bundle.putCharSequence(it.resultKey,text)};RemoteInput.addResultsToIntent(inputs,intent,bundle);action.actionIntent.send(svc,0,intent);svc.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("last_reply",text).putString(KEY_FOCUS,sbn.key).apply();return "Reply sent."}catch(_:Exception){}}};return "I couldn't find a notification that supports direct reply."}
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
        if(listOf("download","update available","charging","battery","silent notifications").any{lower.contains(it)})return
        val prefs=getSharedPreferences(PREFS,MODE_PRIVATE)
        val existing=prefs.getString(KEY_ITEMS,"").orEmpty().split("\n---\n").filter{it.isNotBlank()}.toMutableList()
        val entry="$title says: $text"
        existing.remove(entry);existing.add(entry);while(existing.size>MAX_ITEMS)existing.removeAt(0)
        prefs.edit().putString(KEY_ITEMS,existing.joinToString("\n---\n")).apply()
    }
}
