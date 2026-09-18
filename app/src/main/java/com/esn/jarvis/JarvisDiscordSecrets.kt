package com.esn.jarvis
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
object JarvisDiscordSecrets{private const val A="jarvis_discord_token";private fun key():SecretKey{val k=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(k.getKey(A,null) as? SecretKey)?.let{return it};val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(KeyGenParameterSpec.Builder(A,3).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey()} fun saveToken(c:Context,t:String){val x=Cipher.getInstance("AES/GCM/NoPadding");x.init(Cipher.ENCRYPT_MODE,key());c.getSharedPreferences("jarvis_discord_secret",0).edit().putString("iv",Base64.encodeToString(x.iv,2)).putString("ct",Base64.encodeToString(x.doFinal(t.trim().toByteArray()),2)).apply()} fun getToken(c:Context):String=try{val p=c.getSharedPreferences("jarvis_discord_secret",0);val iv=Base64.decode(p.getString("iv",""),2);val ct=Base64.decode(p.getString("ct",""),2);if(iv.isEmpty()||ct.isEmpty())""else{val x=Cipher.getInstance("AES/GCM/NoPadding");x.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));String(x.doFinal(ct))}}catch(_:Exception){""}}