package com.esn.jarvis

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Local-only acoustic owner profile. This is deliberately not used as Android
 * lock-screen authentication; it gates JARVIS commands only.
 */
object OwnerVoiceProfile {
    private const val PREFS = "jarvis_owner_voice"
    private const val KEY = "profile"
    private const val RATE = 16000
    private const val FEATURES = 16

    fun isEnrolled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun enroll(context: Context, samples: List<ShortArray>): Boolean {
        if (samples.size < 3) return false
        val vectors = samples.mapNotNull(::features)
        if (vectors.size < 3) return false
        val mean = FloatArray(FEATURES)
        vectors.forEach { v -> for (i in mean.indices) mean[i] += v[i] / vectors.size }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, mean.joinToString(",")).apply()
        return true
    }

    fun matches(context: Context, pcm: ShortArray, threshold: Float = 0.84f): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return false
        if (stored.size != FEATURES) return false
        val v = features(pcm) ?: return false
        var dot=0f; var a=0f; var b=0f
        for(i in 0 until FEATURES){ dot += stored[i]*v[i]; a += stored[i]*stored[i]; b += v[i]*v[i] }
        return dot / (sqrt(a)*sqrt(b)+1e-6f) >= threshold
    }

    /** Records one short phrase locally. Caller must already hold RECORD_AUDIO. */
    fun recordPhrase(seconds: Int = 2): ShortArray? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, RATE))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
        val out = ShortArray(RATE * seconds); var n=0
        return try {
            rec.startRecording()
            while(n < out.size){ val r=rec.read(out,n,out.size-n); if(r<=0) break else n+=r }
            if(n < RATE/2) null else out.copyOf(n)
        } catch(_:Throwable){ null } finally { try{rec.stop()}catch(_:Throwable){};rec.release() }
    }

    private fun features(pcm: ShortArray): FloatArray? {
        if (pcm.size < RATE/2) return null
        var energy=0.0
        pcm.forEach { val x=it/32768.0; energy += x*x }
        if (energy/pcm.size < 0.00008) return null
        val f=FloatArray(FEATURES)
        val block=max(1,pcm.size/FEATURES)
        for(i in 0 until FEATURES){
            val s=i*block; val e=minOf(pcm.size,s+block); var z=0; var en=0.0; var prev=pcm[s]
            for(j in s until e){ val x=pcm[j].toDouble(); en+=x*x; if((x>=0)!=(prev>=0))z++; prev=pcm[j] }
            val rms=sqrt(en/max(1,e-s))/32768.0
            f[i]=(ln(1.0+rms*20.0)+z.toDouble()/max(1,e-s)).toFloat()
        }
        val norm=sqrt(f.sumOf{(it*it).toDouble()}).toFloat()+1e-6f
        for(i in f.indices)f[i]/=norm
        return f
    }
}
