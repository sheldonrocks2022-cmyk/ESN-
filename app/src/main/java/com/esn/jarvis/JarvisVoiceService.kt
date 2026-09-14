package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.jarvis.JarvisCommandEngine
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.esn.jarvis.START"
        const val ACTION_STOP = "com.esn.jarvis.STOP"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
        private const val PREFS = "jarvis"
        private const val ACTIVE = "active"
        private const val CODE_101 = "code 101"
        private const val WAKE = "jarvis"
        private const val DEFAULT_RATE = 0.82f
        private const val DEFAULT_PITCH = 0.72f
    }

    private var recognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var active = false
    private var commandMode = false
    private var restarting = false
    private var waitingForSpeechToFinish = false
    private var pendingSpeech: String? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        fallbackTts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) deactivate() else activate()
        return START_STICKY
    }

    private fun activate() {
        active = true
        commandMode = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, true).apply()
        startForeground(NOTIFICATION_ID, notification("Active — say JARVIS"))
        speak("Good evening. JARVIS is online. How may I assist you?")
    }

    private fun deactivate() {
        active = false
        commandMode = false
        restarting = false
        waitingForSpeechToFinish = false
        pendingSpeech = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        fallbackTts?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRecognition() {
        if (!active || restarting || waitingForSpeechToFinish || !fallbackTtsReady || !SpeechRecognizer.isRecognitionAvailable(this)) return
        restarting = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { restarting = false }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                restarting = false
                if (active && !waitingForSpeechToFinish) restartRecognition()
            }
            override fun onResults(results: Bundle?) {
                restarting = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (spoken.isBlank()) {
                    restartRecognition()
                    return
                }
                handleSpeech(spoken)
                if (!isSpeaking() && !waitingForSpeechToFinish) restartRecognition()
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.UK.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            restarting = false
            restartRecognition()
        }
    }

    private fun restartRecognition() {
        restarting = false
        if (active && !waitingForSpeechToFinish && fallbackTtsReady) {
            android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 600)
        }
    }

    private fun handleSpeech(spoken: String) {
        if (spoken.isBlank()) return
        val normalized = spoken.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9 ]"), "").trim()
        if (normalized.contains(CODE_101)) {
            speak("Code 101 acknowledged. I shall stand down.")
            android.os.Handler(mainLooper).postDelayed({ deactivate() }, 1400)
            return
        }
        if (!commandMode) {
            if (normalized == WAKE || normalized.startsWith("$WAKE ") || normalized.startsWith("hey $WAKE ")) {
                commandMode = true
                val command = normalized.removePrefix("hey ").removePrefix(WAKE).trim()
                if (command.isNotBlank()) execute(command) else speak("Yes, sir?")
            }
            return
        }
        execute(spoken)
        commandMode = false
    }

    private fun execute(command: String) {
        val result = JarvisCommandEngine(this).execute(command)
        speak(result)
    }

    private fun speak(text: String) {
        if (text.isBlank() || !active) return
        val polished = polishForVoice(text)
        waitingForSpeechToFinish = true
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        pendingSpeech = polished
        if (fallbackTtsReady) {
            speakFallback(polished)
        }
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady || !active) return
        pendingSpeech = null
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val rate = prefs.getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.5f, 1.5f)
        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(rate)
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    private fun isSpeaking(): Boolean = fallbackTts?.isSpeaking == true

    private fun polishForVoice(text: String): String {
        var result = text.trim()
        result = result
            .replace("I couldn't", "I'm afraid I couldn't")
            .replace("I can't", "I'm afraid I can't")
            .replace("I can’t", "I'm afraid I can't")
            .replace("Opening ", "Certainly. Opening ")
            .replace("Launching ", "Certainly. Launching ")
            .replace("Searching for ", "Certainly. Searching for ")
            .replace("Done.", "Very good. Done.")
            .replace("Cancelled.", "Very well. Cancelled.")
            .replace("Canceling.", "Very well. Canceling.")
            .replace("Settings opened.", "Certainly. I've opened Settings.")
            .replace("Not available", "I'm afraid that isn't available")
            .replace("not available", "I'm afraid that isn't available")
            .replace("Phone Access isn't enabled", "I'm afraid Phone Access is not enabled")
            .replace("Phone Access is not enabled", "I'm afraid Phone Access is not enabled")
            .replace("Review it and tap Send", "Please review the message, then tap Send")
        if (result.equals("Okay.", ignoreCase = true)) result = "Very good."
        else if (result.equals("Done", ignoreCase = true)) result = "Very good. Done."
        return result
    }

    private fun notification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
        } else {
            Notification.Builder(this).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val british = fallbackTts?.setLanguage(Locale.UK)
        fallbackTtsReady = british != TextToSpeech.LANG_MISSING_DATA && british != TextToSpeech.LANG_NOT_SUPPORTED
        if (!fallbackTtsReady) return
        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(getSharedPreferences(PREFS, MODE_PRIVATE).getFloat("speech_rate", DEFAULT_RATE))
        fallbackTts?.voices?.firstOrNull { voice ->
            voice.locale.language == "en" && voice.locale.country == "GB" && !voice.isNetworkConnectionRequired
        }?.let { fallbackTts?.setVoice(it) }
        fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                android.os.Handler(mainLooper).post {
                    waitingForSpeechToFinish = false
                    if (active) startRecognition()
                }
            }
            override fun onError(utteranceId: String?) {
                android.os.Handler(mainLooper).post {
                    waitingForSpeechToFinish = false
                    if (active) startRecognition()
                }
            }
        })
        val pending = pendingSpeech
        if (active && !pending.isNullOrBlank()) {
            speakFallback(pending)
        } else if (active) {
            waitingForSpeechToFinish = false
            startRecognition()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        fallbackTts?.stop()
        fallbackTts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
