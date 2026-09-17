package com.esn.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object JarvisBrain {
    private const val PREFS="jarvis_brain"
    private const val ENDPOINT="ai_endpoint"

    fun respond(context:Context,raw:String):String{
        val text=raw.trim()
        val lower=text.lowercase(Locale.US)
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val previous=prefs.getString("last_user","").orEmpty()
        val local=when{
            lower in setOf("who are you","what are you")->"I'm JARVIS, your personal assistant. I can control supported phone functions and keep track of our recent conversation."
            lower.contains("what did i just say")||lower.contains("what was my last question")->if(previous.isBlank())"We haven't established any conversation context yet." else "You said, $previous."
            lower.startsWith("remember that ")->{val note=text.substringAfter("remember that ").trim();prefs.edit().putString("note",note).apply();"I'll keep that in our local conversation context."}
            lower=="what do you remember"||lower=="what did i ask you to remember"->{val note=prefs.getString("note","").orEmpty();if(note.isBlank())"You haven't asked me to remember anything yet." else "You asked me to remember: $note."}
            lower.startsWith("thanks")||lower=="thank you"->"You're welcome."
            lower.contains("how are you")->"All systems are operational."
            else->null
        }
        val response=local?:askBackend(context,text,previous)
        prefs.edit().putString("last_user",text).putString("last_response",response).apply()
        return response
    }

    fun setEndpoint(context:Context,url:String):String{
        val clean=url.trim().removeSuffix("/")
        if(clean.isBlank()){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(ENDPOINT).apply();return "AI backend disconnected."}
        if(!clean.startsWith("https://"))return "For security, the JARVIS AI backend must use HTTPS."
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(ENDPOINT,clean).apply()
        return "AI backend saved."
    }

    private fun askBackend(context:Context,text:String,previous:String):String{
        val endpoint=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(ENDPOINT,"").orEmpty()
        if(endpoint.isBlank())return "My conversational AI backend is ready to connect, but no secure endpoint has been configured yet."
        return try{
            val connection=(URL(endpoint).openConnection() as HttpURLConnection).apply{
                requestMethod="POST";connectTimeout=8000;readTimeout=15000;doOutput=true
                setRequestProperty("Content-Type","application/json")
            }
            val body=JSONObject().put("message",text).put("previous",previous).put("client","jarvis-android")
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=connection.responseCode
            val stream=if(code in 200..299)connection.inputStream else connection.errorStream
            val rawResponse=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
            connection.disconnect()
            if(code !in 200..299)return "The AI backend returned error $code."
            val json=JSONObject(rawResponse)
            json.optString("reply").ifBlank{json.optString("response")}.ifBlank{"The AI backend responded without a reply."}
        }catch(_:Exception){"I couldn't reach the AI backend. Your local phone commands are still available."}
    }
}
