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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private var message by mutableStateOf("Systems ready. Awaiting activation.")
    private var batteryText by mutableStateOf("Battery: checking…")
    private var clockText by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) message = "Microphone ready. JARVIS is awaiting activation."
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
        val transition = rememberInfiniteTransition(label = "jarvis_core")
        val pulse by transition.animateFloat(initialValue = 0.82f, targetValue = 1.08f, animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
        LaunchedEffect(Unit) {
            while (true) {
                clockText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                refreshStatus()
                delay(1000)
            }
        }
        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF030B16)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 30.sp)
                            Text("PERSONAL AI CONTROL SYSTEM", color = Color(0xFF7891AA), fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(clockText, color = Color(0xFF42E8F4), fontSize = 16.sp)
                            Text(if (active) "ONLINE" else "STANDBY", color = if (active) Color(0xFF7DEFF2) else Color(0xFF7891AA), fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.minDimension * 0.31f
                            drawCircle(Color(0xFF061426), r * 1.35f)
                            drawCircle(Color(0xFF42E8F4), r * pulse, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
                            drawArc(Color(0xFF168CFF), -35f, 105f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round), topLeft = Offset(c.x-r*1.18f, c.y-r*1.18f), size = androidx.compose.ui.geometry.Size(r*2.36f, r*2.36f))
                            drawArc(Color(0xFF6366F1), 145f, 105f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round), topLeft = Offset(c.x-r*1.18f, c.y-r*1.18f), size = androidx.compose.ui.geometry.Size(r*2.36f, r*2.36f))
                            drawCircle(Color(0xFF7DEFF2), r * 0.36f)
                            drawCircle(Color(0xFF061426), r * 0.23f)
                        }
                        Text(if (active) "LISTENING" else "JARVIS", color = Color(0xFF7DEFF2), fontSize = 18.sp)
                    }
                    Text(message, color = Color(0xFFD6E2F0), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF071A2B)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("SYSTEM TELEMETRY", color = Color(0xFF42E8F4), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            TelemetryRow("CORE", if (active) "ACTIVE" else "STANDBY")
                            TelemetryRow("VOICE", if (hasMicPermission()) "READY" else "PERMISSION NEEDED")
                            TelemetryRow("COMMAND ENGINE", "ONLINE")
                            TelemetryRow("NATIVE TTS", "ONLINE")
                            TelemetryRow("POWER", batteryText.removePrefix("Battery: "))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { toggleVoice() }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A3550))) { Text(if (active) "DEACTIVATE JARVIS" else "ACTIVATE JARVIS", color = Color(0xFF7DEFF2)) }
                    Spacer(Modifier.height(12.dp))
                    Text("QUICK COMMANDS", color = Color(0xFF42E8F4), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth()) {
                        HudButton("DIAGNOSTICS", Modifier.weight(1f)) { runQuickCommand("run diagnostics") }
                        Spacer(Modifier.width(8.dp))
                        HudButton("STATUS", Modifier.weight(1f)) { runQuickCommand("system status") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        HudButton("GAMING", Modifier.weight(1f)) { runQuickCommand("gaming mode") }
                        Spacer(Modifier.width(8.dp))
                        HudButton("WORK", Modifier.weight(1f)) { runQuickCommand("work mode") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF061426)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("COMMAND CONSOLE", color = Color(0xFF42E8F4), fontSize = 12.sp)
                            Spacer(Modifier.height(7.dp))
                            Text("Say:  JARVIS, open Discord", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                            Text("Say:  JARVIS, set a timer for 10 minutes", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                            Text("Say:  JARVIS, what is my battery?", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                            Text("Say:  JARVIS, start gaming mode", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("PHONE ACCESS • OPTIONAL", color = Color(0xFF7DEFF2)) }
                    Spacer(Modifier.height(10.dp))
                    Text("Screen-level controls may be limited by Android or account restrictions.", color = Color(0xFF60778E), fontSize = 10.sp)
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    @Composable private fun TelemetryRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF71879B), fontSize = 11.sp)
            Text(value, color = Color(0xFFD6E2F0), fontSize = 11.sp)
        }
    }

    @Composable private fun HudButton(label: String, modifier: Modifier, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(44.dp).border(1.dp, Color(0xFF164B68), RoundedCornerShape(10.dp)), shape = RoundedCornerShape(10.dp)) { Text(label, color = Color(0xFF9FEFF2), fontSize = 11.sp) }
    }

    private fun runQuickCommand(command: String) {
        val result = JarvisCommandEngine.execute(this, command)
        message = result
    }

    private fun toggleVoice() {
        if (!active && !hasMicPermission()) { requestPermissionsIfNeeded(); return }
        val action = if (active) JarvisVoiceService.ACTION_STOP else JarvisVoiceService.ACTION_START
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(action)
        if (!active) startForegroundService(intent) else startService(intent)
        active = !active
        message = if (active) "JARVIS is listening. Awaiting your command." else "JARVIS is offline."
    }

    private fun hasMicPermission(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        active = getSharedPreferences("jarvis", MODE_PRIVATE).getBoolean("active", active)
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
