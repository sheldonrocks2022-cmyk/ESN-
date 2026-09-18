package com.esn.jarvis

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class JarvisBluetoothReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent?){val connected=intent?.action==BluetoothDevice.ACTION_ACL_CONNECTED;val disconnected=intent?.action==BluetoothDevice.ACTION_ACL_DISCONNECTED;if(!connected&&!disconnected)return;val prefs=context.getSharedPreferences("jarvis_bluetooth",Context.MODE_PRIVATE);prefs.edit().putBoolean("connected",connected).putLong("changed_at",System.currentTimeMillis()).apply();JarvisContext.remember(context,"bluetooth",if(connected)"connected" else "disconnected")}}
