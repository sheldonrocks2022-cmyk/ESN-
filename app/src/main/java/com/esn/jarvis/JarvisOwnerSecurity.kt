package com.esn.jarvis
import android.app.KeyguardManager
import android.content.Context
object JarvisOwnerSecurity{
 fun status(c:Context):String{val km=c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager;return "Device authentication is "+(if(km.isDeviceSecure)"configured" else "not configured")+". Owner voice profile is "+(if(OwnerVoiceProfile.isEnrolled(c))"enrolled" else "not enrolled")+". Sensitive agent actions require explicit confirmation."}
 fun canProtectSensitive(c:Context)= (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
}