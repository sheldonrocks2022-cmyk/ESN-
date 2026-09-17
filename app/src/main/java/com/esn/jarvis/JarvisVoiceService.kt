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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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

    override fun onCreate(){ super.onCreate(); checkpoint("SERVICE_CREATE"); try{createChannel()}catch(t:Throwable){checkpoint("CHANNEL_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")} }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{ checkpoint("SERVICE_START"); when(intent?.action){ACTION_START->activate();ACTION_STOP->deactivate();else->stopSelf()}; return START_NOT_STICKY }
    private fun activate(){ if(active){startRecognition();return}; if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){checkpoint("MIC_PERMISSION_MISSING");setActive(false);stopSelf();return}; checkpoint("MIC_PERMISSION_OK"); try{checkpoint("FGS_BEFORE");val n=notification("JARVIS active — listening");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)ServiceCompat.startForeground(this,NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)else startForeground(NOTIFICATION_ID,n);checkpoint("FGS_AFTER")}catch(t:Throwable){checkpoint("FGS_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");setActive(false);stopSelf();return};active=true;setActive(true);initTts();checkpoint("RECOGNIZER_START");startRecognition() }
    private fun initTts(){ if(tts!=null)return; try{checkpoint("TTS_CREATE_BEFORE");tts=TextToSpeech(this,this);checkpoint("TTS_CREATE_AFTER")}catch(t:Throwable){checkpoint("TTS_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");tts=null;ttsReady=false} }
    override fun onInit(status:Int){ checkpoint("TTS_INIT:$status"); if(status==TextToSpeech.SUCCESS){try{configureVoice();ttsReady=true;checkpoint("TTS_READY:${tts?.voice?.name.orEmpty()}");pendingSpeech?.let{pendingSpeech=null;speak(it)}}catch(t:Throwable){checkpoint("TTS_SETUP_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")}} }
    private fun configureVoice(){
        val engine=tts?:return
        engine.language=Locale.US
        val preferred=chooseCalmEnglishVoice(engine.voices)
        if(preferred!=null){engine.voice=preferred;checkpoint("TTS_VOICE:${preferred.name}")}
        val prefs=getSharedPreferences(PREFS,MODE_PRIVATE)
        engine.setSpeechRate(prefs.getFloat("speech_rate",0.76f))
        engine.setPitch(prefs.getFloat("speech_pitch",0.68f))
    }
    private fun chooseCalmEnglishVoice(voices:Set<Voice>?):Voice? {
        if(voices.isNullOrEmpty())return null
        val english=voices.filter{it.locale?.language==Locale.ENGLISH.language && !it.isNetworkConnectionRequired}
        if(english.isEmpty())return null
        fun score(v:Voice):Int{
            val n=v.name.lowercase(Locale.US)
            var s=0
            if(v.locale==Locale.US)s+=30
            if(!v.isNetworkConnectionRequired)s+=20
            if(n.contains("male"))s+=120
            if(n.contains("masculine"))s+=120
            if(n.contains("low"))s+=35
            if(n.contains("deep"))s+=35
            if(n.contains("en-us"))s+=15
            if(n.contains("local"))s+=10
            return s
        }
        return english.maxByOrNull(::score)
    }
    private fun speak(text:String){if(text.isBlank())return;if(!ttsReady){pendingSpeech=text;initTts();return};try{starting=false;recognizer?.cancel();checkpoint("SPEAK:${text.take(100)}");tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-response");handler.postDelayed({if(active)startRecognition()},maxOf(1400L,text.length*62L))}catch(t:Throwable){checkpoint("SPEAK_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");if(active)handler.postDelayed({startRecognition()},1200L)}}
    private fun startRecognition(){ if(!active||starting)return;if(!SpeechRecognizer.isRecognitionAvailable(this)){checkpoint("RECOGNIZER_UNAVAILABLE");return};starting=true;try{recognizer?.cancel();recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer?.setRecognitionListener(object:RecognitionListener{override fun onReadyForSpeech(params:Bundle?){starting=false;checkpoint("LISTENING")};override fun onBeginningOfSpeech()=Unit;override fun onRmsChanged(rmsdB:Float)=Unit;override fun onBufferReceived(buffer:ByteArray?)=Unit;override fun onEndOfSpeech()=Unit;override fun onError(error:Int){starting=false;checkpoint("RECOGNIZER_ERROR:$error");if(active)handler.postDelayed({startRecognition()},1500L)};override fun onResults(results:Bundle?){starting=false;val spoken=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty();if(spoken.isNotBlank())handleSpeech(spoken);else if(active)handler.postDelayed({startRecognition()},700L)};override fun onPartialResults(partialResults:Bundle?)=Unit;override fun onEvent(eventType:Int,params:Bundle?)=Unit});val speechIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.US.toLanguageTag());putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)};recognizer?.startListening(speechIntent)}catch(t:Throwable){starting=false;checkpoint("RECOGNIZER_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");if(active)handler.postDelayed({startRecognition()},2000L)} }
    private fun handleSpeech(spoken:String){val command=spoken.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"),"").trim().removePrefix("jarvis").trim();checkpoint("HEARD:${spoken.take(80)}");if(command.isBlank()){speak("Yes?");return};try{checkpoint("COMMAND:${command.take(80)}");val result=if(JarvisMessaging.canHandle(command))JarvisMessaging.execute(this,command)else JarvisNaturalCommandRouter.execute(this,command)?:JarvisCommandEngine.execute(this,command);checkpoint("RESULT:${result.take(100)}");speak(result)}catch(t:Throwable){checkpoint("COMMAND_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}");speak("I couldn't complete that command.")} }
    private fun deactivate(){active=false;starting=false;handler.removeCallbacksAndMessages(null);try{recognizer?.cancel();recognizer?.destroy()}catch(_:Throwable){};recognizer=null;try{tts?.stop();tts?.shutdown()}catch(_:Throwable){};tts=null;ttsReady=false;setActive(false);checkpoint("STOP_COMPLETE");try{ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE)}catch(_:Throwable){};stopSelf()}
    private fun setActive(value:Boolean){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(ACTIVE,value).commit()}
    private fun checkpoint(stage:String){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("service_stage",stage).putLong("service_stage_time",System.currentTimeMillis()).commit()}
    private fun notification(text:String):Notification=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL_ID).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()else Notification.Builder(this).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val manager=getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager;manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,"JARVIS Voice",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})}}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);try{recognizer?.cancel();recognizer?.destroy()}catch(_:Throwable){};recognizer=null;try{tts?.stop();tts?.shutdown()}catch(_:Throwable){};tts=null;super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
