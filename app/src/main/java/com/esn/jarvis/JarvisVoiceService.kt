package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.media.AudioManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.ServiceCompat
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {
    companion object { const val ACTION_START="com.esn.jarvis.START"; const val ACTION_STOP="com.esn.jarvis.STOP"; private const val CHANNEL_ID="jarvis_voice"; private const val NOTIFICATION_ID=101; private const val PREFS="jarvis"; private const val ACTIVE="active" }
    private val handler by lazy { Handler(mainLooper) }
    private var recognizer: SpeechRecognizer?=null
    private var tts: TextToSpeech?=null
    private var ttsReady=false
    private var pendingSpeech:String?=null
    private var active=false
    private var starting=false
    private var listening=false
    private var standby=false
    private var speaking=false
    private var recoveryAttempts=0
    private var pendingConfirmation:String?=null
    private var lastSpokenText=""
    private var ttsRecoveryAttempts=0
    private var lastHealthyAt=0L
    private var lastTtsFinishedAt=0L
    private var listeningSince=0L
    private var mediaPauseUntil=0L
    private var conversationUntil=0L
    private val conversationWindowMs=45000L
    private var lastUserCommand=""

    override fun onCreate(){super.onCreate();checkpoint("SERVICE_CREATE");try{createChannel()}catch(t:Throwable){checkpoint("CHANNEL_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")}}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{checkpoint("SERVICE_START");when(intent?.action){ACTION_START->activate();ACTION_STOP->deactivate();JarvisNotificationListenerService.ACTION_SPEAK_NOTIFICATION->{val n=intent.getStringExtra(JarvisNotificationListenerService.EXTRA_NOTIFICATION_TEXT).orEmpty();if(n.isNotBlank()&&getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(ACTIVE,false)&&!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("emergency_shutdown",false)){active=true;initTts();speak(n)}else stopSelf()};else->stopSelf()};return START_NOT_STICKY}
    private fun activate(){if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("emergency_shutdown",false)){checkpoint("EMERGENCY_SHUTDOWN");setActive(false);stopSelf();return};if(active){startRecognition();return};if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){checkpoint("MIC_PERMISSION_MISSING");setActive(false);stopSelf();return};checkpoint("MIC_PERMISSION_OK");try{checkpoint("FGS_BEFORE");val n=notification("JARVIS active — listening");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)ServiceCompat.startForeground(this,NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)else startForeground(NOTIFICATION_ID,n);checkpoint("FGS_AFTER")}catch(t:Throwable){checkpoint("FGS_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");setActive(false);stopSelf();return};active=true;standby=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("standby",false);setActive(true);initTts();checkpoint(if(standby)"STANDBY" else "RECOGNIZER_START");startRecognition();handler.postDelayed({watchdog()},10000L)}
    private fun initTts(){if(tts!=null)return;try{checkpoint("TTS_CREATE_BEFORE");tts=TextToSpeech(this,this);checkpoint("TTS_CREATE_AFTER")}catch(t:Throwable){checkpoint("TTS_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");tts=null;ttsReady=false}}
    override fun onInit(status:Int){checkpoint("TTS_INIT:$status");if(status==TextToSpeech.SUCCESS){try{configureVoice();tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String){speaking=true;checkpoint("SPEAKING");handler.postDelayed({if(active)startRecognition()},150L)};override fun onDone(id:String){speaking=false;lastTtsFinishedAt=System.currentTimeMillis();ttsRecoveryAttempts=0;checkpoint("SPEAK_DONE");handler.postDelayed({if(active)startRecognition()},250L)};@Deprecated("Deprecated in Java") override fun onError(id:String){speaking=false;lastTtsFinishedAt=System.currentTimeMillis();ttsRecoveryAttempts++;checkpoint("SPEAK_ERROR:RECOVERY:$ttsRecoveryAttempts");try{tts?.shutdown()}catch(_:Throwable){};tts=null;ttsReady=false;if(ttsRecoveryAttempts<=3)handler.postDelayed({initTts()},500L*ttsRecoveryAttempts);handler.postDelayed({if(active)startRecognition()},700L)}});ttsReady=true;checkpoint("TTS_READY:${tts?.voice?.name.orEmpty()}");pendingSpeech?.let{pendingSpeech=null;speak(it)}}catch(t:Throwable){checkpoint("TTS_SETUP_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")}}}
    private fun configureVoice(){val engine=tts?:return;engine.language=Locale.US;val prefs=getSharedPreferences(PREFS,MODE_PRIVATE);val selected=prefs.getString("tts_voice","").orEmpty();val gender=prefs.getString("voice_gender","male").orEmpty();val preferred=engine.voices?.firstOrNull{it.name==selected&&!it.isNetworkConnectionRequired}?:chooseGenderVoice(engine.voices,gender);if(preferred!=null){engine.voice=preferred;checkpoint("TTS_VOICE:${preferred.name}")};engine.setSpeechRate(prefs.getFloat("speech_rate",0.76f));engine.setPitch(prefs.getFloat("speech_pitch",0.68f))}
    private fun chooseGenderVoice(voices:Set<Voice>?,gender:String):Voice?{if(voices.isNullOrEmpty())return null;val english=voices.filter{it.locale?.language==Locale.ENGLISH.language&&!it.isNetworkConnectionRequired};if(english.isEmpty())return null;val keys=if(gender=="female")listOf("female","feminine","woman") else listOf("male","masculine","man","low","deep");val matched=english.filter{v->keys.any{v.name.lowercase(Locale.US).contains(it)}};return (matched.ifEmpty{english}).maxByOrNull{if(it.locale==Locale.US)20 else 0}}
    private fun chooseCalmEnglishVoice(voices:Set<Voice>?):Voice?{if(voices.isNullOrEmpty())return null;val english=voices.filter{it.locale?.language==Locale.ENGLISH.language&&!it.isNetworkConnectionRequired};if(english.isEmpty())return null;fun score(v:Voice):Int{val n=v.name.lowercase(Locale.US);var s=0;if(v.locale==Locale.US)s+=30;if(!v.isNetworkConnectionRequired)s+=20;if(n.contains("male")||n.contains("masculine"))s+=120;if(n.contains("low")||n.contains("deep"))s+=35;if(n.contains("en-us"))s+=15;if(n.contains("local"))s+=10;return s};return english.maxByOrNull(::score)}
    private fun speak(text:String){if(text.isBlank())return;listening=false;try{recognizer?.cancel()}catch(_:Throwable){};lastSpokenText=text.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"),"").trim();if(!ttsReady){pendingSpeech=text;initTts();return};try{starting=false;checkpoint("SPEAK:${text.take(100)}");tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-${System.currentTimeMillis()}")}catch(t:Throwable){checkpoint("SPEAK_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");if(active)handler.postDelayed({startRecognition()},900L)}}
    private fun mediaPlaying():Boolean=try{(getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive}catch(_:Throwable){false}
    private fun startRecognition(){if(!active||starting||listening||speaking)return;if(mediaPlaying()){mediaPauseUntil=System.currentTimeMillis()+2500L;checkpoint("MEDIA_ACTIVE_MIC_PAUSED");handler.postDelayed({if(active)startRecognition()},2500L);return};if(System.currentTimeMillis()<mediaPauseUntil){handler.postDelayed({if(active)startRecognition()},500L);return};if(!SpeechRecognizer.isRecognitionAvailable(this)){checkpoint("RECOGNIZER_UNAVAILABLE");return};starting=true;try{if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer?.setRecognitionListener(object:RecognitionListener{override fun onReadyForSpeech(params:Bundle?){starting=false;listening=true;recoveryAttempts=0;lastHealthyAt=System.currentTimeMillis();listeningSince=lastHealthyAt;checkpoint("LISTENING")};override fun onBeginningOfSpeech()=Unit;override fun onRmsChanged(rmsdB:Float)=Unit;override fun onBufferReceived(buffer:ByteArray?)=Unit;override fun onEndOfSpeech()=Unit;override fun onError(error:Int){starting=false;listening=false;listeningSince=0L;recoveryAttempts++;checkpoint("RECOGNIZER_ERROR:$error:RECOVERY:$recoveryAttempts");JarvisDiagnostics.recordFailure(this@JarvisVoiceService,"speech recognizer","error $error, recovery $recoveryAttempts");if(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY||error==SpeechRecognizer.ERROR_CLIENT){try{recognizer?.destroy()}catch(_:Throwable){};recognizer=null};if(active)handler.postDelayed({startRecognition()},minOf(5000L,700L*recoveryAttempts))};override fun onResults(results:Bundle?){starting=false;listening=false;listeningSince=0L;val candidates=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().map{it.trim()}.filter{it.isNotBlank()};val spoken=chooseBestCandidate(candidates);if(spoken.isNotBlank())handleSpeech(spoken);else if(active)handler.postDelayed({startRecognition()},700L)};override fun onPartialResults(partialResults:Bundle?){val p=partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.US).orEmpty();if(speaking&&isWakeCommand(p)){tts?.stop();speaking=false;checkpoint("INTERRUPTED");val clean=stripWake(p);if(clean.isNotBlank()&&clean!="stop"&&clean!="cancel")handleSpeech(p)}};override fun onEvent(eventType:Int,params:Bundle?)=Unit})};val speechIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.US.toLanguageTag());putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)};recognizer?.startListening(speechIntent)}catch(t:Throwable){starting=false;checkpoint("RECOGNIZER_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");if(active)handler.postDelayed({startRecognition()},2000L)}}
    private fun chooseBestCandidate(candidates:List<String>):String {
        if(candidates.isEmpty())return ""
        fun score(raw:String):Int {
            val n=raw.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"),"").trim()
            val cmd=n.removePrefix("jarvis").trim()
            var s=0
            if(n.startsWith("jarvis"))s+=20
            if(cmd.matches(Regex("^(send (a )?(text|message)( to)?|text|message) .+")))s+=100
            if(cmd.matches(Regex("^(send (a )?(text|message)( to)?|text|message) .+ (saying|say|that says|message) .+")))s+=120
            if(cmd.startsWith("open ")||cmd.startsWith("call ")||cmd.startsWith("set ")||cmd.startsWith("read ")||cmd.startsWith("reply "))s+=60
            if(cmd.startsWith("jarvis agent ")||cmd.startsWith("figure out ")||cmd.startsWith("handle this "))s+=90
            if(n.contains("code 101")||n.contains("code one oh one")||n.contains("code one zero one"))s+=150
            if(n.contains("start up")||n.contains("startup"))s+=120
            return s
        }
        return candidates.withIndex().maxWithOrNull(compareBy<IndexedValue<String>>{score(it.value)}.thenBy{-it.index})?.value.orEmpty()
    }

    private fun handleSpeech(spoken:String){
        val normalized=spoken.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"),"").trim()
        if(speaking&&lastSpokenText.isNotBlank()&&(normalized==lastSpokenText||(lastSpokenText.contains(normalized)&&normalized.length>12))){checkpoint("SELF_SPEECH_IGNORED");handler.postDelayed({if(active)startRecognition()},200L);return}
        if(speaking){tts?.stop();speaking=false;checkpoint("BARGE_IN")}
        val hadWake=isWakeCommand(normalized)
        val command=stripWake(normalized)
        JarvisContext.remember(this,"last_spoken_command",command)
        val inConversation=System.currentTimeMillis()<conversationUntil
        if(!hadWake && pendingConfirmation==null && !inConversation){checkpoint("IGNORED_NO_WAKE:${spoken.take(60)}");handler.postDelayed({if(active)startRecognition()},250L);return}
        if(hadWake)conversationUntil=System.currentTimeMillis()+conversationWindowMs
        if(System.currentTimeMillis()-lastTtsFinishedAt<900L && !hadWake){checkpoint("POST_TTS_ECHO_IGNORED");handler.postDelayed({if(active)startRecognition()},350L);return}
        checkpoint("HEARD:${spoken.take(80)}")
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_heard",spoken).apply()
        if(normalized=="jarvis code 101"||normalized=="jarvis code one oh one"||normalized=="jarvis code one zero one"){
            standby=true
            checkpoint("STANDBY")
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("standby",true).apply()
            // Code 101 must silence only JARVIS. Do not request audio focus or send media keys,
            // so Spotify/other media keeps playing.
            pendingSpeech=null
            try { tts?.stop() } catch (_:Throwable) {}
            speaking=false
            checkpoint("CODE101_ACK")
            speak("Standing by.")
            return
        }
        if(standby){
            if(normalized=="jarvis start up"||normalized=="jarvis startup"||normalized=="jarvis start"||normalized=="start up"||normalized=="startup"||normalized=="start"||normalized.contains("jarvis start up")||normalized.contains("jarvis startup")){
                standby=false
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("standby",false).apply()
                checkpoint("STARTUP")
                speak("JARVIS online. All systems ready.")
            }else handler.postDelayed({if(active)startRecognition()},250L)
            return
        }
        if(command.isBlank()){conversationUntil=System.currentTimeMillis()+conversationWindowMs;speak("Yes?");return}
        if(command in setOf("stop","stop listening","end conversation","cancel conversation")){pendingConfirmation=null;conversationUntil=0L;JarvisAgent.stop(this);speak("Conversation ended.");return}
        if(command in setOf("wait","hold on","pause")){JarvisAgent.stop(this);conversationUntil=System.currentTimeMillis()+conversationWindowMs;speak("Standing by.");return}
        if(command.startsWith("actually ")){JarvisAgent.stop(this);conversationUntil=System.currentTimeMillis()+conversationWindowMs;handleSpeech("jarvis "+command.removePrefix("actually "));return}
        if(command in setOf("what are you doing","progress","task progress")){conversationUntil=System.currentTimeMillis()+conversationWindowMs;speak(JarvisAgent.status(this));return}
        if(command in setOf("cancel","never mind") && pendingConfirmation==null){conversationUntil=System.currentTimeMillis()+conversationWindowMs;speak("Cancelled.");return}
        pendingConfirmation?.let { pending ->
            if(command=="yes"||command=="confirm"||command=="do it"){pendingConfirmation=null;val result=JarvisCommandEngine.execute(this,pending);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_result",result).apply();speak(result);return}
            if(command=="no"||command=="cancel"||command=="never mind"){pendingConfirmation=null;speak("Cancelled.");return}
            speak("Please say yes to confirm or no to cancel.");return
        }
        val rememberContact=Regex("^remember (.+?) is (.+)$").find(command)
        if(rememberContact!=null){speak(JarvisMessaging.saveContactAlias(this,rememberContact.groupValues[1].trim(),rememberContact.groupValues[2].trim()));return}
        if(command=="delete all reminders"||command=="clear all reminders"||command=="clear all aliases"){pendingConfirmation=command;speak("That will remove saved information. Say yes to confirm or no to cancel.");return}
        try{
            lastUserCommand=command
            checkpoint("PROCESSING")
            checkpoint("COMMAND:${command.take(80)}")
            checkpoint("UNDERSTOOD:${command.take(80)}")
            checkpoint("EXECUTING")
            val chained=command.contains(Regex("\\s+(?:and then|then)\\s+"))
            val result=if(chained)JarvisNaturalCommandRouter.execute(this,command)?:JarvisCommandEngine.execute(this,command)else if(JarvisMessaging.canHandle(command))JarvisMessaging.execute(this,command)else JarvisNaturalCommandRouter.execute(this,command)?:JarvisCommandEngine.execute(this,command)
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_command",command).putString("last_result",result).apply();appendHistory(command,result);JarvisContext.rememberCommand(this,command,result);conversationUntil=System.currentTimeMillis()+conversationWindowMs
            checkpoint("RESULT:${result.take(100)}")
            speak(result)
        }catch(t:Throwable){
            checkpoint("COMMAND_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");JarvisDiagnostics.recordFailure(this,"command",t.javaClass.simpleName+":"+t.message.orEmpty())
            speak("I couldn't complete that command.")
        }
    }
    private fun appendHistory(command:String,result:String){val p=getSharedPreferences("jarvis_history",MODE_PRIVATE);val old=p.getString("items","").orEmpty().lines().filter{it.isNotBlank()}.takeLast(29).toMutableList();old.add("${System.currentTimeMillis()}|$command|${result.replace("|","/").replace("\n"," ")}");p.edit().putString("items",old.joinToString("\n")).apply()}
    private fun isWakeCommand(text:String)=Regex("^(jarvis|jervis|jarvises|service|drivers)\\b").containsMatchIn(text.trim())
    private fun stripWake(text:String)=text.replaceFirst(Regex("^(jarvis|jervis|jarvises|service|drivers)\\s+"),"").trim()
    private fun watchdog(){if(!active)return;val stale=listeningSince==0L&&lastHealthyAt>0&&System.currentTimeMillis()-lastHealthyAt>20000;if(stale&&!starting){checkpoint("WATCHDOG_RECOVERY");try{recognizer?.cancel();recognizer?.destroy()}catch(_:Throwable){};recognizer=null;starting=false;startRecognition()};handler.postDelayed({watchdog()},10000L)}
    private fun deactivate(){active=false;starting=false;listening=false;handler.removeCallbacksAndMessages(null);try{recognizer?.cancel();recognizer?.destroy()}catch(_:Throwable){};recognizer=null;try{tts?.stop();tts?.shutdown()}catch(_:Throwable){};tts=null;ttsReady=false;setActive(false);checkpoint("STOP_COMPLETE");try{ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE)}catch(_:Throwable){};stopSelf()}
    private fun setActive(value:Boolean){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(ACTIVE,value).commit()}
    private fun checkpoint(stage:String){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("service_stage",stage).putLong("service_stage_time",System.currentTimeMillis()).commit()}
    private fun notification(text:String):Notification=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL_ID).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()else Notification.Builder(this).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val manager=getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager;manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,"JARVIS Voice",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})}}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);listening=false;try{recognizer?.cancel();recognizer?.destroy()}catch(_:Throwable){};recognizer=null;try{tts?.stop();tts?.shutdown()}catch(_:Throwable){};tts=null;super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
