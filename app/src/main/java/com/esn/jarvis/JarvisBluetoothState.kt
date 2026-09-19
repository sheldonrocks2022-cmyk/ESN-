package com.esn.jarvis
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
object JarvisBluetoothState{
 fun status(c:Context):String{val a=BluetoothAdapter.getDefaultAdapter()?:return "Bluetooth is not available on this device.";if(!a.isEnabled)return "Bluetooth is off.";if(android.os.Build.VERSION.SDK_INT>=31&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return "Bluetooth is on, but JARVIS needs Nearby devices permission to read connected device details.";val p=c.getSharedPreferences("jarvis_bluetooth",0);val connected=p.getBoolean("connected",false);val name=p.getString("device_name","").orEmpty();return if(connected)"Bluetooth is on and "+(if(name.isBlank())"a device is connected." else "$name is connected.") else "Bluetooth is on. I do not currently detect an active device connection."}
 fun update(c:Context,d:BluetoothDevice?,connected:Boolean){val name=try{d?.name.orEmpty()}catch(_:SecurityException){""};c.getSharedPreferences("jarvis_bluetooth",0).edit().putBoolean("connected",connected).putString("device_name",if(connected)name else "").putLong("changed_at",System.currentTimeMillis()).apply()}
}