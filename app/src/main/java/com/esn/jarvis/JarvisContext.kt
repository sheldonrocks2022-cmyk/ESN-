package com.esn.jarvis

import android.content.Context

object JarvisContext {
    private const val PREFS="jarvis_context"
    fun remember(context:Context,key:String,value:String){if(value.isNotBlank())context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key,value).apply()}
    fun recall(context:Context,key:String)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,"").orEmpty()
    fun rememberCommand(context:Context,command:String,result:String){val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);p.edit().putString("previous_command",p.getString("current_command","").orEmpty()).putString("current_command",command).putString("current_result",result).putLong("context_time",System.currentTimeMillis()).apply()}
    fun recent(context:Context):String{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val current=p.getString("current_command","").orEmpty();val previous=p.getString("previous_command","").orEmpty();return listOf(previous,current).filter{it.isNotBlank()}.joinToString(" then ")}
    fun clear(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply()}
}
