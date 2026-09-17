package com.esn.jarvis

import android.content.Context

object JarvisAliases {
    private const val PREFS="jarvis_aliases"
    fun save(context:Context,name:String,command:String):String{if(name.isBlank()||command.isBlank())return "I need an alias name and command.";context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(name,command).apply();return "Alias $name saved locally."}
    fun resolve(context:Context,name:String)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(name,"").orEmpty()
    fun list(context:Context):String{val keys=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).all.keys.sorted();return if(keys.isEmpty())"You don't have any local aliases." else "Your aliases are: "+keys.joinToString(", ")+ "."}
    fun delete(context:Context,name:String):String{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(!p.contains(name))return "I couldn't find alias $name.";p.edit().remove(name).apply();return "Alias $name deleted."}
    fun clear(context:Context):String{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();return "All local aliases cleared."}
}
