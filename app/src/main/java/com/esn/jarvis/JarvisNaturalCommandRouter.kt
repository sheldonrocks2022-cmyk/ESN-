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

    private fun looksConversational(text:String):Boolean =
        text.startsWith("who ") || text.startsWith("what ") || text.startsWith("why ") || text.startsWith("how ") ||
        text.startsWith("when ") || text.startsWith("where ") || text.startsWith("can you explain") ||
        text.startsWith("tell me about") || text.startsWith("remember that ") || text.contains("thank you") ||
        text.contains("what do you remember") || text.contains("recent commands") || text.contains("last command") ||
        text.contains("what did i do recently") || text.contains("what have i done recently") || text.contains("context")

    private fun executeSingle(context: Context, text: String): String? = when {
        text.startsWith("create alias ") && text.contains(" for ") -> { val name=text.substringAfter("create alias ").substringBefore(" for ").trim(); val command=text.substringAfter(" for ").trim(); JarvisAliases.save(context,name,command) }
        text == "list aliases" || text == "what are my aliases" -> JarvisAliases.list(context)
        text.startsWith("delete alias ") -> JarvisAliases.delete(context,text.removePrefix("delete alias ").trim())
        text == "clear aliases" -> JarvisAliases.clear(context)
        text == "clear local context" || text == "forget recent context" -> { JarvisContext.clear(context); "Recent local context cleared." }
        text in setOf("read all notifications aloud","turn on notification reading","announce notifications") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("read_all_notifications",true).apply(); "I will read incoming notifications aloud." }
        text in setOf("stop reading notifications aloud","turn off notification reading","silence notifications") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("read_all_notifications",false).apply(); "Automatic notification reading is off." }
        text.startsWith("when i say ") && text.contains(" do ") -> saveRoutine(context,text)
        text.startsWith("when ") && text.contains(" do ") -> { val event=text.substringAfter("when ").substringBefore(" do ").trim();val action=text.substringAfter(" do ").trim();JarvisAutomationEngine.setTrigger(context,event,action) }
        text == "list automations" || text == "list triggers" -> JarvisAutomationEngine.list(context)
        text.startsWith("create routine ") && text.contains(" to ") -> saveNamedRoutine(context,text)
        text == "list routines" || text == "what are my routines" -> listRoutines(context)
        text.startsWith("delete routine ") -> deleteRoutine(context,text.removePrefix("delete routine ").trim())
        text.startsWith("edit routine ") && text.contains(" to ") -> saveNamedRoutine(context,text.replaceFirst("edit routine ","create routine "))
        text.startsWith("run routine ") -> execute(context,text.removePrefix("run routine ").trim())
        text == "do that again" || text == "repeat that command" || text == "do it again" -> repeatLast(context)
        text == "what were we doing" || text == "what was i doing" -> { val recent=JarvisContext.recent(context);if(recent.isBlank())"I do not have recent command context." else "Recently you asked me to $recent." }
        text == "call them back" || text == "call them again" -> callCurrentContact(context)
        text == "message them again" || text == "text them again" -> { val contact=JarvisContext.recall(context,"contact"); if(contact.isBlank()) "I don't have a recent contact in context." else "Tell me what you want to say to $contact." }
        text == "bluetooth status" || text == "is bluetooth connected" -> if(context.getSharedPreferences("jarvis_bluetooth",Context.MODE_PRIVATE).getBoolean("connected",false)) "A Bluetooth device is connected." else "I don't currently detect a Bluetooth device connection."
        text.startsWith("jarvis agent ") -> JarvisAgent.execute(context,text.removePrefix("jarvis agent ").trim())
        text.startsWith("agent ") -> JarvisAgent.execute(context,text.removePrefix("agent ").trim())
        text.startsWith("do this on screen ") -> JarvisAgent.execute(context,text.removePrefix("do this on screen ").trim())
        text.startsWith("discord tap ") -> JarvisDiscordPhoneControl.tap(context,text.removePrefix("discord tap ").trim())
        text.startsWith("discord open server ") -> JarvisDiscordPhoneControl.openServer(context,text.removePrefix("discord open server ").trim())
        text.startsWith("discord ban ") -> JarvisDiscordPhoneControl.ban(context,text.removePrefix("discord ban ").trim())
        text.startsWith("discord kick ") -> JarvisDiscordPhoneControl.kick(context,text.removePrefix("discord kick ").trim())
        text.startsWith("discord timeout ") -> JarvisDiscordPhoneControl.timeout(context,text.removePrefix("discord timeout ").trim())
        text.startsWith("discord delete channel ") -> JarvisDiscordPhoneControl.deleteChannel(context,text.removePrefix("discord delete channel ").trim())
        text == "confirm discord action" -> JarvisDiscordPhoneControl.confirm(context)
        text == "help" || text == "what can you do" || text == "what can i say" -> "I can open apps, send messages, control supported phone functions, read notifications, inspect your screen, run routines, chain commands, and maintain recent conversation context."
        text == "are you there" || text == "you there" || text == "hello" || text == "hey" -> "At your service."
        text.contains("what am i looking at") || text.contains("what is on my screen") || text.contains("read this screen") || text.contains("describe my screen") -> JarvisScreenInspector.describeScreen()
        text.startsWith("do you see ") -> if(JarvisScreenInspector.hasText(text.removePrefix("do you see ").trim())) "Yes, I can see that on the current screen." else "I don't see that on the current screen."
        text.startsWith("tap on screen ") -> JarvisScreenInspector.tapText(text.removePrefix("tap on screen ").trim())
        text == "what controls can you see" || text == "what buttons can you see" -> JarvisScreenInspector.listControls()
        text.matches(Regex("tap (the )?(\\d+)(st|nd|rd|th)?( item| result| button)?")) -> JarvisScreenInspector.tapNumber(Regex("\\d+").find(text)?.value?.toIntOrNull()?:1)
        text.startsWith("scroll until you see ") -> JarvisScreenInspector.scrollTo(text.removePrefix("scroll until you see ").trim())
        text.startsWith("find and tap ") -> JarvisScreenInspector.scrollTo(text.removePrefix("find and tap ").trim())
        text.startsWith("type ") && text.contains(" into ") -> { val value=text.substringAfter("type ").substringBefore(" into ").trim();val field=text.substringAfter(" into ").trim();JarvisScreenInspector.typeInto(field,value) }
        text == "run diagnostics" || text == "system diagnostics" || text == "system health" -> JarvisDiagnostics.report(context)
        text == "recover jarvis" || text == "repair jarvis" -> JarvisDiagnostics.recover(context)
        text in setOf("pause music","play music","resume music","play pause") -> JarvisMediaControl.playPause(context)
        text in setOf("next song","next track","skip song","skip track") -> JarvisMediaControl.next(context)
        text in setOf("previous song","previous track","go back a song") -> JarvisMediaControl.previous(context)
        text == "stop music" -> JarvisMediaControl.stop(context)
        text == "last automation event" -> context.getSharedPreferences("jarvis_automation",Context.MODE_PRIVATE).getString("last_event","No system event recorded yet.").orEmpty()
        text == "social briefing" || text == "run my social life" || text == "social status" -> JarvisSocialManager.briefing(context)
        text == "social follow ups" || text == "who should i follow up with" -> JarvisSocialManager.followUps(context)
        text == "important social dates" || text == "birthdays and important dates" -> JarvisSocialManager.dates(context)
        text == "who have i talked to recently" || text == "recent people" -> JarvisSocialManager.recentPeople(context)
        text.startsWith("remember about ") && text.contains(" that ") -> {val person=text.substringAfter("remember about ").substringBefore(" that ").trim();val note=text.substringAfter(" that ").trim();JarvisSocialManager.note(context,person,note)}
        text.startsWith("what do you know about ") -> JarvisSocialManager.about(context,text.removePrefix("what do you know about ").trim())
        text.startsWith("follow up with ") && text.contains(" ") -> {val rest=text.removePrefix("follow up with ");val parts=rest.split(Regex("\\s+(tomorrow|tonight|next|in )"),limit=2);if(parts.size<2)"Tell me when to follow up." else JarvisSocialManager.followUp(context,parts[0].trim(),rest.removePrefix(parts[0]).trim())}
        text.startsWith("clear social data for ") -> JarvisSocialManager.clearPerson(context,text.removePrefix("clear social data for ").trim())
        text == "what did i miss" || text == "summarize my notifications" -> JarvisNotificationListenerService.summary(context)
        text.startsWith("mute notifications from ") -> JarvisNotificationListenerService.muteSource(context,text.removePrefix("mute notifications from ").trim())
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
        text.startsWith("draft message to ") && text.contains(" saying ") -> { val r=text.substringAfter("draft message to ").substringBefore(" saying ").trim();val b=text.substringAfter(" saying ").trim();JarvisContext.saveDraft(context,r,b);"Draft saved for $r." }
        text == "read my draft" -> { val r=JarvisContext.draftRecipient(context);val b=JarvisContext.draftBody(context);if(r.isBlank()||b.isBlank())"There is no message draft." else "Draft to $r: $b" }
        text == "send the draft" || text == "send it" -> { val r=JarvisContext.draftRecipient(context);val b=JarvisContext.draftBody(context);if(r.isBlank()||b.isBlank())"There is no message draft." else JarvisMessaging.execute(context,"send a message to $r saying $b").also{if(it.startsWith("Message sent"))JarvisContext.clearDraft(context)} }
        text == "call him" || text == "call her" || text == "call them" -> callCurrentContact(context)
        text == "go to settings" || text == "take me to settings" -> open(context, Settings.ACTION_SETTINGS, "Settings opened.")
        text == "go home" || text == "take me home" -> if (JarvisAccessibilityService.home()) "Going home." else "Phone Access is not enabled."
        text == "go back" || text == "take me back" -> if (JarvisAccessibilityService.back()) "Going back." else "Phone Access is not enabled."
        text in setOf("use male voice","switch to male voice","male voice") -> {context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putString("voice_gender","male").remove("tts_voice").apply();"Male voice selected. Restart JARVIS to apply it."}
        text in setOf("use female voice","switch to female voice","female voice") -> {context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putString("voice_gender","female").remove("tts_voice").apply();"Female voice selected. Restart JARVIS to apply it."}
        text.contains("speak slower") || text.contains("talk slower") -> setSpeechRate(context,-0.08f,"Speech speed reduced.")
        text.contains("speak faster") || text.contains("talk faster") -> setSpeechRate(context,0.08f,"Speech speed increased.")
        text.contains("normal speech") || text.contains("normal speed") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putFloat("speech_rate",0.76f).apply(); "Speech speed restored." }
        text == "good morning" || text.contains("morning briefing") -> morningBriefing(context)
        text.contains("good night") || text.contains("bedtime mode") || text.contains("going to bed") -> bedtime(context)
        text.contains("gaming mode") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("gaming_mode",true).apply(); "Gaming mode enabled." }
        text.contains("work mode") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("gaming_mode",false).apply(); "Work mode enabled." }
        else -> null
    }

    private fun repeatLast(context:Context):String{val raw=context.getSharedPreferences("jarvis_history",Context.MODE_PRIVATE).getString("items","").orEmpty();val last=raw.lines().filter{it.isNotBlank()}.lastOrNull()?.split("|",limit=3)?.getOrNull(1).orEmpty();return if(last.isBlank()||last=="do that again"||last=="repeat that command")"I don't have a previous command to repeat." else execute(context,last) ?: JarvisCommandEngine.execute(context,last)}
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
