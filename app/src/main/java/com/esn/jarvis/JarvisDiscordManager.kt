package com.esn.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
object JarvisDiscordManager{
 private const val API="https://discord.com/api/v10";private val io=Executors.newCachedThreadPool()
 fun guild(c:Context)=c.getSharedPreferences("jarvis_discord",0).getString("guild_id","").orEmpty().trim()
 fun saveGuild(c:Context,id:String){c.getSharedPreferences("jarvis_discord",0).edit().putString("guild_id",id.trim()).apply()}
 fun configured(c:Context)=guild(c).isNotBlank()&&JarvisDiscordSecrets.getToken(c).isNotBlank()
 fun test(c:Context)=execute(c,"discord status")
 fun execute(c:Context,raw:String):String{if(!configured(c))return "Configure the Discord server ID and bot token in the dashboard.";return try{io.submit<String>{net(c,raw.lowercase().trim())}.get(25,TimeUnit.SECONDS)}catch(e:Exception){"Discord command failed: "+(e.cause?.message?:e.message?:e.javaClass.simpleName)}}
 private fun net(c:Context,t:String):String{val g=guild(c);val p=c.getSharedPreferences("jarvis_discord_pending",0)
  if(t=="confirm discord wipe"){if(System.currentTimeMillis()>p.getLong("wipe",0))return "No wipe is awaiting confirmation.";p.edit().remove("wipe").apply();return wipe(c,g)}
  if(t=="wipe discord server"||t=="destroy discord server"||t=="nuke discord server"){p.edit().putLong("wipe",System.currentTimeMillis()+30000).apply();return "Destructive Discord wipe armed for 30 seconds. Say confirm Discord wipe."}
  if(t=="confirm discord action"){val ids=p.getString("ids","").orEmpty().split(",").filter{it.isNotBlank()};if(ids.isEmpty()||System.currentTimeMillis()>p.getLong("until",0))return "No mass action is awaiting confirmation.";p.edit().clear().apply();var x=0;ids.forEach{if(req(c,"PUT","/guilds/$g/bans/$it",JSONObject().put("delete_message_seconds",0)).code in 200..299)x++};return "Banned $x of ${ids.size} selected accounts."}
  if(t.contains("ban all accounts")&&t.contains("created within")){val n=Regex("(\\d+)").find(t)?.value?.toLongOrNull()?:1;val h=if(t.contains("day"))n*24 else n;val cut=System.currentTimeMillis()-h*3600000;val ids=members(c,g).mapNotNull{it.optJSONObject("user")?.optString("id")}.filter{snow(it)>=cut};if(ids.isEmpty())return "No matching new accounts found.";p.edit().putString("ids",ids.joinToString(",")).putLong("until",System.currentTimeMillis()+60000).apply();return "Found ${ids.size} matching accounts. Say confirm Discord action within 60 seconds."}
  when{
   t=="discord status"||t=="discord server status"||t=="discord server info"->{val r=req(c,"GET","/guilds/$g?with_counts=true");if(r.code !in 200..299)return bad(r);val j=JSONObject(r.body);return "Connected to ${j.optString("name")}. Members: ${j.optInt("approximate_member_count")}."}
   t=="discord list channels"->return names(arr(c,"/guilds/$g/channels"))
   t=="discord list roles"->return names(arr(c,"/guilds/$g/roles"))
   t=="discord list bans"->{val a=arr(c,"/guilds/$g/bans");return "Bans: "+a.length()}
   t=="discord audit log"||t=="discord recent audit log"->{val r=req(c,"GET","/guilds/$g/audit-logs?limit=10");return if(r.code in 200..299)"Discord audit log retrieved." else bad(r)}
   t.startsWith("discord send ")&&t.contains(" to ")->{val body=t.removePrefix("discord send ").substringBeforeLast(" to ");val id=channel(c,g,t.substringAfterLast(" to "))?:return "Channel not found.";return ok(req(c,"POST","/channels/$id/messages",JSONObject().put("content",body)),"Discord message sent.")}
   t.startsWith("discord create text channel ")->return ok(req(c,"POST","/guilds/$g/channels",JSONObject().put("name",t.removePrefix("discord create text channel ")).put("type",0)),"Text channel created.")
   t.startsWith("discord create voice channel ")->return ok(req(c,"POST","/guilds/$g/channels",JSONObject().put("name",t.removePrefix("discord create voice channel ")).put("type",2)),"Voice channel created.")
   t.startsWith("discord create category ")->return ok(req(c,"POST","/guilds/$g/channels",JSONObject().put("name",t.removePrefix("discord create category ")).put("type",4)),"Category created.")
   t.startsWith("discord delete channel ")->{val id=channel(c,g,t.removePrefix("discord delete channel "))?:return "Channel not found.";return ok(req(c,"DELETE","/channels/$id"),"Channel deleted.")}
   t.startsWith("discord create role ")->return ok(req(c,"POST","/guilds/$g/roles",JSONObject().put("name",t.removePrefix("discord create role "))),"Role created.")
   t.startsWith("discord delete role ")->{val id=role(c,g,t.removePrefix("discord delete role "))?:return "Role not found.";return ok(req(c,"DELETE","/guilds/$g/roles/$id"),"Role deleted.")}
   t.startsWith("discord ban ")->return mod(c,g,t.removePrefix("discord ban "),true)
   t.startsWith("discord kick ")->return mod(c,g,t.removePrefix("discord kick "),false)
   t.startsWith("discord unban ")->{val id=t.removePrefix("discord unban ").digits();return ok(req(c,"DELETE","/guilds/$g/bans/$id"),"User unbanned.")}
   t.startsWith("discord timeout ")->{val id=t.removePrefix("discord timeout ").substringBefore(" for ").digits();val min=Regex("(\\d+)").find(t.substringAfter(" for ","10"))?.value?.toLongOrNull()?:10;return ok(req(c,"PATCH","/guilds/$g/members/$id",JSONObject().put("communication_disabled_until",Instant.now().plus(min,ChronoUnit.MINUTES).toString())),"Timeout set.")}
   t.startsWith("discord remove timeout ")->{val id=t.removePrefix("discord remove timeout ").digits();return ok(req(c,"PATCH","/guilds/$g/members/$id",JSONObject().put("communication_disabled_until",JSONObject.NULL)),"Timeout removed.")}
   t.startsWith("discord rename server ")->return ok(req(c,"PATCH","/guilds/$g",JSONObject().put("name",t.removePrefix("discord rename server "))),"Server renamed.")
   t.startsWith("discord prune ")->{val d=Regex("(\\d+)").find(t)?.value?.toIntOrNull()?:7;return ok(req(c,"POST","/guilds/$g/prune",JSONObject().put("days",d)),"Prune completed.")}
   else->return "Discord manager supports server info, messages, channels, roles, bans, kicks, timeouts, pruning, audit log, account-age mass bans, and guarded destructive wipe."
  }}
 private fun mod(c:Context,g:String,s:String,b:Boolean):String{val id=s.digits();if(id.isBlank())return "Give me a Discord user ID.";return if(b)ok(req(c,"PUT","/guilds/$g/bans/$id",JSONObject().put("delete_message_seconds",0)),"User banned.")else ok(req(c,"DELETE","/guilds/$g/members/$id"),"User kicked.")}
 private fun channel(c:Context,g:String,q:String):String?{val a=arr(c,"/guilds/$g/channels");for(i in 0 until a.length()){val j=a.getJSONObject(i);if(j.optString("id")==q.trim()||j.optString("name").equals(q.trim(),true))return j.optString("id")};return null}
 private fun role(c:Context,g:String,q:String):String?{val a=arr(c,"/guilds/$g/roles");for(i in 0 until a.length()){val j=a.getJSONObject(i);if(j.optString("id")==q.trim()||j.optString("name").equals(q.trim(),true))return j.optString("id")};return null}
 private fun members(c:Context,g:String):List<JSONObject>{val out=mutableListOf<JSONObject>();var after="0";repeat(20){val r=req(c,"GET","/guilds/$g/members?limit=1000&after=$after");if(r.code !in 200..299)return out;val a=JSONArray(r.body);for(i in 0 until a.length())out+=a.getJSONObject(i);if(a.length()<1000)return out;after=a.getJSONObject(a.length()-1).getJSONObject("user").getString("id")};return out}
 private fun snow(id:String)=try{(id.toLong() ushr 22)+1420070400000L}catch(_:Exception){0L}
 private fun wipe(c:Context,g:String):String{var b=0;var ch=0;var ro=0;members(c,g).forEach{val id=it.optJSONObject("user")?.optString("id").orEmpty();if(id.isNotBlank()&&req(c,"PUT","/guilds/$g/bans/$id",JSONObject().put("delete_message_seconds",0)).code in 200..299)b++};val a=arr(c,"/guilds/$g/channels");for(i in 0 until a.length())if(req(c,"DELETE","/channels/"+a.getJSONObject(i).optString("id")).code in 200..299)ch++;val r=arr(c,"/guilds/$g/roles");for(i in 0 until r.length()){val id=r.getJSONObject(i).optString("id");if(id!=g&&req(c,"DELETE","/guilds/$g/roles/$id").code in 200..299)ro++};return "Wipe finished: $b members banned, $ch channels deleted, $ro roles deleted."}
 private fun arr(c:Context,p:String):JSONArray{val r=req(c,"GET",p);if(r.code !in 200..299)throw IllegalStateException(bad(r));return JSONArray(r.body)}
 private fun names(a:JSONArray)=if(a.length()==0)"None found."else(0 until minOf(a.length(),25)).joinToString(", "){a.getJSONObject(it).optString("name")}
 private data class Resp(val code:Int,val body:String)
 private fun req(c:Context,m:String,p:String,b:JSONObject?=null):Resp{val x=URL(API+p).openConnection() as HttpURLConnection;x.requestMethod=m;x.setRequestProperty("Authorization","Bot "+JarvisDiscordSecrets.getToken(c));x.setRequestProperty("Content-Type","application/json");x.connectTimeout=10000;x.readTimeout=15000;if(b!=null){x.doOutput=true;x.outputStream.use{it.write(b.toString().toByteArray())}};val code=x.responseCode;val s=(if(code in 200..299)x.inputStream else x.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty();x.disconnect();return Resp(code,s)}
 private fun ok(r:Resp,s:String)=if(r.code in 200..299)s else bad(r);private fun bad(r:Resp)="Discord returned "+r.code+": "+runCatching{JSONObject(r.body).optString("message")}.getOrDefault(r.body.take(100));private fun String.digits()=filter{it.isDigit()}
}