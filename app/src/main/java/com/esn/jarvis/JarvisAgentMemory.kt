package com.esn.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
object JarvisAgentMemory{
 private const val PREFS="jarvis_agent";private const val KEY="structured_history"
 fun record(c:Context,goal:String,trace:List<String>,success:Boolean=true){val p=c.getSharedPreferences(PREFS,0);val a=try{JSONArray(p.getString(KEY,"[]"))}catch(_:Throwable){JSONArray()};val out=JSONArray();val start=(a.length()-39).coerceAtLeast(0);for(i in start until a.length())out.put(a.optJSONObject(i));out.put(JSONObject().put("time",System.currentTimeMillis()).put("goal",goal.take(300)).put("success",success).put("trace",trace.joinToString(" > ").take(1800)));p.edit().putString(KEY,out.toString()).putString("last_goal",goal.take(300)).putString("history",legacy(out)).apply()}
 fun lastGoal(c:Context)=c.getSharedPreferences(PREFS,0).getString("last_goal","").orEmpty()
 fun summary(c:Context):String{val a=try{JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"))}catch(_:Throwable){JSONArray()};if(a.length()==0)return "No learned agent runs yet.";val s=mutableListOf<String>();for(i in (a.length()-6).coerceAtLeast(0) until a.length()){val o=a.optJSONObject(i)?:continue;s+=((if(o.optBoolean("success"))"success" else "failed")+": "+o.optString("goal").take(140))};return s.joinToString(" | ")}
 fun clear(c:Context):String{c.getSharedPreferences(PREFS,0).edit().remove(KEY).remove("history").remove("last_goal").apply();return "Agent memory cleared."}
 private fun legacy(a:JSONArray):String{val x=mutableListOf<String>();for(i in (a.length()-20).coerceAtLeast(0) until a.length()){val o=a.optJSONObject(i)?:continue;x+=o.optLong("time").toString()+"|"+o.optString("goal")+"|"+o.optString("trace")};return x.joinToString("\n")}
}