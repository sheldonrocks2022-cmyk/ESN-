package com.esn.jarvis

import android.content.Context
import java.util.Locale

object JarvisBrain {
    private const val PREFS="jarvis_brain"

    fun respond(context:Context,raw:String):String{
        val text=raw.trim()
        val lower=text.lowercase(Locale.US)
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val previous=prefs.getString("last_user","").orEmpty()
        val note=prefs.getString("note","").orEmpty()
        val recent=recentCommands(context)
        val response=when{
            lower in setOf("who are you","what are you")->"I'm JARVIS, your local Android assistant. My core functions run without an external AI service."
            lower.contains("what did i just say")||lower.contains("what was my last question")->if(previous.isBlank())"We haven't established conversation context yet." else "You said, $previous."
            lower.startsWith("remember that ")->{val saved=text.substringAfter("remember that ").trim();prefs.edit().putString("note",saved).apply();"Remembered locally."}
            lower=="what do you remember"||lower=="what did i ask you to remember"->if(note.isBlank())"You haven't asked me to remember anything yet." else "You asked me to remember: $note."
            lower=="forget that"||lower=="forget what i told you"->{prefs.edit().remove("note").apply();"Local note cleared."}
            lower.startsWith("thanks")||lower=="thank you"->listOf("You're welcome.","Of course.","Anytime.").random()
            lower.contains("how are you")->"All systems are operational."
            lower.contains("are you there")->"At your service."
            lower.contains("what did i do recently")||lower.contains("what have i done recently")||lower=="recent commands"->if(recent.isEmpty())"There is no recent command history yet." else "Recently: "+recent.takeLast(5).joinToString(", ")+"."
            lower.contains("what was my last command")->recent.lastOrNull()?.let{"Your last command was: $it."}?:"There is no recent command history yet."
            lower in setOf("do that again","repeat that","same again")->if(recent.isEmpty())"There is no recent command to reference." else "Your previous command was ${recent.last()}. Say repeat that command if you want me to execute it."
            else->"I don't have a local handler for that yet. I can still control supported phone functions, apps, messages, notifications, media, reminders, routines, and screen actions."
        }
        prefs.edit().putString("last_user",text).putString("last_response",response).apply()
        return response
    }
    private fun recentCommands(context:Context):List<String>{val raw=context.getSharedPreferences("jarvis_history",Context.MODE_PRIVATE).getString("items","").orEmpty();return raw.lines().filter{it.isNotBlank()}.mapNotNull{it.split("|",limit=3).getOrNull(1)}.takeLast(10)}
}
