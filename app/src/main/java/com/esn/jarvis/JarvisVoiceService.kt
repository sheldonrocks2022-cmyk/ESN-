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
        private const val DEFAULT_RATE = 0.68f
        private const val DEFAULT_PITCH = 0.58f
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
            android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 500)
        }
    }

    private fun handleSpeech(spoken: String) {
        if (spoken.isBlank()) { restartRecognition(); return }
        val normalized = spoken.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()
        if (normalized.contains(CODE_101)) {
            speak("Code 101 acknowledged. Standing down.")
            android.os.Handler(mainLooper).postDelayed({ deactivate() }, 1400)
            return
        }

        val wakeIndex = normalized.indexOf(WAKE)
        if (wakeIndex >= 0) {
            commandMode = true
            val command = normalized.substring(wakeIndex + WAKE.length).trim()
            if (command.isNotBlank()) {
                execute(command)
                commandMode = false
            } else {
                speak("Yes?")
            }
            return
        }

        if (commandMode) {
            execute(normalized)
            commandMode = false
            return
        }

        // Also accept a clear command without the wake word. This makes the assistant
        // usable when Android's speech recognizer drops the first word of a sentence.
        if (looksLikeCommand(normalized)) {
            execute(normalized)
            return
        }

        restartRecognition()
    }

    private fun looksLikeCommand(text: String): Boolean {
        val prefixes = listOf(
            "open ", "launch ", "start ", "run ", "search ", "find ", "google ", "look up ",
            "play", "pause", "resume", "next", "previous", "volume", "turn the volume", "mute", "unmute",
            "flashlight", "flash light", "torch", "brightness", "battery", "charge", "android version",
            "phone model", "what phone", "status", "diagnostics", "storage", "wifi", "bluetooth",
            "airplane mode", "location settings", "sound settings", "display settings", "notification settings",
            "app settings", "settings", "back", "go back", "home", "go home", "recent apps", "recents",
            "scroll ", "click ", "tap ", "type ", "enter ", "what time", "time", "what date", "date",
            "set a timer", "start a timer", "timer for ", "remind me", "set a reminder", "set an alarm", "set alarm",
            "alarm for ", "text ", "send a text", "send sms", "sms ", "call ", "phone ", "dial ", "email",
            "camera", "take a picture", "take a photo", "clock", "calendar", "contacts", "downloads",
            "gaming mode", "work mode", "speak slower", "talk slower", "speak faster", "talk faster",
            "normal speech", "calculate ", "who are you", "what are you", "hello", "hi jarvis"
        )
        return prefixes.any { text == it.trim() || text.startsWith(it) }
    }

    private fun execute(command: String) {
        val result = JarvisCommandEngine.execute(this, command)
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
        if (fallbackTtsReady) speakFallback(polished)
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady || !active) return
        pendingSpeech = null
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val rate = prefs.getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.55f, 1.15f)
        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(rate)
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    private fun polishForVoice(text: String): String {
        var result = text.trim()
        result = result
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
            .filter { voice -> voice.locale.language == "en" && !voice.isNetworkConnectionRequired }
            .sortedWith(
                compareByDescending<Voice> { voice ->
                    val n = voice.name.lowercase(Locale.US)
                    when {
                        n.contains("en-gb") && n.contains("male") -> 100
                        n.contains("en-gb") -> 90
                        n.contains("en-us") && n.contains("male") -> 85
                        n.contains("en-us") -> 80
                        n.contains("en-au") && n.contains("male") -> 75
                        n.contains("en-au") -> 70
                        else -> 50
                    }
                }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
            )
            .firstOrNull()
        if (bestVoice != null) fallbackTts?.voice = bestVoice

        fallbackTts?.setPitch(DEFAULT_PITCH)
        fallbackTts?.setSpeechRate(getSharedPreferences(PREFS, MODE_PRIVATE).getFloat("speech_rate", DEFAULT_RATE).coerceIn(0.55f, 1.15f))
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
        recognizer?.destroy()
        fallbackTts?.stop()
        fallbackTts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
