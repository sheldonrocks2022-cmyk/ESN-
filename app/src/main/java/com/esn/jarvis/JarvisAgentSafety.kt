package com.esn.jarvis
import android.content.Context
object JarvisAgentSafety{
 private const val PREFS="jarvis_agent_pending"
 private val risky=Regex("\\b(send|post|publish|delete|remove|uninstall|buy|purchase|pay|transfer|ban|kick|timeout|block|report|reset|factory|password|permission|call|dial|message|text|upload)\\b",RegexOption.IGNORE_CASE)
 fun authorize(c:Context,goal:String):String?{if(!risky.containsMatchIn(goal))return null;c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("goal",goal).putLong("until",System.currentTimeMillis()+60000).apply();return "That action can change data, communicate, spend money, or affect an account. Say confirm agent action within 60 seconds to continue."}
 fun confirm(c:Context):String{val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val g=p.getString("goal","").orEmpty();val until=p.getLong("until",0);p.edit().clear().apply();return if(g.isBlank()||System.currentTimeMillis()>until)"No agent action is awaiting confirmation." else JarvisAgent.executeConfirmed(c,g)}
 fun cancel(c:Context):String{c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();return "Pending agent action cancelled."}
}