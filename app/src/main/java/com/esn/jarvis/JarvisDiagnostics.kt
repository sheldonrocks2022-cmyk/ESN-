package com.esn.jarvis
import android.content.Context
import android.speech.SpeechRecognizer
object JarvisDiagnostics {
 fun report(context:Context):String{
  val security=JarvisSecurity.status(context)
  val prefs=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE)
  val speech=SpeechRecognizer.isRecognitionAvailable(context)
  val stage=prefs.getString("service_stage","unknown").orEmpty()
  val bluetooth=context.getSharedPreferences("jarvis_bluetooth",Context.MODE_PRIVATE).getBoolean("connected",false)
  val issues=security.filter{it.second in setOf("OFF","PERMISSION NEEDED","NOT ENROLLED")}
  return "Diagnostics. Speech recognition: "+(if(speech)"ready" else "unavailable")+". Voice engine: "+stage+". Bluetooth: "+(if(bluetooth)"connected" else "not connected")+". Accessibility: "+(if(JarvisAccessibilityService.hasAccess())"ready" else "off")+". "+(if(issues.isEmpty())"Core systems report ready." else "Attention: "+issues.joinToString(", "){it.first+" "+it.second}+".")
 }
 fun recover(context:Context):String{val p=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE);if(p.getBoolean("emergency_shutdown",false))return "Emergency shutdown is active. Recovery will not override it.";return try{val i=android.content.Intent(context,JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START);context.startService(i);p.edit().putString("service_stage","RECOVERY_REQUESTED").apply();"Recovery requested. Voice service is restarting; Accessibility and Notification Access still require Android permission if disabled."}catch(e:Throwable){"Recovery could not restart the voice service: "+e.javaClass.simpleName}}
}

