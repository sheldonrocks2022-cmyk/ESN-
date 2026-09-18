package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

object JarvisNaturalCommandRouter {
    fun execute(context: Context, raw: String): String? = executeSafe(context,raw,0,mutableSetOf())
    private fun executeSafe(context:Context,raw:String,depth:Int,seen:MutableSet<String>):String? {
        if(depth>8)return "Routine stopped because it exceeded the safety limit."
        val text = normalize(raw)
        if (text.isBlank()) return "I didn't catch that."
        val alias=JarvisAliases.resolve(context,text)
        if(alias.isNotBlank()){if(!seen.add(text))return "Routine stopped because a loop was detected.";return executeSafe(context,alias,depth+1,seen)}
        val routine=context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).getString(text,"").orEmpty()
        if(routine.isNotBlank()){if(!seen.add("routine:$text"))return "Routine stopped because a loop was detected.";return executeSafe(context,routine,depth+1,seen)}
        val parts = text.split(Regex("\\s+(?:and then|then|after that)\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size in 2..6) return parts.joinToString(" ") { executeSafe(context,it,depth+1,seen) ?: JarvisCommandEngine.execute(context,it) }
        return executeSingle(context,text) ?: if (looksConversational(text)) JarvisBrain.respond(context, raw) else null
    }

    private fun executePart(context:Context,text:String):String {
        val alias=context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).getString(text,"").orEmpty()
        if(alias.isNotBlank()) return alias.split(Regex("\\s+(?:and then|then|after that)\\s+")).filter{it.isNotBlank()}.take(8).joinToString(" ") { executePart(context,it.trim()) }
        return if(JarvisMessaging.canHandle(text)) JarvisMessaging.execute(context,text) else executeSingle(context,text) ?: JarvisCommandEngine.execute(context,text)
    }

    private fun looksConversational(text:String):Boolean =
        text.startsWith("who ") || text.startsWith("what ") || text.startsWith("why ") || text.startsWith("how ") ||
        text.startsWith("when ") || text.startsWith("where ") || text.startsWith("can you explain") ||
        text.startsWith("tell me about") || text.startsWith("remember that ") || text.contains("thank you") ||
        text.contains("what do you remember")

    private fun executeSingle(context: Context, text: String): String? = when {
        text.startsWith("create alias ") && text.contains(" for ") -> { val name=text.substringAfter("create alias ").substringBefore(" for ").trim(); val command=text.substringAfter(" for ").trim(); JarvisAliases.save(context,name,command) }
        text == "list aliases" || text == "what are my aliases" -> JarvisAliases.list(context)
        text.startsWith("delete alias ") -> JarvisAliases.delete(context,text.removePrefix("delete alias ").trim())
        text == "clear aliases" -> JarvisAliases.clear(context)
        text == "clear local context" || text == "forget recent context" -> { JarvisContext.clear(context); "Recent local context cleared." }
        text.startsWith("when i say ") && text.contains(" do ") -> saveRoutine(context,text)
        text.startsWith("create routine ") && text.contains(" to ") -> saveNamedRoutine(context,text)
        text == "list routines" || text == "what are my routines" -> listRoutines(context)
        text.startsWith("delete routine ") -> deleteRoutine(context,text.removePrefix("delete routine ").trim())
        text.startsWith("edit routine ") && text.contains(" to ") -> saveNamedRoutine(context,text.replaceFirst("edit routine ","create routine "))
        text.startsWith("run routine ") -> execute(context,text.removePrefix("run routine ").trim())
        text == "help" || text == "what can you do" || text == "what can i say" -> "I can open apps, send messages, control supported phone functions, read notifications, inspect your screen, run routines, chain commands, and maintain recent conversation context."
        text == "are you there" || text == "you there" || text == "hello" || text == "hey" -> "At your service."
        text.contains("what am i looking at") || text.contains("what is on my screen") || text.contains("read this screen") || text.contains("describe my screen") -> JarvisScreenInspector.describeScreen()
        text.startsWith("do you see ") -> if(JarvisScreenInspector.hasText(text.removePrefix("do you see ").trim())) "Yes, I can see that on the current screen." else "I don't see that on the current screen."
        text.startsWith("tap on screen ") -> JarvisScreenInspector.tapText(text.removePrefix("tap on screen ").trim())
        text == "open notifications" || text == "show notifications" || text == "pull down notifications" -> if (JarvisAccessibilityService.notifications()) "Opening notifications." else "Phone Access is not enabled."
        text == "open quick settings" || text == "show quick settings" -> if (JarvisAccessibilityService.quickSettings()) "Opening quick settings." else "Phone Access is not enabled."
        text.contains("read my messages") || text.contains("read my texts") || text.contains("read my sms") || text.contains("read my dms") -> JarvisNotificationListenerService.readLatestMessages(context)
        text == "read it" || text == "read that" || text == "read the latest message" -> JarvisNotificationListenerService.readLatestMessage(context)
        text.contains("any new messages") || text.contains("what did i miss") || text.contains("any new texts") || text.contains("unread messages") -> JarvisNotificationListenerService.readLatestMessages(context)
        text.startsWith("read notifications from ") -> JarvisNotificationListenerService.readMatching(context,text.removePrefix("read notifications from ").trim())
        text.startsWith("read messages from ") -> JarvisNotificationListenerService.readMatching(context,text.removePrefix("read messages from ").trim())
        text.startsWith("dismiss notification from ") -> JarvisNotificationListenerService.dismissMatching(text.removePrefix("dismiss notification from ").trim())
        text.contains("read my notifications") || text.contains("show me my notifications") -> JarvisNotificationListenerService.readLatestMessages(context)
        text.contains("notification access") || text.contains("let you read my notifications") || text.contains("enable notification access") -> JarvisNotificationListenerService.notificationAccessSettings(context)
        text == "who sent that" || text == "who was that" -> JarvisNotificationListenerService.latestSender(context)
        text.startsWith("reply ") || text.startsWith("reply that ") -> replyNotification(text)
        text == "dismiss that notification" || text == "dismiss latest notification" || text == "clear that notification" -> JarvisNotificationListenerService.dismissLatest()
        text.startsWith("tell him ") || text.startsWith("tell her ") || text.startsWith("message him ") || text.startsWith("message her ") -> messageCurrentContact(context,text)
        text == "call him" || text == "call her" || text == "call them" -> callCurrentContact(context)
        text == "go to settings" || text == "take me to settings" -> open(context, Settings.ACTION_SETTINGS, "Settings opened.")
        text == "go home" || text == "take me home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access is not enabled."
        text == "go back" || text == "take me back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access is not enabled."
        text.contains("speak slower") || text.contains("talk slower") -> setSpeechRate(context,-0.08f,"Speech speed reduced.")
        text.contains("speak faster") || text.contains("talk faster") -> setSpeechRate(context,0.08f,"Speech speed increased.")
        text.contains("normal speech") || text.contains("normal speed") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putFloat("speech_rate",0.76f).apply(); "Speech speed restored." }
        text == "good morning" || text.contains("morning briefing") -> morningBriefing(context)
        text.contains("good night") || text.contains("bedtime mode") || text.contains("going to bed") -> bedtime(context)
        text.contains("gaming mode") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("gaming_mode",true).apply(); "Gaming mode enabled." }
        text.contains("work mode") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("gaming_mode",false).apply(); "Work mode enabled." }
        else -> null
    }

    private fun messageCurrentContact(context:Context,text:String):String{val contact=JarvisContext.recall(context,"contact");if(contact.isBlank())return "I don't have a recent contact in context.";val body=text.replaceFirst(Regex("^(tell|message) (him|her|them)\\s*"),"").trim();if(body.isBlank())return "Tell me what you want to say.";return JarvisMessaging.execute(context,"send a message to $contact saying $body")}
    private fun callCurrentContact(context:Context):String{val contact=JarvisContext.recall(context,"contact");return if(contact.isBlank())"I don't have a recent contact in context." else JarvisCommandEngine.execute(context,"call $contact")}

    private fun saveRoutine(context:Context,text:String):String{val phrase=text.substringAfter("when i say ").substringBefore(" do ").trim();val actions=text.substringAfter(" do ").trim();if(phrase.isBlank()||actions.isBlank())return "I need both a phrase and an action.";context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).edit().putString(phrase,actions).apply();return "Routine saved for $phrase."}
    private fun saveNamedRoutine(context:Context,text:String):String{val name=text.substringAfter("create routine ").substringBefore(" to ").trim();val actions=text.substringAfter(" to ").trim();if(name.isBlank()||actions.isBlank())return "I need a routine name and actions.";context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).edit().putString(name,actions).apply();return "Routine $name saved."}
    private fun listRoutines(context:Context):String{val names=context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).all.keys.sorted();return if(names.isEmpty())"You don't have any custom routines yet." else "Your routines are: "+names.joinToString(", ")+ "."}
    private fun deleteRoutine(context:Context,name:String):String{val p=context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE);if(!p.contains(name))return "I couldn't find that routine.";p.edit().remove(name).apply();return "Routine $name deleted."}

    private fun normalize(raw:String)=raw.lowercase(Locale.US).replace(Regex("[^a-z0-9%' ]")," ").replace(Regex("\\s+")," ").trim()
    private fun replyNotification(raw:String):String{val reply=raw.replaceFirst(Regex("(?i)^reply( that)?\\s*"),"").trim();return JarvisNotificationListenerService.replyLatest(reply)}
    private fun setSpeechRate(context:Context,delta:Float,response:String):String{val prefs=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE);prefs.edit().putFloat("speech_rate",(prefs.getFloat("speech_rate",0.76f)+delta).coerceIn(0.60f,1.05f)).apply();return response}
    private fun morningBriefing(context:Context):String="Good morning. ${JarvisNotificationListenerService.readLatestMessages(context)} ${JarvisCommandEngine.execute(context,"battery status")}"
    private fun bedtime(context:Context):String=try{context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));"Bedtime mode ready. Sound settings opened for your confirmation."}catch(_:Exception){"I couldn't open the sound settings."}
    private fun open(context:Context,action:String,response:String):String=try{context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));response}catch(_:Exception){"I couldn't open that setting."}
}
