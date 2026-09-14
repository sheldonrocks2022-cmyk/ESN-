package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

    private var recognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var active = false
    private var commandMode = false
    private var restarting = false
    private var waitingForSpeechToFinish = false
    private var pendingSpeech: String? = null
    private var audioManager: AudioManager? = null

    private val incomingMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active || intent?.action != JarvisNotificationListenerService.ACTION_INCOMING_MESSAGE) return
            val title = intent.getStringExtra(JarvisNotificationListenerService.EXTRA_TITLE).orEmpty().trim()
            val text = intent.getStringExtra(JarvisNotificationListenerService.EXTRA_TEXT).orEmpty().trim()
            if (title.isBlank() || text.isBlank()) return
            speak("Incoming message from $title. $text")
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        fallbackTts = TextToSpeech(this, this)
        createChannel()
        val filter = IntentFilter(JarvisNotificationListenerService.ACTION_INCOMING_MESSAGE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(incomingMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(incomingMessageReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) deactivate() else activate()
        return START_STICKY
    }

    private fun activate() {
        active = true
        commandMode = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ACTIVE, true).apply()
        startForeground(NOTIFICATION_ID, notification("Active — listening for commands"))
        speak("JARVIS online.")
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
                val resultsList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val spoken = resultsList.firstOrNull().orEmpty().trim()
                if (spoken.isBlank()) { restartRecognition(); return }
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { restarting = false; restartRecognition() }
    }

    private fun restartRecognition() {
        restarting = false
        if (active && !waitingForSpeechToFinish && fallbackTtsReady) {
            android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 400)
        }
    }

    private fun handleSpeech(spoken: String) {
        val normalized = spoken.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) { restartRecognition(); return }

        if (normalized.contains(CODE_101)) {
            speak("Code 101 acknowledged. Standing down.")
            android.os.Handler(mainLooper).postDelayed({ deactivate() }, 1400)
            return
        }

        val command = if (normalized.startsWith(WAKE + " ")) normalized.removePrefix(WAKE).trim()
        else if (normalized == WAKE) ""
        else normalized

        if (command.isBlank()) {
            speak("Listening.")
            commandMode = true
            return
        }

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
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        pendingSpeech = polished
        if (fallbackTtsReady) speakFallback(polished)
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady || !active) return
        pendingSpeech = null
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val rate = prefs.getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.60f, 1.15f)
        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(rate)
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
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
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val english = fallbackTts?.setLanguage(Locale.US)
        fallbackTtsReady = english != TextToSpeech.LANG_MISSING_DATA && english != TextToSpeech.LANG_NOT_SUPPORTED
        if (!fallbackTtsReady) return

        val voices = fallbackTts?.voices.orEmpty()
        val bestVoice = voices.asSequence()
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedWith(
                compareByDescending<Voice> { voice ->
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
                }.thenByDescending { it.quality }.thenBy { it.latency }
            ).firstOrNull()
        if (bestVoice != null) fallbackTts?.voice = bestVoice

        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(getSharedPreferences(PREFS, MODE_PRIVATE).getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.60f, 1.15f))
        fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { android.os.Handler(mainLooper).post { waitingForSpeechToFinish = false; if (active) startRecognition() } }
            override fun onError(utteranceId: String?) { android.os.Handler(mainLooper).post { waitingForSpeechToFinish = false; if (active) startRecognition() } }
        })
        val pending = pendingSpeech
        if (active && !pending.isNullOrBlank()) speakFallback(pending)
        else if (active) { waitingForSpeechToFinish = false; startRecognition() }
    }

    override fun onDestroy() {
        try { unregisterReceiver(incomingMessageReceiver) } catch (_: Exception) { }
        recognizer?.destroy()
        fallbackTts?.stop()
        fallbackTts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
