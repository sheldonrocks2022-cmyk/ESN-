package com.esn.jarvis
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
class JarvisSystemEventReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){val event=when(i?.action){Intent.ACTION_POWER_CONNECTED->"charging";Intent.ACTION_POWER_DISCONNECTED->"unplugged";Intent.ACTION_HEADSET_PLUG->if(i.getIntExtra("state",0)==1)"headphones connected" else "headphones disconnected";Intent.ACTION_BATTERY_LOW->"battery low";Intent.ACTION_BATTERY_OKAY->"battery okay";else->null}?:return;JarvisAutomationEngine.fire(c,event);c.getSharedPreferences("jarvis_automation",Context.MODE_PRIVATE).edit().putString("last_event",event).putLong("last_event_time",System.currentTimeMillis()).apply()}}
