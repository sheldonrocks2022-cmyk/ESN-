package com.esn.jarvis

import android.content.Context

object JarvisContext {
    private const val PREFS="jarvis_context"
    fun remember(context:Context,key:String,value:String){if(value.isNotBlank())context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key,value).apply()}
    fun recall(context:Context,key:String)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,"").orEmpty()
    fun clear(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply()}
}
