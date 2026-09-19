package com.esn.jarvis
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
object JarvisBluetoothState{
 fun status(c:Context):String{val m=c.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager?:return "Bluetooth is unavailable.";val a=m.adapter?:return "Bluetooth is unavailable.";if(!a.isEnabled)return "Bluetooth is off.";if(android.os.Build.VERSION.SDK_INT>=31&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return "Bluetooth is on, but JARVIS needs Nearby Devices permission to inspect paired devices.";val cached=c.getSharedPreferences("jarvis_bluetooth",0).getBoolean("connected",false);val paired=try{a.bondedDevices.size}catch(_:SecurityException){0};return if(cached)"Bluetooth is on and a device connection is currently detected." else "Bluetooth is on. $paired paired device"+(if(paired==1)" is" else "s are")+" known; no active ACL connection is currently detected."}
}