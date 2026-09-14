package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.example.jarvis.JarvisCommandEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

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
        private const val FISH_VOICE_ID = "612b878b113047d9a770c069c8b4fdfe"
        private const val FISH_ENDPOINT = "https://api.fish.audio/v1/tts"
        private const val FISH_MODEL = "s2.1-pro"
    }

    private var recognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var active = false
    private var commandMode = false
    private var restarting = false
    private var mediaPlayer: MediaPlayer? = null
    private val voiceExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        fallbackTts = TextToSpeech(this, this)
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
        speak("Good evening. JARVIS is online. How may I assist you?")
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
        mediaPlayer?.release()
        mediaPlayer = null
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.UK)
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
        if (active) android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 500)
    }

    private fun handleSpeech(spoken: String) {
        if (spoken.isBlank()) return
        val normalized = spoken.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9 ]"), "").trim()
        if (normalized.contains(CODE_101)) {
            speak("Code 101 acknowledged. I shall stand down.")
            android.os.Handler(mainLooper).postDelayed({ deactivate() }, 1200)
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
        if (text.isBlank()) return
        val polished = polishForVoice(text)
        val apiKey = BuildConfig.FISH_AUDIO_API_KEY.trim()
        if (apiKey.isBlank()) {
            speakFallback(polished)
            return
        }
        recognizer?.cancel()
        voiceExecutor.execute {
            try {
                val audio = requestFishAudio(polished, apiKey)
                playFishAudio(audio)
            } catch (_: Exception) {
                android.os.Handler(mainLooper).post { speakFallback(polished) }
            }
        }
    }

    private fun requestFishAudio(text: String, apiKey: String): ByteArray {
        val connection = (URL(FISH_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("model", FISH_MODEL)
        }
        val payload = JSONObject().apply {
            put("text", text)
            put("reference_id", FISH_VOICE_ID)
            put("format", "mp3")
        }.toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("Fish Audio HTTP $code")
        return connection.inputStream.use { it.readBytes() }
    }

    private fun playFishAudio(audio: ByteArray) {
        val file = File.createTempFile("jarvis_voice_", ".mp3", cacheDir)
        file.writeBytes(audio)
        android.os.Handler(mainLooper).post {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                        file.delete()
                        if (active) restartRecognition()
                    }
                    setOnErrorListener { player, _, _ ->
                        player.release()
                        mediaPlayer = null
                        file.delete()
                        if (active) restartRecognition()
                        true
                    }
                    prepareAsync()
                    setOnPreparedListener { it.start() }
                }
            } catch (_: Exception) {
                file.delete()
                if (active) restartRecognition()
            }
        }
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady) return
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

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
        if (status == TextToSpeech.SUCCESS) {
            val british = fallbackTts?.setLanguage(Locale.UK)
            fallbackTtsReady = british != TextToSpeech.LANG_MISSING_DATA && british != TextToSpeech.LANG_NOT_SUPPORTED
            if (fallbackTtsReady) {
                fallbackTts?.setPitch(0.72f)
                fallbackTts?.setSpeechRate(0.82f)
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.stop()
        fallbackTts?.shutdown()
        voiceExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
