package com.esn.jarvis

import android.content.Context

object JarvisAutomationEngine {
    private const val PREFS="jarvis_automation"
    fun setTrigger(context:Context,event:String,command:String):String{
        if(event.isBlank()||command.isBlank())return "I need both a trigger and an action."
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(event.lowercase(),command).apply()
        return "Automation saved for $event."
    }
    fun fire(context:Context,event:String):String?{
        val command=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(event.lowercase(),"").orEmpty()
        if(command.isBlank())return null
        return JarvisNaturalCommandRouter.execute(context,command)?:JarvisCommandEngine.execute(context,command)
    }
    fun list(context:Context):String{
        val keys=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).all.keys.sorted()
        return if(keys.isEmpty())"No event automations are configured." else "Automation triggers: "+keys.joinToString(", ")+"."
    }
}
