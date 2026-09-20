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
 private const val HEALTH="jarvis_health"
 fun recordFailure(context:Context,area:String,detail:String){val p=context.getSharedPreferences(HEALTH,Context.MODE_PRIVATE);p.edit().putString("last_failure_area",area).putString("last_failure_detail",detail.take(240)).putLong("last_failure_time",System.currentTimeMillis()).putInt("failure_count",p.getInt("failure_count",0)+1).apply()}
 fun lastFailure(context:Context):String{val p=context.getSharedPreferences(HEALTH,0);val area=p.getString("last_failure_area","").orEmpty();return if(area.isBlank())"No recent JARVIS failure is recorded." else "Last failure: $area — "+p.getString("last_failure_detail","unknown").orEmpty()+"."}
 fun health(context:Context):String{val p=context.getSharedPreferences(HEALTH,0);return report(context)+" Recovery count: "+p.getInt("recovery_count",0)+". Failure count: "+p.getInt("failure_count",0)+"."}

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
  return "Diagnostics. Build "+BuildConfig.VERSION_NAME+". Speech recognition: "+(if(speech)"ready" else "unavailable")+". Voice engine: "+stage+". Phone Access: "+(if(JarvisAccessibilityService.hasAccess())"connected" else if(JarvisAccessibilityService.isEnabled(context))"enabled, reconnect needed" else "off")+". Notification Access: "+JarvisNotificationListenerService.accessStatus(context)+" "+(if(issues.isEmpty())"Core systems report ready." else "Attention: "+issues.joinToString(", ")+".")
 }
 fun recover(context:Context):String{val p=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE);if(p.getBoolean("emergency_shutdown",false))return "Emergency shutdown is active. Recovery will not override it.";return try{val i=android.content.Intent(context,JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i)else context.startService(i);context.getSharedPreferences(HEALTH,Context.MODE_PRIVATE).edit().putInt("recovery_count",context.getSharedPreferences(HEALTH,0).getInt("recovery_count",0)+1).putLong("last_recovery",System.currentTimeMillis()).apply();p.edit().putString("service_stage","RECOVERY_REQUESTED").apply();"Recovery requested. Voice service restart requested; Android permissions still require your approval if disabled."}catch(e:Throwable){"Recovery could not restart the voice service: "+e.javaClass.simpleName}}
}
