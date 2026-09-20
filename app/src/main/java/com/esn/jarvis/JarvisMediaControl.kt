package com.esn.jarvis
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
object JarvisMediaControl {
 private fun audio(c:Context)=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 fun key(context:Context,code:Int,label:String):String=try{val a=audio(context);a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code));a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code));label}catch(_:Throwable){"I could not control media."}
 fun pause(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_PAUSE,"Media paused.")
 fun play(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_PLAY,"Media playback started.")
 fun playPause(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,"Media toggled.")
 fun next(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_NEXT,"Skipping to the next track.")
 fun previous(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_PREVIOUS,"Going to the previous track.")
 fun stop(c:Context)=key(c,KeyEvent.KEYCODE_MEDIA_STOP,"Media stopped.")
}
