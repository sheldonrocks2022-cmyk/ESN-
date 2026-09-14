package com.esn.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private var message by mutableStateOf("JARVIS is ready. Microphone access is required for voice commands.")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) message = "Microphone ready. Tap Activate JARVIS."
        else message = "Microphone permission is required for voice commands."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContent { JarvisScreen() }
    }

    @Composable
    private fun JarvisScreen() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF061426)) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 42.sp)
                    Text("VOICE ASSISTANT", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(32.dp))
                    Text(if (active) "VOICE SYSTEM: ACTIVE" else "VOICE SYSTEM: OFFLINE", color = Color(0xFF42E8F4), fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = Color(0xFFD6E2F0), fontSize = 15.sp)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { toggleVoice() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (active) "Deactivate JARVIS" else "Activate JARVIS")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Phone Access (Optional)")
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Basic commands work without Phone Access.\nTry: “JARVIS, open Discord”, “JARVIS, open YouTube”,\n“JARVIS, search the web for cats”, or “JARVIS, volume up”.", color = Color(0xFFB8C7D9), fontSize = 13.sp)
                }
            }
        }
    }

    private fun toggleVoice() {
        if (!active && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsIfNeeded()
            return
        }
        val action = if (active) JarvisVoiceService.ACTION_STOP else JarvisVoiceService.ACTION_START
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(action)
        if (!active) startForegroundService(intent) else startService(intent)
        active = !active
        message = if (active) "JARVIS is listening. Say “JARVIS” followed by a command." else "JARVIS is offline."
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }
}
