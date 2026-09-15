package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.ServiceCompat
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
        private const val DEFAULT_RATE = 0.72f
        private const val DEFAULT_PITCH = 0.68f
    }

    private val handler = Handler(mainLooper)
    private var recognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var active = false
    private var commandMode = false
    private var recognitionStarting = false
    private var waitingForSpeechToFinish = false
    private var pendingSpeech: String? = null
    private var lastRecognitionStart = 0L
    private var recognitionFailures = 0
    private var receiverRegistered = false

    private val restartRunnable = Runnable {
        recognitionStarting = false
        startRecognition()
    }

    private val incomingMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (!active || intent?.action != JarvisNotificationListenerService.ACTION_INCOMING_MESSAGE) return
                val title = intent.getStringExtra(JarvisNotificationListenerService.EXTRA_TITLE).orEmpty().trim()
                val text = intent.getStringExtra(JarvisNotificationListenerService.EXTRA_TEXT).orEmpty().trim()
                if (title.isBlank() || text.isBlank()) return
                speak("Incoming message from $title. $text")
            } catch (_: Exception) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try { createChannel() } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_STOP -> deactivate()
                ACTION_START -> activate()
                else -> stopSelf()
            }
        } catch (e: Exception) {
            active = false
            commandMode = false
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
            try { stopSelf() } catch (_: Exception) { }
        }
        return START_NOT_STICKY
    }

    private fun activate() {
        if (active) {
            commandMode = true
            if (!waitingForSpeechToFinish) startRecognition()
            return
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
            stopSelf()
            return
        }

        try {
            val notification = notification("JARVIS active — listening")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            failActivation()
            return
        }

        try {
            if (!receiverRegistered) {
                val filter = IntentFilter(JarvisNotificationListenerService.ACTION_INCOMING_MESSAGE)
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(incomingMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else @Suppress("DEPRECATION") registerReceiver(incomingMessageReceiver, filter)
                receiverRegistered = true
            }
        } catch (_: Exception) { }

        try {
            fallbackTts = TextToSpeech(this, this)
        } catch (_: Exception) {
            fallbackTts = null
            fallbackTtsReady = false
        }

        active = true
        commandMode = true
        recognitionFailures = 0
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, true).apply()
        speak("JARVIS online.")
    }

    private fun failActivation() {
        active = false
        commandMode = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        try { stopSelf() } catch (_: Exception) { }
    }

    private fun deactivate() {
        active = false
        commandMode = false
        recognitionStarting = false
        waitingForSpeechToFinish = false
        pendingSpeech = null
        recognitionFailures = 0
        handler.removeCallbacks(restartRunnable)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, false).apply()
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        try { fallbackTts?.stop() } catch (_: Exception) { }
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        stopSelf()
    }

    private fun startRecognition() {
        if (!active || waitingForSpeechToFinish || recognitionStarting || !SpeechRecognizer.isRecognitionAvailable(this)) return
        val now = System.currentTimeMillis()
        if (now - lastRecognitionStart < 900L) return
        lastRecognitionStart = now
        recognitionStarting = true

        try {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { recognitionStarting = false; recognitionFailures = 0 }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    recognitionStarting = false
                    if (!active || waitingForSpeechToFinish) return
                    recognitionFailures = (recognitionFailures + 1).coerceAtMost(8)
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1800L
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> 3000L
                        else -> (1800L + recognitionFailures * 400L).coerceAtMost(5000L)
                    }
                    scheduleRecognition(delay)
                }
                override fun onResults(results: Bundle?) {
                    recognitionStarting = false
                    recognitionFailures = 0
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isBlank()) { scheduleRecognition(1200L); return }
                    handleSpeech(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            recognitionStarting = false
            scheduleRecognition(3000L)
        }
    }

    private fun scheduleRecognition(delay: Long) {
        if (!active || waitingForSpeechToFinish) return
        handler.removeCallbacks(restartRunnable)
        recognitionStarting = true
        handler.postDelayed(restartRunnable, delay)
    }

    private fun handleSpeech(spoken: String) {
        val normalized = spoken.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) { scheduleRecognition(1200L); return }
        if (normalized.contains(CODE_101)) { speak("Code 101 acknowledged. Standing down."); handler.postDelayed({ deactivate() }, 1400L); return }
        val command = when {
            normalized.startsWith(WAKE + " ") -> normalized.removePrefix(WAKE).trim()
            normalized == WAKE -> ""
            else -> normalized
        }
        if (command.isBlank()) { speak("Listening."); commandMode = true; return }
        commandMode = true
        execute(command)
    }

    private fun execute(command: String) {
        val naturalResult = JarvisNaturalCommandRouter.execute(this, command)
        speak(naturalResult ?: JarvisCommandEngine.execute(this, command))
    }

    private fun speak(text: String) {
        if (text.isBlank() || !active) return
        val polished = polishForVoice(text)
        waitingForSpeechToFinish = true
        handler.removeCallbacks(restartRunnable)
        recognitionStarting = false
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        pendingSpeech = polished
        if (fallbackTtsReady) speakFallback(polished)
        else { pendingSpeech = null; waitingForSpeechToFinish = false; scheduleRecognition(500L) }
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady || !active) return
        pendingSpeech = null
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        try {
            fallbackTts?.setPitch(DEFAULT_PITCH)
            fallbackTts?.setSpeechRate(prefs.getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.60f, 1.15f))
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
        } catch (_: Exception) {
            waitingForSpeechToFinish = false
            scheduleRecognition(1000L)
        }
    }

    private fun polishForVoice(text: String): String {
        var result = text.trim()
            .replace("I'm afraid I couldn't", "I couldn't")
            .replace("I'm afraid I can't", "I can't")
            .replace("Certainly. Opening ", "Opening ")
            .replace("Certainly. Launching ", "Launching ")
            .replace("Certainly. Searching for ", "Searching for ")
            .replace("Very good. Done.", "Done.")
            .replace("Very well. Cancelled.", "Cancelled.")
            .replace("Very well. Canceling.", "Canceling.")
            .replace("Certainly. I've opened Settings.", "Settings opened.")
            .replace("I'm afraid that isn't available", "That isn't available")
            .replace("I'm afraid Phone Access is not enabled", "Phone Access is not enabled")
            .replace("Please review the message, then tap Send", "Review the message, then tap Send")
        if (result.length > 180) result = result.take(177).trimEnd() + "..."
        return result
    }

    private fun notification(text: String): Notification = if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, CHANNEL_ID).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    } else {
        Notification.Builder(this).setContentTitle("JARVIS").setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status != TextToSpeech.SUCCESS) {
                fallbackTtsReady = false
                pendingSpeech = null
                waitingForSpeechToFinish = false
                if (active) scheduleRecognition(500L)
                return
            }
            val english = fallbackTts?.setLanguage(Locale.US)
            fallbackTtsReady = english != TextToSpeech.LANG_MISSING_DATA && english != TextToSpeech.LANG_NOT_SUPPORTED
            if (!fallbackTtsReady) {
                pendingSpeech = null
                waitingForSpeechToFinish = false
                if (active) scheduleRecognition(500L)
                return
            }
            val bestVoice = fallbackTts?.voices.orEmpty().asSequence()
                .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                .sortedWith(compareByDescending<Voice> { voice ->
                    val n = voice.name.lowercase(Locale.US)
                    when {
                        (n.contains("en-gb") || n.contains("en_uk")) && (n.contains("male") || n.contains("rjs") || n.contains("rjc")) -> 110
                        n.contains("en-gb") || n.contains("en_uk") -> 100
                        (n.contains("en-us") || n.contains("en_us")) && (n.contains("male") || n.contains("rjs") || n.contains("rjc")) -> 95
                        n.contains("en-us") || n.contains("en_us") -> 85
                        n.contains("en-au") && (n.contains("male") || n.contains("rjs") || n.contains("rjc")) -> 80
                        n.contains("en-au") -> 75
                        else -> 50
                    }
                }.thenByDescending { it.quality }.thenBy { it.latency }).firstOrNull()
            if (bestVoice != null) fallbackTts?.voice = bestVoice
            fallbackTts?.setPitch(DEFAULT_PITCH)
            fallbackTts?.setSpeechRate(getSharedPreferences(PREFS, MODE_PRIVATE).getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.60f, 1.15f))
            fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { handler.post { waitingForSpeechToFinish = false; if (active) scheduleRecognition(500L) } }
                override fun onError(utteranceId: String?) { handler.post { waitingForSpeechToFinish = false; if (active) scheduleRecognition(1000L) } }
            })
            val pending = pendingSpeech
            if (active && !pending.isNullOrBlank()) speakFallback(pending)
            else if (active && !waitingForSpeechToFinish) scheduleRecognition(500L)
        } catch (_: Exception) {
            fallbackTtsReady = false
            pendingSpeech = null
            waitingForSpeechToFinish = false
            if (active) scheduleRecognition(1000L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            try { unregisterReceiver(incomingMessageReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        try { fallbackTts?.stop() } catch (_: Exception) { }
        try { fallbackTts?.shutdown() } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
