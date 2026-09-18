package com.esn.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SmsManager

object JarvisMessaging {
    private const val ALIAS_PREFS = "jarvis_contact_aliases"
    fun saveContactAlias(context:Context,alias:String,contact:String):String { if(alias.isBlank()||contact.isBlank()) return "Tell me the alias and contact."; context.getSharedPreferences(ALIAS_PREFS,Context.MODE_PRIVATE).edit().putString(alias.lowercase(),contact).apply(); return "I will remember $alias as $contact." }
    private fun alias(context:Context,name:String):String { val p=context.getSharedPreferences(ALIAS_PREFS,Context.MODE_PRIVATE); p.getString(name.lowercase(),null)?.let{return it}; val keys=p.all.keys; val best=keys.minByOrNull{distance(it,name.lowercase())}; return if(best!=null && distance(best,name.lowercase())<=1)p.getString(best,name).orEmpty() else name }
    private fun distance(a:String,b:String):Int { val d=Array(a.length+1){IntArray(b.length+1)}; for(i in 0..a.length)d[i][0]=i; for(j in 0..b.length)d[0][j]=j; for(i in 1..a.length)for(j in 1..b.length)d[i][j]=minOf(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+if(a[i-1]==b[j-1])0 else 1); return d[a.length][b.length] }
    fun canHandle(command: String): Boolean = command.matches(Regex("^(send (a )?(text|message)( to)?|text|message) .+", RegexOption.IGNORE_CASE))

    fun execute(context: Context, raw: String): String {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, MessagingPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Allow Contacts and SMS access, then say the command again."
        }
        val cleaned = raw.trim().replaceFirst(Regex("^(send (a )?(text|message)( to)?|text|message)\\s+", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(.+?)\\s+(?:saying|say|that says|message)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned) ?: return "Say: send a message to John saying I'm on my way."
        val spokenRecipient = match.groupValues[1].trim(); val recipient = alias(context, spokenRecipient); val body = match.groupValues[2].trim()
        JarvisContext.remember(context,"contact",recipient)
        if (recipient.isBlank() || body.isBlank()) return "Tell me who to message and what to say."
        val number = if (recipient.count { it.isDigit() } >= 7) recipient.filter { it.isDigit() || it == '+' } else resolveContact(context, recipient)?.second ?: return "I couldn't find one clear contact named $recipient. Say the full contact name so I don't guess."
        return try {
            @Suppress("DEPRECATION") val sms = SmsManager.getDefault()
            if (body.length > 150) sms.sendMultipartTextMessage(number, null, sms.divideMessage(body), null, null) else sms.sendTextMessage(number, null, body, null, null)
            "Message sent to $recipient."
        } catch (t: Throwable) { "I couldn't send that message: ${t.javaClass.simpleName}." }
    }

    fun contactChoices(context:Context,name:String):List<Pair<String,String>> {
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return emptyList()
        val wanted=alias(context,name).trim();if(wanted.isBlank())return emptyList()
        val out=mutableListOf<Pair<String,String>>();val projection=arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,null)?.use{c->val ni=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);val di=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);while(c.moveToNext()){val d=c.getString(di).orEmpty().trim();val n=c.getString(ni).orEmpty().trim();if(n.isNotBlank()&&(d.equals(wanted,true)||d.contains(wanted,true)||wanted.contains(d,true)))out+=d to n}}
        return out.distinctBy{it.first.lowercase()+":"+it.second.filter(Char::isDigit)}
    }

    fun resolveContact(context:Context,name:String):Pair<String,String>? {
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return null
        val wanted=alias(context,name).trim()
        if(wanted.isBlank())return null
        val projection=arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val exact=mutableListOf<Pair<String,String>>();val partial=mutableListOf<Pair<String,String>>()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,null)?.use{cursor->
            val ni=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);val di=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while(cursor.moveToNext()){
                val display=cursor.getString(di).orEmpty().trim();val number=cursor.getString(ni).orEmpty().trim()
                if(number.isBlank())continue
                if(display.equals(wanted,true))exact+=display to number
                else if(display.contains(wanted,true)||wanted.contains(display,true))partial+=display to number
            }
        }
        val exactUnique=exact.distinctBy{it.second.filter(Char::isDigit)}
        if(exactUnique.isNotEmpty())return exactUnique.first()
        val partialUnique=partial.distinctBy{it.first.lowercase()}
        return if(partialUnique.size==1)partialUnique.first() else null
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
