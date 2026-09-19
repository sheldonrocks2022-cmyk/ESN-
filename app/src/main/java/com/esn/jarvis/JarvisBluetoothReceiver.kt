package com.esn.jarvis
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class JarvisBluetoothReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent?){val connected=intent?.action==BluetoothDevice.ACTION_ACL_CONNECTED;val disconnected=intent?.action==BluetoothDevice.ACTION_ACL_DISCONNECTED;if(!connected&&!disconnected)return;val device=if(android.os.Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);JarvisBluetoothState.update(context,device,connected);JarvisContext.remember(context,"bluetooth",if(connected)"connected" else "disconnected");JarvisAutomationEngine.fire(context,if(connected)"bluetooth connected" else "bluetooth disconnected")}}