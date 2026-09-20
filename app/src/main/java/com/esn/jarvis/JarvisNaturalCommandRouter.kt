package com.esn.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

object JarvisNaturalCommandRouter {
    fun execute(context: Context, raw: String): String? = executeSafe(context,raw,0,mutableSetOf())
    private fun executeSafe(context:Context,raw:String,depth:Int,seen:MutableSet<String>):String? {
        if(depth>8)return "Routine stopped because it exceeded the safety limit."
        val text = normalize(JarvisIntelligenceCore.resolve(context,raw))
        if (text.isBlank()) return "I didn't catch that."
        val alias=JarvisAliases.resolve(context,text)
        if(alias.isNotBlank()){if(!seen.add(text))return "Routine stopped because a loop was detected.";return executeSafe(context,alias,depth+1,seen)}
        JarvisPersonalMemory.learnCorrection(context,text)?.let{return it}
        val routine=context.getSharedPreferences("jarvis_routines",Context.MODE_PRIVATE).getString(text,"").orEmpty()
        if(routine.isNotBlank()){if(!seen.add("routine:$text"))return "Routine stopped because a loop was detected.";return executeSafe(context,routine,depth+1,seen)}
        val parts = text.split(Regex("\\s+(?:and then|then|after that)\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size in 2..6) return parts.joinToString(" ") { executeSafe(context,it,depth+1,seen) ?: JarvisCommandEngine.execute(context,it) }
        JarvisIntelligenceCore.route(context,text)?.let{ JarvisIntelligenceCore.observe(context,text,it); return it }
        val result=executeSingle(context,text) ?: if (looksConversational(text)) JarvisBrain.respond(context, raw) else null
        if(result!=null)JarvisIntelligenceCore.observe(context,text,result)
        return result
    }

    private fun looksConversational(text:String):Boolean =
        text.startsWith("who ") || text.startsWith("what ") || text.startsWith("why ") || text.startsWith("how ") ||
        text.startsWith("when ") || text.startsWith("where ") || text.startsWith("can you explain") ||
        text.startsWith("tell me about") || text.startsWith("remember that ") || text.contains("thank you") ||
        text.contains("what do you remember") || text.contains("recent commands") || text.contains("last command") ||
        text.contains("what did i do recently") || text.contains("what have i done recently") || text.contains("context")

    private fun executeSingle(context: Context, text: String): String? {
        JarvisPersonalMemory.observe(context,text)
        JarvisProactiveIntelligence.observeSequence(context,text)
        naturalDiscord(context,text)?.let{return it}
        return when {
        text.startsWith("create alias ") && text.contains(" for ") -> { val name=text.substringAfter("create alias ").substringBefore(" for ").trim(); val command=text.substringAfter(" for ").trim(); JarvisAliases.save(context,name,command) }
        text == "list aliases" || text == "what are my aliases" -> JarvisAliases.list(context)
        text.startsWith("delete alias ") -> JarvisAliases.delete(context,text.removePrefix("delete alias ").trim())
        text == "clear aliases" -> JarvisAliases.clear(context)
        text == "clear local context" || text == "forget recent context" -> { JarvisContext.clear(context); "Recent local context cleared." }
        text in setOf("intelligence core status","brain status","jarvis brain status") -> JarvisIntelligenceCore.status(context)
        text in setOf("clear intelligence context","clear brain context") -> JarvisIntelligenceCore.clear(context)
        text in setOf("what do you remember","what do you remember about me","personal memory") -> JarvisPersonalMemory.summary(context)
        text.startsWith("remember that ") && text.contains(" is ") -> {val x=text.removePrefix("remember that ");JarvisPersonalMemory.remember(context,x.substringBefore(" is ").trim(),x.substringAfter(" is ").trim())}
        text.startsWith("forget ") -> JarvisPersonalMemory.forget(context,text.removePrefix("forget ").trim())
        text == "clear personal memory" -> JarvisPersonalMemory.clear(context)
        text in setOf("catch me up","give me a briefing","jarvis briefing","brief me") -> JarvisPersonalMemory.briefing(context)
        text in setOf("what should i automate","suggest a routine","routine suggestion") -> JarvisProactiveIntelligence.suggestion(context)
        text.startsWith("make ") && text.endsWith(" a priority contact") -> JarvisProactiveIntelligence.priority(context,text.removePrefix("make ").removeSuffix(" a priority contact").trim(),true)
        text.startsWith("remove ") && text.endsWith(" as a priority contact") -> JarvisProactiveIntelligence.priority(context,text.removePrefix("remove ").removeSuffix(" as a priority contact").trim(),false)
        text in setOf("why did you alert me","why did you tell me that","why was that important") -> JarvisProactiveIntelligence.whyAlert(context)
        text in setOf("priority briefing","important briefing","what is important") -> JarvisProactiveIntelligence.briefing(context)
        text in setOf("what have you learned about me","what have you learned") -> JarvisProactiveIntelligence.learned(context)
        text in setOf("clear proactive learning","forget learned behavior") -> JarvisProactiveIntelligence.clear(context)
        text in setOf("read all notifications aloud","turn on notification reading","announce notifications") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("read_all_notifications",true).apply(); "I will read incoming notifications aloud." }
        text in setOf("stop reading notifications aloud","turn off notification reading","silence notifications") -> { context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putBoolean("read_all_notifications",false).apply(); "Automatic notification reading is off." }
        text.startsWith("when i say ") && text.contains(" do ") -> saveRoutine(context,text)
        text.startsWith("when ") && text.contains(" do ") -> { val event=text.substringAfter("when ").substringBefore(" do ").trim();val action=text.substringAfter(" do ").trim();JarvisAutomationEngine.setTrigger(context,event,action) }
        text == "list automations" || text == "list triggers" -> JarvisAutomationEngine.list(context)
        text in setOf("pause automations","pause all automations") -> JarvisAutomationEngine.pause(context,true)
        text in setOf("resume automations","resume all automations") -> JarvisAutomationEngine.pause(context,false)
        text in setOf("automation history","what did you do automatically today","what did you do automatically") -> JarvisAutomationEngine.history(context)
        text in setOf("why did you do that","why did that automation run") -> JarvisAutomationEngine.why(context)
        text in setOf("confirm automation","confirm automatic action") -> JarvisAutomationEngine.confirm(context)
        text.startsWith("create routine ") && text.contains(" to ") -> saveNamedRoutine(context,text)
        text == "list routines" || text == "what are my routines" -> listRoutines(context)
        text.startsWith("delete routine ") -> deleteRoutine(context,text.removePrefix("delete routine ").trim())
        text.startsWith("edit routine ") && text.contains(" to ") -> saveNamedRoutine(context,text.replaceFirst("edit routine ","create routine "))
        text.startsWith("run routine ") -> execute(context,text.removePrefix("run routine ").trim())
        text == "do that again" || text == "repeat that command" || text == "do it again" -> repeatLast(context)
        text == "what were we doing" || text == "what was i doing" -> { val recent=JarvisContext.recent(context);if(recent.isBlank())"I do not have recent command context." else "Recently you asked me to $recent." }
        text == "call them back" || text == "call them again" -> callCurrentContact(context)
        text == "message them again" || text == "text them again" -> { val contact=JarvisContext.recall(context,"contact"); if(contact.isBlank()) "I don't have a recent contact in context." else "Tell me what you want to say to $contact." }
        text == "bluetooth status" || text == "is bluetooth connected" -> JarvisBluetoothState.status(context)
        text == "agent memory" || text == "what has the agent learned" -> JarvisAgentMemory.summary(context)
        text == "clear agent memory" -> JarvisAgentMemory.clear(context)
        text == "coding workspace" || text == "coding workspace status" -> JarvisCodingWorkspace.status(context)
        text.startsWith("create code file ") && text.contains(" with ") -> { val path=text.substringAfter("create code file ").substringBefore(" with ").trim(); val body=text.substringAfter(" with ").trim(); JarvisCodingWorkspace.write(context,path,body) }
        text.startsWith("read code file ") -> JarvisCodingWorkspace.read(context,text.removePrefix("read code file ").trim())
        text == "list code files" -> JarvisCodingWorkspace.list(context)
        text in setOf("jarvis confirm","confirm") -> confirmPendingAction(context)
        text == "confirm agent action" -> JarvisAgentSafety.confirm(context)
        text == "cancel agent action" -> JarvisAgentSafety.cancel(context)
        text in setOf("stop everything","stop the task","stop agent task","abort task") -> JarvisAgent.stop(context)
        text in setOf("what are you doing","agent status","task status","what is the task status") -> JarvisAgent.status(context)
        text in setOf("resume the task","continue the task","resume agent task","continue what you were doing") -> JarvisAgent.resume(context)
        text.startsWith("jarvis agent ") -> JarvisAgent.execute(context,text.removePrefix("jarvis agent ").trim())
        text.startsWith("agent ") -> JarvisAgent.execute(context,text.removePrefix("agent ").trim())
        text.startsWith("do this on screen ") -> JarvisAgent.execute(context,text.removePrefix("do this on screen ").trim())
        text.startsWith("discord tap ") -> JarvisDiscordPhoneControl.tap(context,text.removePrefix("discord tap ").trim())
        text in setOf("open discord server","open the discord server","open my discord server","discord open server","open server") -> JarvisDiscordPhoneControl.openSavedServer(context)
        text.startsWith("discord open server ") -> JarvisDiscordPhoneControl.openServer(context,text.removePrefix("discord open server ").trim())
        (text.startsWith("ban everyone whose account was created in the last ") || text.startsWith("discord ban everyone whose account was created in the last ")) -> { val days=Regex("""\\d+""").find(text)?.value?.toIntOrNull()?:3;JarvisDiscordPhoneControl.massBanRecentAccounts(context,days) }
        text in setOf("discord recent account scan status","recent account scan status","mass ban scan status") -> JarvisDiscordPhoneControl.recentMassBanStatus(context)
        text.startsWith("discord audit recent accounts ") -> { val rest=text.removePrefix("discord audit recent accounts ").trim();val days=Regex("""\d+""").find(rest)?.value?.toIntOrNull()?:3;JarvisDiscordPhoneControl.recentAccountAudit(context,days,rest) }
        text == "confirm recent account bans" -> JarvisDiscordPhoneControl.confirmRecentAccountBans(context)
        text.startsWith("discord ban ") -> JarvisDiscordPhoneControl.ban(context,text.removePrefix("discord ban ").trim())
        text.startsWith("discord kick ") -> JarvisDiscordPhoneControl.kick(context,text.removePrefix("discord kick ").trim())
        text.startsWith("discord timeout ") -> JarvisDiscordPhoneControl.timeout(context,text.removePrefix("discord timeout ").trim())
        text.startsWith("discord mute ") -> JarvisDiscordPhoneControl.mute(context,text.removePrefix("discord mute ").trim())
        text.startsWith("discord unmute ") -> JarvisDiscordPhoneControl.unmute(context,text.removePrefix("discord unmute ").trim())
        text.startsWith("discord add role ") && text.contains(" to ") -> { val role=text.substringAfter("discord add role ").substringBefore(" to ").trim(); val member=text.substringAfter(" to ").trim(); JarvisDiscordPhoneControl.addRole(context,member,role) }
        text.startsWith("discord remove role ") && text.contains(" from ") -> { val role=text.substringAfter("discord remove role ").substringBefore(" from ").trim(); val member=text.substringAfter(" from ").trim(); JarvisDiscordPhoneControl.removeRole(context,member,role) }
        text.startsWith("discord pin ") -> JarvisDiscordPhoneControl.pin(context,text.removePrefix("discord pin ").trim())
        text.startsWith("discord unpin ") -> JarvisDiscordPhoneControl.unpin(context,text.removePrefix("discord unpin ").trim())
        text.startsWith("discord delete message ") -> JarvisDiscordPhoneControl.deleteMessage(context,text.removePrefix("discord delete message ").trim())
        text.startsWith("discord delete channel ") -> JarvisDiscordPhoneControl.deleteChannel(context,text.removePrefix("discord delete channel ").trim())
        text.startsWith("discord create channel ") -> JarvisDiscordPhoneControl.createChannel(context,text.removePrefix("discord create channel ").trim())
        text.startsWith("discord create role ") -> JarvisDiscordPhoneControl.createRole(context,text.removePrefix("discord create role ").trim())
        text.startsWith("discord delete role ") -> JarvisDiscordPhoneControl.deleteRole(context,text.removePrefix("discord delete role ").trim())
        text.startsWith("discord edit channel ") -> JarvisDiscordPhoneControl.editChannel(context,text.removePrefix("discord edit channel ").trim())
        text.startsWith("discord edit role ") -> JarvisDiscordPhoneControl.editRole(context,text.removePrefix("discord edit role ").trim())
        text.startsWith("discord manage member ") -> JarvisDiscordPhoneControl.manageMember(context,text.removePrefix("discord manage member ").trim())
        text in setOf("confirm discord action","confirm discord","discord confirm","confirm the discord action","confirm discord command","confirm discord moderation") -> JarvisDiscordPhoneControl.confirm(context)
        text == "help" || text == "what can you do" || text == "what can i say" -> "I can open apps, send messages, control supported phone functions, read notifications, inspect your screen, run routines, chain commands, and maintain recent conversation context."
        text == "are you there" || text == "you there" || text == "hello" || text == "hey" -> "At your service."
        text.contains("what am i looking at") || text.contains("what is on my screen") || text.contains("read this screen") || text.contains("describe my screen") -> JarvisScreenInspector.describeScreen()
        text.startsWith("do you see ") -> if(JarvisScreenInspector.hasText(text.removePrefix("do you see ").trim())) "Yes, I can see that on the current screen." else "I don't see that on the current screen."
        text.startsWith("tap on screen ") -> JarvisScreenInspector.tapText(text.removePrefix("tap on screen ").trim())
        text == "what controls can you see" || text == "what buttons can you see" -> JarvisScreenInspector.listControls()
        text in setOf("what screen am i on","what page am i on","screen state") -> JarvisScreenInspector.screenState()
        text.startsWith("open the one underneath ") -> JarvisScreenInspector.tapRelative(text.removePrefix("open the one underneath ").trim(),true)
        text.startsWith("tap the one below ") -> JarvisScreenInspector.tapRelative(text.removePrefix("tap the one below ").trim(),true)
        text.startsWith("tap the one above ") -> JarvisScreenInspector.tapRelative(text.removePrefix("tap the one above ").trim(),false)
        text.matches(Regex("tap (the )?(\\d+)(st|nd|rd|th)?( item| result| button)?")) -> JarvisScreenInspector.tapNumber(Regex("\\d+").find(text)?.value?.toIntOrNull()?:1)
        text.startsWith("scroll until you see ") -> JarvisScreenInspector.scrollTo(text.removePrefix("scroll until you see ").trim())
        text.startsWith("find and tap ") -> JarvisScreenInspector.scrollTo(text.removePrefix("find and tap ").trim())
        text.startsWith("type ") && text.contains(" into ") -> { val value=text.substringAfter("type ").substringBefore(" into ").trim();val field=text.substringAfter(" into ").trim();JarvisScreenInspector.typeInto(field,value) }
        text == "run diagnostics" || text == "system diagnostics" -> JarvisDiagnostics.report(context)
        text in setOf("system health","jarvis health","health report") -> JarvisDiagnostics.health(context)
        text in setOf("what failed","why did that fail","last failure","what went wrong") -> JarvisDiagnostics.lastFailure(context)
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
        text.startsWith("tell ") && text.contains(" saying ") -> {val person=text.substringAfter("tell ").substringBefore(" saying ").trim();val body=text.substringAfter(" saying ").trim();JarvisCommunicationManager.send(context,person,body)}
        text.startsWith("message ") && text.contains(" saying ") -> {val person=text.substringAfter("message ").substringBefore(" saying ").trim();val body=text.substringAfter(" saying ").trim();JarvisCommunicationManager.send(context,person,body)}
        text.startsWith("draft for ") && text.contains(" saying ") -> {val person=text.substringAfter("draft for ").substringBefore(" saying ").trim();val body=text.substringAfter(" saying ").trim();JarvisCommunicationManager.draft(context,person,body)}
        text.startsWith("tell them ") -> JarvisCommunicationManager.tellCurrent(context,text.removePrefix("tell them ").trim())
        text.startsWith("reply to them ") -> JarvisCommunicationManager.replyToLatest(context,text.removePrefix("reply to them ").trim())
        text in setOf("did my message send","verify that message","verify my message") -> JarvisCommunicationManager.verify(context)
        text in setOf("who am i talking to","conversation status","communication status") -> JarvisCommunicationManager.status(context)
        text == "read my draft" -> { val r=JarvisContext.draftRecipient(context);val b=JarvisContext.draftBody(context);if(r.isBlank()||b.isBlank())"There is no message draft." else "Draft to $r: $b" }
        text == "send the draft" || text == "send it" -> JarvisCommunicationManager.sendDraft(context)
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
    }

    private fun naturalDiscord(context:Context,text:String):String?{
        val p=context.getSharedPreferences("jarvis_discord_context",Context.MODE_PRIVATE)
        fun remember(member:String){if(member.isNotBlank())p.edit().putString("member",member).putLong("until",System.currentTimeMillis()+600000).apply()}
        fun recalled():String=if(System.currentTimeMillis()<p.getLong("until",0L))p.getString("member","").orEmpty() else ""
        fun clean(v:String)=v.replace(Regex("\\s+(?:from|in|on) (?:the )?(?:discord )?server$"),"").trim()
        fun member(raw:String):String{val v=clean(raw);return if(v in setOf("him","her","them","that guy","that person","this guy","this person"))recalled() else v}
        fun need(v:String)=if(v.isBlank())"Tell me which Discord member first." else ""
        var m=Regex("^(?:discord )?manage (.+)$").find(text)?.groupValues?.get(1)?.let(::member)
        if(m!=null){if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.manageMember(context,m)}
        m=Regex("^(?:discord )?ban (.+?)(?: from (?:the )?(?:discord )?server)?$").find(text)?.groupValues?.get(1)?.let(::member)
        if(m!=null){if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.ban(context,m)}
        m=Regex("^(?:discord )?kick (.+?)(?: from (?:the )?(?:discord )?server)?$").find(text)?.groupValues?.get(1)?.let(::member)
        if(m!=null){if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.kick(context,m)}
        val timeout=Regex("^(?:discord )?(?:timeout|time out) (.+?)(?: for (.+))?$").find(text)
        if(timeout!=null){m=member(timeout.groupValues[1]);if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.timeout(context,m)}
        m=Regex("^(?:discord )?mute (.+?)(?: for .+)?$").find(text)?.groupValues?.get(1)?.let(::member)
        if(m!=null){if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.mute(context,m)}
        m=Regex("^(?:discord )?unmute (.+)$").find(text)?.groupValues?.get(1)?.let(::member)
        if(m!=null){if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.unmute(context,m)}
        val give=Regex("^(?:discord )?(?:give|add) (.+?) (?:the )?(.+?) role$").find(text)
        if(give!=null){m=member(give.groupValues[1]);val role=give.groupValues[2].trim();if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.addRole(context,m,role)}
        val remove=Regex("^(?:discord )?remove (?:the )?(.+?) role from (.+)$").find(text)
        if(remove!=null){val role=remove.groupValues[1].trim();m=member(remove.groupValues[2]);if(need(m).isNotBlank())return need(m);remember(m);return JarvisDiscordPhoneControl.removeRole(context,m,role)}
        if(text in setOf("delete this message","delete that message","discord delete this message","discord delete that message"))return JarvisDiscordPhoneControl.deleteMessage(context,"message")
        val channel=Regex("^(?:discord )?(?:make|create) (?:a )?channel (?:called |named )?(.+)$").find(text)?.groupValues?.get(1)?.trim()
        if(!channel.isNullOrBlank())return JarvisDiscordPhoneControl.createChannel(context,channel)
        val role=Regex("^(?:discord )?(?:make|create) (?:a )?role (?:called |named )?(.+)$").find(text)?.groupValues?.get(1)?.trim()
        if(!role.isNullOrBlank())return JarvisDiscordPhoneControl.createRole(context,role)
        if(text in setOf("mute him","mute her","mute them","kick him","kick her","kick them","ban him","ban her","ban them","unmute him","unmute her","unmute them")){
            m=recalled();if(m.isBlank())return "Tell me which Discord member first."
            return when{text.startsWith("mute")->JarvisDiscordPhoneControl.mute(context,m);text.startsWith("kick")->JarvisDiscordPhoneControl.kick(context,m);text.startsWith("ban")->JarvisDiscordPhoneControl.ban(context,m);else->JarvisDiscordPhoneControl.unmute(context,m)}
        }
        return null
    }

    private fun confirmPendingAction(context:Context):String{val discord=context.getSharedPreferences("jarvis_discord_phone_pending",Context.MODE_PRIVATE);if(discord.getLong("until",0)>System.currentTimeMillis())return JarvisDiscordPhoneControl.confirm(context);val recent=context.getSharedPreferences("jarvis_discord_recent_audit",Context.MODE_PRIVATE);if(recent.getLong("until",0)>System.currentTimeMillis())return JarvisDiscordPhoneControl.confirmRecentAccountBans(context);return JarvisAgentSafety.confirm(context)}

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
