package com.esn.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var listening by mutableStateOf(false)
    private var transcript by mutableStateOf("")
    private var response by mutableStateOf("Systems ready. Awaiting your command.")
    private val history = mutableStateListOf<String>()
    private lateinit var speech: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private val speechPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) response = "Microphone permission is required for voice commands."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) speech = SpeechRecognizer.createSpeechRecognizer(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speechPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent { JarvisScreen() }
    }

    @Composable
    private fun JarvisScreen() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF061426)) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 38.sp)
                    Text("ESN MOBILE ASSISTANT", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Surface(Modifier.fillMaxWidth(), color = Color(0xFF0A2038), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(18.dp)) {
                            Text(if (listening) "LISTENING..." else "STANDBY", color = Color(0xFF42E8F4), fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(transcript.ifBlank { response }, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(Modifier.weight(1f), onClick = { if (listening) stopListening() else startListening() }) {
                            Text(if (listening) "Stop" else "Talk")
                        }
                        OutlinedButton(Modifier.weight(1f), onClick = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Phone Access") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Try: \"open Discord\", \"open YouTube\", \"search the web for ESN\", \"volume up\", or \"go home\".", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("COMMAND LOG", color = Color(0xFF7DEFF2), fontSize = 13.sp)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(history) { item -> Text(item, color = Color(0xFFD6E2F0), fontSize = 13.sp, modifier = Modifier.padding(vertical = 5.dp)) }
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            response = "Speech recognition is unavailable on this device."
            speak(response)
            return
        }
        listening = true
        transcript = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; response = "I didn't catch that. Please try again." }
            override fun onResults(results: Bundle?) {
                listening = false
                val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (command.isNotBlank()) runCommand(command)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        speech.startListening(intent)
    }

    private fun stopListening() {
        listening = false
        speech.stopListening()
    }

    private fun runCommand(command: String) {
        transcript = command
        history.add("You: $command")
        val result = JarvisCommandEngine.execute(this, command)
        response = result
        history.add("JARVIS: $result")
        speak(result)
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
    }

    override fun onDestroy() {
        if (::speech.isInitialized) speech.destroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
