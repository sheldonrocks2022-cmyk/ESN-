package com.esn.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SmsManager

object JarvisMessaging {
    fun canHandle(command: String): Boolean = command.matches(Regex("^(send (a )?(text|message) to|text) .+", RegexOption.IGNORE_CASE))

    fun execute(context: Context, raw: String): String {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "Messaging permission is required. Open JARVIS and allow Contacts and SMS permissions."
        }

        val cleaned = raw.trim().replaceFirst(Regex("^(send (a )?(text|message) to|text)\\s+", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(.+?)\\s+(?:saying|say|that says|message)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned)
            ?: return "Say: send a message to John saying I'm on my way."
        val recipient = match.groupValues[1].trim()
        val body = match.groupValues[2].trim()
        if (recipient.isBlank() || body.isBlank()) return "Tell me who to message and what to say."

        val number = if (recipient.any { it.isDigit() } && recipient.count { it.isDigit() } >= 7) recipient.filter { it.isDigit() || it == '+' }
        else findMobileNumber(context, recipient)
            ?: return "I couldn't find a phone number for $recipient in your contacts."

        return try {
            @Suppress("DEPRECATION")
            val sms = SmsManager.getDefault()
            if (body.length > 150) {
                val parts = sms.divideMessage(body)
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                sms.sendTextMessage(number, null, body, null, null)
            }
            "Message sent to $recipient."
        } catch (t: Throwable) {
            "I couldn't send that message: ${t.javaClass.simpleName}."
        }
    }

    private fun findMobileNumber(context: Context, name: String): String? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            var partial: String? = null
            while (cursor.moveToNext()) {
                val display = cursor.getString(nameIndex).orEmpty()
                val number = cursor.getString(numberIndex).orEmpty()
                if (display.equals(name, ignoreCase = true)) return number
                if (partial == null && display.contains(name, ignoreCase = true)) partial = number
            }
            return partial
        }
        return null
    }
}
