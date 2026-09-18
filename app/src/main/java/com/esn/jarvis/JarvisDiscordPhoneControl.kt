package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

object JarvisDiscordPhoneControl {
 private const val PKG="com.discord"
 private fun open(c:Context,g:String=""):Boolean { return try { val i=if(g.isNotBlank()) Intent(Intent.ACTION_VIEW,Uri.parse("https://discord.com/channels/"+g)).setPackage(PKG) else c.packageManager.getLaunchIntentForPackage(PKG); if(i==null) false else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); true } } catch(_:Exception){ false } }
 fun openServer(c:Context,id:String):String{if(id.isBlank()||id.any{!it.isDigit()})return "Give me a valid Discord server ID.";c.getSharedPreferences("jarvis_discord_phone",0).edit().putString("guild_id",id).apply();return if(open(c,id))"Opening your Discord server as your logged-in account." else "I could not open Discord."}
 fun tap(c:Context,label:String):String=if(!JarvisAccessibilityService.hasAccess())"Enable Phone Access for JARVIS first." else JarvisScreenInspector.tapText(label)
 fun ban(c:Context,t:String)=arm(c,"ban",t)
 fun kick(c:Context,t:String)=arm(c,"kick",t)
 fun timeout(c:Context,t:String)=arm(c,"timeout",t)
 fun deleteChannel(c:Context,t:String)=arm(c,"delete_channel",t)
 private fun arm(c:Context,a:String,t:String):String{if(t.isBlank())return "Tell me which Discord member or channel.";c.getSharedPreferences("jarvis_discord_phone_pending",0).edit().putString("action",a).putString("target",t).putLong("until",System.currentTimeMillis()+60000).apply();open(c,c.getSharedPreferences("jarvis_discord_phone",0).getString("guild_id","").orEmpty());return "Discord "+a.replace("_"," ")+" for "+t+" is ready. Say confirm Discord action within 60 seconds."}
 fun confirm(c:Context):String{val p=c.getSharedPreferences("jarvis_discord_phone_pending",0);if(System.currentTimeMillis()>p.getLong("until",0))return "No Discord action is awaiting confirmation.";val a=p.getString("action","").orEmpty();val t=p.getString("target","").orEmpty();p.edit().clear().apply();if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access for JARVIS first.";if(t.isNotBlank()&&!JarvisScreenInspector.hasText(t))return "I cannot see "+t+" on the current Discord screen. Open that member or channel and try again.";if(t.isNotBlank())JarvisScreenInspector.tapText(t);val labels=when(a){"ban"->arrayOf("Ban","Ban Member");"kick"->arrayOf("Kick","Kick Member");"timeout"->arrayOf("Timeout","Time Out");"delete_channel"->arrayOf("Delete Channel","Delete");else->emptyArray()};if(labels.isEmpty()||!JarvisScreenInspector.tapAny(*labels))return "I could not find that Discord control on this screen.";Thread.sleep(350);JarvisScreenInspector.tapAny("Confirm","Ban","Kick","Delete","Timeout");return "Discord "+a.replace("_"," ")+" action submitted through your logged-in Discord app."}
}
