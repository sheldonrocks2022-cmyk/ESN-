package com.esn.jarvis
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
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
  return if(found.isEmpty()) "I found no supplied Discord user IDs with accounts newer than $days days." else "I found ${found.size} supplied account${if(found.size==1) "" else "s"} newer than $days days. Say JARVIS confirm within two minutes to arm the bans."
 }
 fun massBanRecentAccounts(c:Context,days:Int=3):String{
  if(days<1)return "The account age must be at least one day."
  if(saved(c).isBlank())return "Save your Discord Server ID in the dashboard first."
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access for JARVIS first."
  open(c,saved(c));c.getSharedPreferences("jarvis_discord_mass_scan",0).edit().clear().putInt("days",days).putLong("started",System.currentTimeMillis()).apply()
  Handler(Looper.getMainLooper()).postDelayed({scanRecentMemberIds(c,days,0,linkedSetOf())},1200L)
  return "Scanning Discord for accounts newer than "+days+" days. I will only stage verified user IDs and require confirmation before bans."
 }
 private fun scanRecentMemberIds(c:Context,days:Int,pass:Int,seen:LinkedHashSet<String>){
  if(JarvisAccessibilityService.activePackage()!=PKG){finishRecentScan(c,days,seen,"Discord left the foreground.");return}
  collectIds(JarvisAccessibilityService.currentRoot(),seen)
  if(pass>=24){finishRecentScan(c,days,seen,"Scan complete.");return}
  val moved=JarvisAccessibilityService.scrollForward()
  if(!moved&&pass>2){finishRecentScan(c,days,seen,"Reached the end of the visible member list.");return}
  Handler(Looper.getMainLooper()).postDelayed({scanRecentMemberIds(c,days,pass+1,seen)},350L)
 }
 private fun collectIds(n:AccessibilityNodeInfo?,out:MutableSet<String>){if(n==null)return;val value=n.text?.toString().orEmpty()+" "+n.contentDescription?.toString().orEmpty();Regex("""\\b\\d{17,20}\\b""").findAll(value).forEach{out.add(it.value)};for(i in 0 until n.childCount)collectIds(n.getChild(i),out)}
 private fun finishRecentScan(c:Context,days:Int,seen:Set<String>,note:String){val recent=seen.filter{(accountAgeDays(it)?:Long.MAX_VALUE)<days}.distinct();c.getSharedPreferences("jarvis_discord_recent_audit",0).edit().putString("ids",recent.joinToString(",")).putInt("days",days).putLong("until",System.currentTimeMillis()+120000).apply();c.getSharedPreferences("jarvis_discord_mass_scan",0).edit().putInt("seen",seen.size).putInt("recent",recent.size).putString("status",note).apply()}
 fun recentMassBanStatus(c:Context):String{val p=c.getSharedPreferences("jarvis_discord_mass_scan",0);val seen=p.getInt("seen",-1);val recent=p.getInt("recent",-1);val status=p.getString("status","Scanning is still running.").orEmpty();return if(seen<0)"Scanning is still running." else if(recent<=0)status+" I found no verified accounts newer than "+p.getInt("days",3)+" days." else status+" I verified "+recent+" recent accounts from "+seen+" visible Discord IDs. Say confirm recent account bans within two minutes to arm them."}
 fun confirmRecentAccountBans(c:Context):String{
  val p=c.getSharedPreferences("jarvis_discord_recent_audit",0);if(System.currentTimeMillis()>p.getLong("until",0))return "No recent-account ban audit is awaiting confirmation."
  val ids=p.getString("ids","").orEmpty().split(",").filter{it.isNotBlank()};p.edit().clear().apply()
  if(ids.isEmpty())return "There are no audited accounts to ban."
  c.getSharedPreferences("jarvis_discord_phone_pending",0).edit().putString("action","bulk_ban_ids").putString("target",ids.joinToString(",")).putLong("until",System.currentTimeMillis()+120000).apply();open(c,saved(c))
  return "Armed "+ids.size+" audited account bans. Discord must expose each member in the UI; say JARVIS confirm within two minutes."
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
 private fun arm(c:Context,a:String,t:String,x:String=""):String{if(t.isBlank())return "Tell me which Discord member, channel, message, or role.";val guild=saved(c);if(guild.isBlank())return "Save your Discord Server ID in the dashboard first.";c.getSharedPreferences("jarvis_discord_phone_pending",0).edit().putString("action",a).putString("target",t).putString("extra",x).putLong("until",System.currentTimeMillis()+120000).apply();open(c,guild);return "Discord "+a.replace("_"," ")+" for "+t+" is ready. Say JARVIS confirm within two minutes."}
 fun confirm(c:Context):String{
  val p=c.getSharedPreferences("jarvis_discord_phone_pending",0)
  val until=p.getLong("until",0L)
  if(until<=0L||System.currentTimeMillis()>until){p.edit().clear().apply();return "No Discord action is awaiting confirmation."}
  val a=p.getString("action","").orEmpty();val t=p.getString("target","").orEmpty();val x=p.getString("extra","").orEmpty()
  if(a.isBlank()){p.edit().clear().apply();return "No Discord action is awaiting confirmation."}
  if(!JarvisAccessibilityService.hasAccess())return "Enable Phone Access for JARVIS first. Your Discord action is still waiting."
  if(JarvisAccessibilityService.activePackage()!=PKG){open(c,saved(c));return "Discord was not active, so I reopened it. Your action is still waiting; say JARVIS confirm again when Discord is ready."}
  if(a=="bulk_ban_ids"){
   val ids=t.split(",").map{it.trim()}.filter{it.isNotBlank()}
   val visible=ids.firstOrNull{JarvisScreenInspector.hasText(it)}?:return "None of the audited Discord user IDs are visible yet. Your queue is still saved; open the member list or profile and say JARVIS confirm again."
   val tapped=JarvisScreenInspector.tapText(visible)
   if(tapped.startsWith("I could not",true))return "I can see verified user ID $visible but could not open it. The queue is still saved."
   Handler(Looper.getMainLooper()).postDelayed({
    if(JarvisAccessibilityService.activePackage()==PKG&&JarvisScreenInspector.tapAny("Ban","Ban Member")){
     val remaining=ids.filterNot{it==visible};val e=p.edit()
     if(remaining.isEmpty())e.clear() else e.putString("action","bulk_ban_ids").putString("target",remaining.joinToString(",")).putLong("until",System.currentTimeMillis()+120000)
     e.apply()
    }
   },700L)
   return "Opening verified recent account $visible. I will look for the Ban control after Discord finishes loading. The pending queue stays saved unless the control is reached."
  }
  if(t.isNotBlank()&&a !in setOf("create_channel","create_role")){
   if(!JarvisScreenInspector.hasText(t))return "I cannot see $t on the current Discord screen yet. Your action is still waiting; navigate to it and say JARVIS confirm again."
   val tapped=JarvisScreenInspector.tapText(t)
   if(tapped.startsWith("I could not",true))return "I can see $t but could not open it. Your action is still waiting."
  }
  val labels=when(a){"ban"->arrayOf("Ban","Ban Member");"kick"->arrayOf("Kick","Kick Member");"timeout"->arrayOf("Timeout","Time Out");"mute"->arrayOf("Mute");"unmute"->arrayOf("Unmute");"delete_channel"->arrayOf("Delete Channel","Delete");"delete_message"->arrayOf("Delete Message","Delete");"pin"->arrayOf("Pin Message","Pin");"unpin"->arrayOf("Unpin Message","Unpin");"add_role","remove_role","edit_role"->arrayOf("Roles","Manage Roles");"create_channel"->arrayOf("Create Channel","Create");"create_role"->arrayOf("Create Role","Roles");"delete_role"->arrayOf("Delete Role","Delete");"edit_channel"->arrayOf("Edit Channel","Settings");"manage_member"->arrayOf("Manage","Moderation","Roles");else->emptyArray()}
  if(labels.isEmpty())return "I do not recognize that pending Discord action. It has not been executed."
  Handler(Looper.getMainLooper()).postDelayed({
   if(JarvisAccessibilityService.activePackage()==PKG&&JarvisScreenInspector.tapAny(*labels)){
    if((a=="add_role"||a=="remove_role")&&x.isNotBlank())Handler(Looper.getMainLooper()).postDelayed({JarvisScreenInspector.tapAny(x)},500L)
    p.edit().clear().apply()
   }
  },if(t.isBlank()||a in setOf("create_channel","create_role"))200L else 700L)
  return "Discord $a for $t is being confirmed. I will wait for Discord to load the control; the pending action stays saved if it cannot be reached."
 }
}