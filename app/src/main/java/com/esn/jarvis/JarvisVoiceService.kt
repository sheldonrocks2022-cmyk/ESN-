package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var active = false
    private var commandMode = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) deactivate() else activate()
        return START_STICKY
    }

    private fun activate() {
        active = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, true).apply()
        startForeground(NOTIFICATION_ID, notification("Active — say JARVIS"))
        speak("JARVIS online. How can I help?")
        startRecognition()
    }

    private fun deactivate() {
        active = false
        commandMode = false
        restarting = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRecognition() {
        if (!active || restarting || !SpeechRecognizer.isRecognitionAvailable(this)) return
        restarting = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { restarting = false }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { restartRecognition() }
            override fun onError(error: Int) { restartRecognition() }
            override fun onResults(results: android.os.Bundle?) {
                restarting = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                handleSpeech(spoken)
                restartRecognition()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
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
        if (active) {
            android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 500)
        }
    }

    private fun handleSpeech(spoken: String) {
        if (spoken.isBlank()) return
        val normalized = spoken.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9 ]"), "").trim()
        if (normalized.contains(CODE_101)) {
            speak("Code 101 acknowledged. Going offline.")
            android.os.Handler(mainLooper).postDelayed({ deactivate() }, 900)
            return
        }
        if (!commandMode) {
            if (normalized == WAKE || normalized.startsWith("$WAKE ") || normalized.startsWith("hey $WAKE ")) {
                commandMode = true
                val command = normalized.removePrefix("hey ").removePrefix(WAKE).trim()
                if (command.isNotBlank()) execute(command) else speak("Yes?")
            }
            return
        }
        execute(spoken)
        commandMode = false
    }

    private fun execute(command: String) {
        speak("Okay.")
        val result = JarvisCommandEngine.execute(this, command)
        speak(result)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
        }
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
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (active && ttsReady) speak("Voice system ready.")
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
