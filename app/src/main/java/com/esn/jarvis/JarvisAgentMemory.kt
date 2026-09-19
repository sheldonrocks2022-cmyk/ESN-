package com.esn.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
object JarvisAgentMemory{
 private const val PREFS="jarvis_agent";private const val KEY="structured_history"
 fun record(c:Context,goal:String,trace:List<String>,success:Boolean=true){val p=c.getSharedPreferences(PREFS,0);val a=try{JSONArray(p.getString(KEY,"[]"))}catch(_:Throwable){JSONArray()};val out=JSONArray();val start=(a.length()-79).coerceAtLeast(0);for(i in start until a.length())a.optJSONObject(i)?.let{out.put(it)};out.put(JSONObject().put("time",System.currentTimeMillis()).put("goal",goal.take(400)).put("success",success).put("trace",trace.joinToString(" > ").take(3000)));p.edit().putString(KEY,out.toString()).putString("last_goal",goal.take(400)).putString("history",legacy(out)).apply()}
 fun lastGoal(c:Context)=c.getSharedPreferences(PREFS,0).getString("last_goal","").orEmpty()
 fun summary(c:Context):String{val a=load(c);if(a.length()==0)return "No learned agent runs yet.";val s=mutableListOf<String>();for(i in (a.length()-10).coerceAtLeast(0) until a.length()){val o=a.optJSONObject(i)?:continue;s+=((if(o.optBoolean("success"))"success" else "failed")+": "+o.optString("goal").take(160))};return s.joinToString(" | ")}
 fun relevant(c:Context,goal:String):String{val words=goal.lowercase().split(Regex("\\W+")).filter{it.length>2}.toSet();val a=load(c);val scored=mutableListOf<Pair<Int,String>>();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val g=o.optString("goal");val score=g.lowercase().split(Regex("\\W+")).count{it in words};if(score>0)scored+=score to ((if(o.optBoolean("success"))"worked: " else "failed: ")+g+" -> "+o.optString("trace").take(500))};return scored.sortedByDescending{it.first}.take(4).joinToString(" | "){it.second}}
 fun clear(c:Context):String{c.getSharedPreferences(PREFS,0).edit().remove(KEY).remove("history").remove("last_goal").apply();return "Agent memory cleared."}
 private fun load(c:Context)=try{JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"))}catch(_:Throwable){JSONArray()}
 private fun legacy(a:JSONArray):String{val x=mutableListOf<String>();for(i in (a.length()-30).coerceAtLeast(0) until a.length()){val o=a.optJSONObject(i)?:continue;x+=o.optLong("time").toString()+"|"+o.optString("goal")+"|"+o.optString("trace")};return x.joinToString("\n")}
}