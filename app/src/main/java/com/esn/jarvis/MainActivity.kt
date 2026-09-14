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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private var message by mutableStateOf("JARVIS is ready. Microphone access is required for voice commands.")
    private var batteryText by mutableStateOf("Battery: checking…")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) message = "Microphone ready. Tap Activate JARVIS."
        else message = "Microphone permission is required for voice commands."
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        refreshStatus()
        setContent { JarvisScreen() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    @Composable
    private fun JarvisScreen() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF061426)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(28.dp))
                    Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 44.sp)
                    Text("PERSONAL AI CONTROL SYSTEM", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))

                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A2038))
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(if (active) "SYSTEM: ONLINE" else "SYSTEM: STANDBY", color = Color(0xFF42E8F4), fontSize = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(batteryText, color = Color.White, fontSize = 14.sp)
                            Text(if (hasMicPermission()) "Microphone: READY" else "Microphone: NEEDS PERMISSION", color = Color.White, fontSize = 14.sp)
                            Text("Command engine: ONLINE", color = Color.White, fontSize = 14.sp)
                            Text("Native voice: ONLINE", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(message, color = Color(0xFFD6E2F0), fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))

                    Button(onClick = { toggleVoice() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (active) "Deactivate JARVIS" else "Activate JARVIS")
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = { runQuickCommand("run diagnostics") }, modifier = Modifier.weight(1f)) {
                            Text("Diagnostics")
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { runQuickCommand("system status") }, modifier = Modifier.weight(1f)) {
                            Text("System Status")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = { runQuickCommand("gaming mode") }, modifier = Modifier.weight(1f)) {
                            Text("Gaming Mode")
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { runQuickCommand("work mode") }, modifier = Modifier.weight(1f)) {
                            Text("Work Mode")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Phone Access (Optional)") }

                    Spacer(Modifier.height(22.dp))
                    Text("VOICE COMMANDS", color = Color(0xFF7DEFF2), fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "“JARVIS, open Discord”\n“JARVIS, open Minecraft”\n“JARVIS, search YouTube for Fortnite”\n“JARVIS, set a timer for 10 minutes”\n“JARVIS, remind me to check Discord in 20 minutes”\n“JARVIS, set brightness to 50 percent”\n“JARVIS, what is my battery?”\n“JARVIS, speak slower”\n“JARVIS, start gaming mode”",
                        color = Color(0xFFB8C7D9),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(22.dp))
                    Text("Phone Access is optional. Android or account restrictions can prevent screen-level controls such as Back, Home, scrolling, clicking, and typing.", color = Color(0xFF8094AA), fontSize = 12.sp)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    private fun runQuickCommand(command: String) {
        val result = com.example.jarvis.JarvisCommandEngine(this).execute(command)
        message = result
    }

    private fun toggleVoice() {
        if (!active && !hasMicPermission()) {
            requestPermissionsIfNeeded()
            return
        }
        val action = if (active) JarvisVoiceService.ACTION_STOP else JarvisVoiceService.ACTION_START
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(action)
        if (!active) startForegroundService(intent) else startService(intent)
        active = !active
        message = if (active) "JARVIS is listening. Say “JARVIS” followed by a command." else "JARVIS is offline."
    }

    private fun hasMicPermission(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        active = getSharedPreferences("jarvis", MODE_PRIVATE).getBoolean("active", false)
        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        batteryText = if (level >= 0) "Battery: $level%" else "Battery: unavailable"
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (!hasMicPermission()) permissions += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }
}
