package com.esn.jarvis
import android.content.Context
import android.content.Intent
import android.net.Uri
object JarvisDiscordPhoneControl{
 private const val PKG="com.discord"
 private fun saved(c:Context)=c.getSharedPreferences("jarvis_discord_phone",0).getString("guild_id","").orEmpty()
 private fun open(c:Context,g:String=""):Boolean=try{
  val guild=g.ifBlank{saved(c)}
  val intents=if(guild.isNotBlank())listOf(
   Intent(Intent.ACTION_VIEW,Uri.parse("discord://discord.com/channels/$guild")).setPackage(PKG),
   Intent(Intent.ACTION_VIEW,Uri.parse("https://discord.com/channels/$guild")).setPackage(PKG),
   Intent(Intent.ACTION_VIEW,Uri.parse("https://discord.com/channels/$guild"))
  ) else listOfNotNull(c.packageManager.getLaunchIntentForPackage(PKG))
  intents.any{i->try{i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);true}catch(_:Throwable){false}}
 }catch(_:Throwable){false}
 fun openSavedServer(c:Context):String{val id=saved(c);return if(id.isBlank())"Enter and save your Discord Server ID in the JARVIS dashboard first." else if(open(c,id))"Opening your saved Discord server." else "I could not open the saved Discord server."}
 fun openServer(c:Context,id:String):String{if(id.isBlank()||id.any{!it.isDigit()})return "Give me a valid Discord server ID.";c.getSharedPreferences("jarvis_discord_phone",0).edit().putString("guild_id",id).apply();return if(open(c,id))"Opening your Discord server as your logged-in account." else "I could not open Discord."}
 fun accountCreatedAt(userId:String):Long?=try{val id=userId.toLong();(id ushr 22)+1420070400000L}catch(_:Throwable){null}
 fun accountAgeDays(userId:String,now:Long=System.currentTimeMillis()):Long?=accountCreatedAt(userId)?.let{((now-it).coerceAtLeast(0L))/86400000L}
 fun recentAccountAudit(c:Context,days:Int,ids:String):String{
  if(days<1)return "The account age must be at least one day."
  val found=Regex("""\d{17,20}""").findAll(ids).map{it.value}.distinct().filter{(accountAgeDays(it)?:Long.MAX_VALUE)<days}.toList()
  val p=c.getSharedPreferences("jarvis_discord_recent_audit",0)
  p.edit().putString("ids",found.joinToString(",")).putInt("days",days).putLong("until",System.currentTimeMillis()+120000).apply()
  return if(found.isEmpty())"I found no supplied Discord user IDs with accounts newer than $days days." else "I found "+found.size+" supplied account"+if(found.size==1)"":"s"+" newer than $days days. Say confirm recent account bans within two minutes to arm the bans."
 }
 fun confirmRecentAccountBans(c:Context):String{
  val p=c.getSharedPreferences("jarvis_discord_recent_audit",0);if(System.currentTimeMillis()>p.getLong("until",0))return "No recent-account ban audit is awaiting confirmation."
  val ids=p.getString("ids","").orEmpty().split(",").filter{it.isNotBlank()};p.edit().clear().apply()
  if(ids.isEmpty())return "There are no audited accounts to ban."
  c.getSharedPreferences("jarvis_discord_phone_pending",0).edit().putString("action","bulk_ban_ids").putString("target",ids.joinToString(",")).putLong("until",System.currentTimeMillis()+60000).apply();open(c,saved(c))
  return "Armed "+ids.size+" audited account bans. Discord must expose each member in the UI; say confirm Discord action within 60 seconds."
 }
 fun tap(c:Context,label:String)=if(!JarvisAccessibilityService.hasAccess())"Enable Phone Access for JARVIS first." else JarvisScreenInspector.tapText(label)
 fun ban(c:Context,t:String)=arm(c,"ban",t);fun kick(c:Context,t:String)=arm(c,"kick",t);fun timeout(c:Context,t:String)=arm(c,"timeout",t)
 fun mute(c:Context,t:String)=arm(c,"mute",t);fun unmute(c:Context,t:String)=arm(c,"unmute",t)
 fun deleteChannel(c:Context,t:String)=arm(c,"delete_channel",t);fun deleteMessage(c:Context,t:String)=arm(c,"delete_message",t)
 fun pin(c:Context,t:String)=arm(c,"pin",t);fun unpin(c:Context,t:String)=arm(c,"unpin",t)
 fun addRole(c:Context,t:String,role:String)=arm(c,"add_role",t,role);fun removeRole(c:Context,t:String,role:String)=arm(c,"remove_role",t,role)
 fun createChannel(c:Context,t:String)=arm(c,"create_channel",t);fun createRole(c:Context,t:String)=arm(c,"create_role",t)
 fun deleteRole(c:Context,t:String)=arm(c,"delete_role",t);fun editChannel(c:Context,t:String)=arm(c,"edit_channel",t)
 fun editRole(c:Context,t:String)=arm(c,"edit_role",t);fun manageMember(c:Context,t:String)=arm(c,"manage_member",t)
 private fun arm(c:Context,a:String,t:String,x:String=""):String{if(t.isBlank())return "Tell me which Discord member, channel, message, or role.";val guild=saved(c);if(guild.isBlank())return "Save your Discord Server ID in the dashboard first.";c.getSharedPreferences("jarvis_discord_phone_pending",0).edit().putString("action",a).putString("target",t).putString("extra",x).putLong("until",System.currentTimeMillis()+60000).apply();open(c,guild);return "Discord "+a.replace("_"," ")+" for "+t+" is ready. Say confirm Discord action within 60 seconds."}
 fun confirm(c:Context):String{val p=c.getSharedPreferences("jarvis_discord_phone_pending",0);if(System.currentTimeMillis()>p.getLong("until",0))return "No Discord action is awaiting confirmation.";val a=p.getString("action","").orEmpty();val t=p.getString("target","").orEmpty();val x=p.getString("extra","").orEmpty();p.edit().clear().apply();if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access for JARVIS first.";if(JarvisAccessibilityService.activePackage()!=PKG)return "Discord is not the active app. Open Discord and try again.";if(t.isNotBlank()&&!JarvisScreenInspector.hasText(t)&&a !in setOf("create_channel","create_role"))return "I cannot see "+t+" on the current Discord screen.";if(t.isNotBlank()&&a !in setOf("create_channel","create_role"))JarvisScreenInspector.tapText(t);if(a=="bulk_ban_ids")return "The audited IDs are verified by Discord snowflake age, but this Accessibility build cannot safely map every ID to a visible member. No bans were executed.";val labels=when(a){"ban"->arrayOf("Ban","Ban Member");"kick"->arrayOf("Kick","Kick Member");"timeout"->arrayOf("Timeout","Time Out");"mute"->arrayOf("Mute");"unmute"->arrayOf("Unmute");"delete_channel"->arrayOf("Delete Channel","Delete");"delete_message"->arrayOf("Delete Message","Delete");"pin"->arrayOf("Pin Message","Pin");"unpin"->arrayOf("Unpin Message","Unpin");"add_role","remove_role","edit_role"->arrayOf("Roles","Manage Roles");"create_channel"->arrayOf("Create Channel","Create");"create_role"->arrayOf("Create Role","Roles");"delete_role"->arrayOf("Delete Role","Delete");"edit_channel"->arrayOf("Edit Channel","Settings");"manage_member"->arrayOf("Manage","Moderation","Roles");else->emptyArray()};if(labels.isEmpty()||!JarvisScreenInspector.tapAny(*labels))return "I could not find that Discord control on this screen.";if((a=="add_role"||a=="remove_role")&&x.isNotBlank()&&!JarvisScreenInspector.tapAny(x))return "I reached roles but could not find "+x+".";return "Discord "+a.replace("_"," ")+" control was reached. Check the visible Discord confirmation before finalizing."}
}