package com.esn.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class JarvisBootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent?){if(intent?.action!=Intent.ACTION_BOOT_COMPLETED&&intent?.action!="android.intent.action.LOCKED_BOOT_COMPLETED")return;val prefs=context.getSharedPreferences("jarvis",Context.MODE_PRIVATE);if(!prefs.getBoolean("active",false))return;val i=Intent(context,JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START);try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i)else context.startService(i)}catch(_:Exception){prefs.edit().putString("service_stage","BOOT_RECOVERY_BLOCKED").apply()}}}
