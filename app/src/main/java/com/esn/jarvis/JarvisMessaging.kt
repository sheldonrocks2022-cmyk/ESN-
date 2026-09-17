package com.esn.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SmsManager

object JarvisMessaging {
    fun canHandle(command: String): Boolean = command.matches(Regex("^(send (a )?(text|message) to|text) .+", RegexOption.IGNORE_CASE))

    fun execute(context: Context, raw: String): String {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, MessagingPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Allow Contacts and SMS access, then say the command again."
        }
        val cleaned = raw.trim().replaceFirst(Regex("^(send (a )?(text|message) to|text)\\s+", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(.+?)\\s+(?:saying|say|that says|message)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned) ?: return "Say: send a message to John saying I'm on my way."
        val recipient = match.groupValues[1].trim(); val body = match.groupValues[2].trim()
        JarvisContext.remember(context,"contact",recipient)
        if (recipient.isBlank() || body.isBlank()) return "Tell me who to message and what to say."
        val number = if (recipient.count { it.isDigit() } >= 7) recipient.filter { it.isDigit() || it == '+' } else findMobileNumber(context, recipient) ?: return "I couldn't find a phone number for $recipient in your contacts."
        return try {
            @Suppress("DEPRECATION") val sms = SmsManager.getDefault()
            if (body.length > 150) sms.sendMultipartTextMessage(number, null, sms.divideMessage(body), null, null) else sms.sendTextMessage(number, null, body, null, null)
            "Message sent to $recipient."
        } catch (t: Throwable) { "I couldn't send that message: ${t.javaClass.simpleName}." }
    }

    fun resolveContact(context:Context,name:String):Pair<String,String>? {
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return null
        val projection=arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val matches=mutableListOf<Pair<String,String>>()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,null)?.use{cursor->
            val ni=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);val di=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while(cursor.moveToNext()){val display=cursor.getString(di).orEmpty();val number=cursor.getString(ni).orEmpty();if(display.equals(name,true)||display.contains(name,true))matches+=display to number}
        }
        return matches.distinctBy{it.second}.firstOrNull()
    }

    private fun findMobileNumber(context: Context, name: String): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val ni = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); val di = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); var partial: String? = null
            while (cursor.moveToNext()) { val display = cursor.getString(di).orEmpty(); val number = cursor.getString(ni).orEmpty(); if (display.equals(name, true)) return number; if (partial == null && display.contains(name, true)) partial = number }
            return partial
        }
        return null
    }
}
