package com.esn.jarvis
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
object JarvisDiagnostics {
 fun report(context:Context):String{
  val security=JarvisSecurity.status(context)
  val prefs=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE)
  val speech=SpeechRecognizer.isRecognitionAvailable(context)
  val stage=prefs.getString("service_stage","unknown").orEmpty()
  val bluetoothPerm=Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
  val writeSettings=Settings.System.canWrite(context)
  val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  val exactAlarm=Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms()
  val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
  val batteryUnrestricted=power.isIgnoringBatteryOptimizations(context.packageName)
  val issues=security.filter{it.second in setOf("OFF","PERMISSION NEEDED","NOT ENROLLED")}.map{it.first+" "+it.second}.toMutableList()
  if(!bluetoothPerm)issues+="Bluetooth permission needed"
  if(!writeSettings)issues+="Modify system settings permission off"
  if(!exactAlarm)issues+="Exact alarm access off"
  if(!batteryUnrestricted)issues+="Battery optimization active"
  return "Diagnostics. Build "+BuildConfig.VERSION_NAME+". Speech recognition: "+(if(speech)"ready" else "unavailable")+". Voice engine: "+stage+". Accessibility: "+(if(JarvisAccessibilityService.hasAccess())"ready" else "off")+". "+(if(issues.isEmpty())"Core systems report ready." else "Attention: "+issues.joinToString(", ")+".")
 }
 fun recover(context:Context):String{val p=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE);if(p.getBoolean("emergency_shutdown",false))return "Emergency shutdown is active. Recovery will not override it.";return try{val i=android.content.Intent(context,JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i)else context.startService(i);p.edit().putString("service_stage","RECOVERY_REQUESTED").apply();"Recovery requested. Voice service restart requested; Android permissions still require your approval if disabled."}catch(e:Throwable){"Recovery could not restart the voice service: "+e.javaClass.simpleName}}
}
