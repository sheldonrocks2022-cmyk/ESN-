package com.esn.jarvis
import android.content.Context
import java.util.Locale
object JarvisEsnOperations {
 private const val PREFS="jarvis_esn_ops"
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun prepare(c:Context,request:String):String{val r=request.trim();if(r.isBlank())return "Tell me what you want prepared for ESN.";val lower=r.lowercase(Locale.US);val draft=when{lower.contains("promot")||lower.contains("advertis")->"ESN promotion draft: Join ESN and be part of a growing community. Check out what we're building, get involved, and invite people who would genuinely enjoy it.";lower.contains("announce")->"ESN announcement draft: "+r;else->"ESN operations task prepared: "+r};p(c).edit().putString("last_request",r).putString("last_draft",draft).putLong("prepared",System.currentTimeMillis()).apply();return draft+" Nothing has been posted automatically. Review it, then use an authorized posting action if you want it published."}
 fun status(c:Context):String{val r=p(c).getString("last_request","").orEmpty();return if(r.isBlank())"ready; no campaign prepared." else "last prepared task: $r."}
 fun lastDraft(c:Context):String=p(c).getString("last_draft","No ESN draft is prepared yet.").orEmpty()
 fun clear(c:Context):String{p(c).edit().clear().apply();return "ESN operations workspace cleared."}
}