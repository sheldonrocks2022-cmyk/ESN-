package com.esn.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private var message by mutableStateOf("Systems ready. Awaiting activation.")
    private var batteryText by mutableStateOf("Battery: checking…")
    private var clockText by mutableStateOf("")
    private var lastCrash by mutableStateOf("")
    private var serviceStage by mutableStateOf("IDLE")
    private var lastHeard by mutableStateOf("")
    private var lastResult by mutableStateOf("")
    private var lastCommand by mutableStateOf("")
    private var standbyMode by mutableStateOf(false)
    private var voiceNames by mutableStateOf<List<String>>(emptyList())
    private var selectedVoice by mutableStateOf("")
    private var voiceRate by mutableStateOf(0.72f)
    private var voicePitch by mutableStateOf(0.64f)
    private var ownerVoiceEnrolled by mutableStateOf(false)
    private var enrollingVoice by mutableStateOf(false)
    private var discordGuildId by mutableStateOf("")
    private var modelStatus by mutableStateOf("NONE")
    private var voiceLoader: TextToSpeech? = null
    private val commandHistory = mutableStateListOf<String>()

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null){val r=JarvisModelManager.importGguf(this,uri);modelStatus=JarvisModelManager.installedSize(this);message=r.message} }

    private val deviceAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if(r.resultCode==android.app.Activity.RESULT_OK){getSharedPreferences("jarvis",MODE_PRIVATE).edit().putBoolean("emergency_shutdown",false).apply();message="Device authentication accepted. Emergency lock cleared; activate JARVIS when ready."}else message="Device authentication was not completed." }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) message = "Microphone ready. JARVIS is awaiting activation."
        else message = "Microphone permission is required for voice commands."
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashDiagnostics()
        super.onCreate(savedInstanceState)
        lastCrash = getSharedPreferences("jarvis", MODE_PRIVATE).getString("last_crash", "").orEmpty()
        requestPermissionsIfNeeded()
        refreshStatus()
        val persisted=getSharedPreferences("jarvis_history",MODE_PRIVATE).getString("items","").orEmpty().lines().filter{it.isNotBlank()}.takeLast(8)
        commandHistory.addAll(persisted)
        ownerVoiceEnrolled=OwnerVoiceProfile.isEnrolled(this)
        discordGuildId=getSharedPreferences("jarvis_discord_phone",MODE_PRIVATE).getString("guild_id","").orEmpty()
        modelStatus=JarvisModelManager.installedSize(this)
        loadVoices()
        setContent { JarvisScreen() }
    }

    private fun installCrashDiagnostics() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = StringWriter()
                throwable.printStackTrace(PrintWriter(stack))
                getSharedPreferences("jarvis", MODE_PRIVATE).edit()
                    .putString("last_crash", "Thread: ${thread.name}\n${stack}")
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    @Composable
    private fun JarvisScreen() {
        val transition = rememberInfiniteTransition(label = "jarvis_core")
        val pulse by transition.animateFloat(initialValue = 0.82f, targetValue = 1.08f, animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
        val scan by transition.animateFloat(initialValue = 0.08f, targetValue = 0.28f, animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "scan")
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
                        Column { Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 30.sp); Text("PERSONAL AI CONTROL SYSTEM • v${BuildConfig.VERSION_NAME}", color = Color(0xFF7891AA), fontSize = 10.sp) }
                        Column(horizontalAlignment = Alignment.End) { Text(clockText, color = Color(0xFF42E8F4), fontSize = 16.sp); Text(if (active) "ONLINE" else "STANDBY", color = if (active) Color(0xFF7DEFF2) else Color(0xFF7891AA), fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(10.dp)); HudStatusStrip(active); Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension * 0.31f
                            drawCircle(Color(0xFF061426), r * 1.35f); drawCircle(Color(0xFF42E8F4).copy(alpha = scan), r * 1.48f)
                            drawCircle(Color(0xFF42E8F4), r * pulse, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
                            drawArc(Color(0xFF168CFF), -35f, 105f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round), topLeft = Offset(c.x-r*1.18f, c.y-r*1.18f), size = androidx.compose.ui.geometry.Size(r*2.36f, r*2.36f))
                            drawArc(Color(0xFF6366F1), 145f, 105f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round), topLeft = Offset(c.x-r*1.18f, c.y-r*1.18f), size = androidx.compose.ui.geometry.Size(r*2.36f, r*2.36f))
                            drawCircle(Color(0xFF7DEFF2), r * 0.36f); drawCircle(Color(0xFF061426), r * 0.23f)
                            drawLine(Color(0xFF42E8F4).copy(alpha = scan), Offset(c.x-r*1.6f, c.y), Offset(c.x+r*1.6f, c.y), 1.5f)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (standbyMode) "STANDBY" else if (active) serviceStage.substringBefore(":").take(18) else "JARVIS", color = Color(0xFF7DEFF2), fontSize = 18.sp); Text(if (active) "VOICE LINK ACTIVE" else "CORE STANDBY", color = Color(0xFF7891AA), fontSize = 9.sp, modifier = Modifier.alpha(0.9f)) }
                    }
                    Text(message, color = Color(0xFFD6E2F0), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp)); Spacer(Modifier.height(12.dp))
                    if (lastCrash.isNotBlank()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF241017)), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(15.dp)) {
                                Text("LAST CRASH CAPTURED", color = Color(0xFFFFB4AB), fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(lastCrash.take(3500), color = Color(0xFFE6D8D8), fontSize = 10.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = {
                                    getSharedPreferences("jarvis", MODE_PRIVATE).edit().remove("last_crash").remove("last_crash_time").apply()
                                    lastCrash = ""
                                }, modifier = Modifier.fillMaxWidth()) { Text("CLEAR CRASH REPORT", color = Color(0xFFFFB4AB)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF071A2B)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("SYSTEM TELEMETRY", color = Color(0xFF42E8F4), fontSize = 12.sp); Spacer(Modifier.height(8.dp))
                            TelemetryRow("CORE", if (standbyMode) "STANDBY" else if (active) "ACTIVE" else "OFFLINE"); TelemetryRow("STATE", serviceStage.take(28)); TelemetryRow("VOICE", if (hasMicPermission()) "READY" else "PERMISSION NEEDED"); TelemetryRow("COMMAND ENGINE", "ONLINE"); TelemetryRow("NATIVE TTS", "ONLINE"); TelemetryRow("POWER", batteryText.removePrefix("Battery: "))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if(lastHeard.isNotBlank()||lastResult.isNotBlank()){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF061426)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){Text("JARVIS CONTROL CENTER",color=Color(0xFF42E8F4),fontSize=12.sp)
                    Text("Voice: local TTS  •  Standby: ${if(standbyMode)"ON" else "OFF"}",color=Color(0xFF7891AA),fontSize=11.sp)
                    Text("Say: list routines • list aliases • diagnostics • speak faster/slower",color=Color(0xFF7891AA),fontSize=10.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("LIVE COMMAND TRACE",color=Color(0xFF42E8F4),fontSize=12.sp);if(lastHeard.isNotBlank())Text("HEARD  •  $lastHeard",color=Color(0xFFB8C7D9),fontSize=11.sp);if(lastCommand.isNotBlank())Text("UNDERSTOOD •  $lastCommand",color=Color(0xFFB8C7D9),fontSize=11.sp);Text("ACTION •  ${serviceStage.take(36)}",color=Color(0xFF9FB3C7),fontSize=11.sp);if(lastResult.isNotBlank())Text("RESULT •  $lastResult",color=Color(0xFF9FB3C7),fontSize=11.sp)}};Spacer(Modifier.height(12.dp))}
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF071A2B)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(15.dp)) { Text("VOICE MATRIX", color = Color(0xFF42E8F4), fontSize = 12.sp); Text(if(selectedVoice.isBlank()) "AUTO • LOCAL ENGLISH" else selectedVoice.take(42), color = Color(0xFFD6E2F0), fontSize = 11.sp); Spacer(Modifier.height(7.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { voiceNames.take(3).forEachIndexed { index, name -> HudButton("VOICE ${index+1}", Modifier.weight(1f)) { selectVoice(name) } } }; Spacer(Modifier.height(8.dp)); Text("SPEED  ${(voiceRate*100).toInt()}%",color=Color(0xFF7891AA),fontSize=10.sp); Slider(value=voiceRate,onValueChange={voiceRate=it;saveVoiceTuning()},valueRange=0.55f..1.15f); Text("PITCH  ${(voicePitch*100).toInt()}%",color=Color(0xFF7891AA),fontSize=10.sp); Slider(value=voicePitch,onValueChange={voicePitch=it;saveVoiceTuning()},valueRange=0.55f..1.25f); Text("Changes apply the next time the voice engine starts.",color=Color(0xFF60778E),fontSize=9.sp) } }
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF071A2B)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(15.dp)) {
                        Text("SECURITY CENTER", color = Color(0xFF42E8F4), fontSize = 12.sp)
                        JarvisSecurity.status(this@MainActivity).forEach { TelemetryRow(it.first,it.second) }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton(if(ownerVoiceEnrolled)"RE-ENROLL VOICE" else "ENROLL OWNER VOICE", Modifier.weight(1f)) { enrollOwnerVoice() }
                            if(ownerVoiceEnrolled) HudButton("DELETE PROFILE", Modifier.weight(1f)) { OwnerVoiceProfile.clear(this@MainActivity);ownerVoiceEnrolled=false;message="Owner voice profile deleted." }
                        }
                        if(enrollingVoice) Text("Enrollment running locally. Say “JARVIS” naturally for each sample.",color=Color(0xFF9FB3C7),fontSize=10.sp)
                    } }
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF071A2B)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){Text("LOCAL AI MODEL",color=Color(0xFF42E8F4),fontSize=12.sp);TelemetryRow("GGUF MODEL",modelStatus);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){HudButton("IMPORT GGUF",Modifier.weight(1f)){modelPicker.launch(arrayOf("*/*"))};HudButton("DELETE MODEL",Modifier.weight(1f)){if(JarvisModelManager.delete(this@MainActivity)){modelStatus="NONE";message="Local model deleted."}else message="I could not delete the local model."}};Text("Models stay in JARVIS private app storage.",color=Color(0xFF60778E),fontSize=9.sp)}}
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF071A2B)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){Text("DISCORD SERVER MANAGER",color=Color(0xFF42E8F4),fontSize=12.sp);OutlinedTextField(value=discordGuildId,onValueChange={discordGuildId=it.filter(Char::isDigit)},label={Text("Server ID")},singleLine=true,modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){HudButton("SAVE",Modifier.weight(1f)){if(discordGuildId.isBlank())message="Enter a Discord server ID." else{getSharedPreferences("jarvis_discord_phone",MODE_PRIVATE).edit().putString("guild_id",discordGuildId).apply();message="Discord server saved for phone control."}};HudButton("OPEN",Modifier.weight(1f)){message=JarvisDiscordPhoneControl.openServer(this@MainActivity,discordGuildId)}};Text("Per-install Server ID. JARVIS controls the Discord app as the account logged into this phone. Destructive actions require confirmation.",color=Color(0xFF60778E),fontSize=9.sp)}}
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF061426)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){
                        Text("AGENT CONTROL CENTER",color=Color(0xFF42E8F4),fontSize=12.sp)
                        TelemetryRow("UNIFIED AGENT",JarvisUnifiedAgent.status(this@MainActivity).take(72))
                        TelemetryRow("SCREEN",JarvisScreenInspector.screenState().take(72))
                        TelemetryRow("PHONE ACCESS",if(JarvisAccessibilityService.hasAccess())"READY" else "OFF")
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            HudButton("AGENT STATUS",Modifier.weight(1f)){runQuickCommand("jarvis agent status")}
                            HudButton("STOP TASK",Modifier.weight(1f)){message=JarvisAgent.stop(this@MainActivity)}
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            HudButton("BRIEFING",Modifier.weight(1f)){message=JarvisPersonalMemory.briefing(this@MainActivity)}
                            HudButton("HEALTH",Modifier.weight(1f)){message=JarvisDiagnostics.health(this@MainActivity)}
                        }
                    }}
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF071A2B)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(15.dp)){
                        Text("MEMORY & AUTOMATION",color=Color(0xFF42E8F4),fontSize=12.sp)
                        Text(JarvisPersonalMemory.suggestion(this@MainActivity).take(180),color=Color(0xFF9FB3C7),fontSize=10.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            HudButton("LEARNING",Modifier.weight(1f)){message=JarvisPersonalMemory.learningReport(this@MainActivity)}
                            HudButton("AUTOMATIONS",Modifier.weight(1f)){message=JarvisAutomationEngine.list(this@MainActivity)}
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            HudButton("HISTORY",Modifier.weight(1f)){message=JarvisAutomationEngine.history(this@MainActivity)}
                            HudButton("WHY",Modifier.weight(1f)){message=JarvisUnifiedAgent.explain(this@MainActivity)}
                        }
                    }}
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { toggleVoice() }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A3550))) { Text(if (active) "DEACTIVATE JARVIS" else "ACTIVATE JARVIS", color = Color(0xFF7DEFF2)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { emergencyShutdown() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("EMERGENCY SHUTDOWN", color = Color(0xFFFFB4AB)) }
                    Spacer(Modifier.height(12.dp)); Text("QUICK COMMANDS", color = Color(0xFF42E8F4), fontSize = 12.sp, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth()) { HudButton("DIAGNOSTICS", Modifier.weight(1f)) { runQuickCommand("run diagnostics") }; Spacer(Modifier.width(8.dp)); HudButton("STATUS", Modifier.weight(1f)) { runQuickCommand("system status") } }
                    Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth()) { HudButton("GAMING", Modifier.weight(1f)) { runQuickCommand("gaming mode") }; Spacer(Modifier.width(8.dp)); HudButton("WORK", Modifier.weight(1f)) { runQuickCommand("work mode") } }
                    Spacer(Modifier.height(14.dp))
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF061426)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("COMMAND CONSOLE", color = Color(0xFF42E8F4), fontSize = 12.sp); Spacer(Modifier.height(7.dp))
                            Text("Say:  JARVIS, open Discord", color = Color(0xFFB8C7D9), fontSize = 12.sp); Text("Say:  JARVIS, set a timer for 10 minutes", color = Color(0xFFB8C7D9), fontSize = 12.sp); Text("Say:  JARVIS, what is my battery?", color = Color(0xFFB8C7D9), fontSize = 12.sp); Text("Say:  JARVIS, start gaming mode", color = Color(0xFFB8C7D9), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (commandHistory.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF071522)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(15.dp)) { Text("RECENT ACTIVITY", color = Color(0xFF42E8F4), fontSize = 12.sp); Spacer(Modifier.height(7.dp)); commandHistory.takeLast(5).reversed().forEach { entry -> Text(entry, color = Color(0xFF9FB3C7), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) } } }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("PHONE ACCESS • OPTIONAL", color = Color(0xFF7DEFF2)) }
                    Spacer(Modifier.height(10.dp)); Text("Screen-level controls may be limited by Android or account restrictions.", color = Color(0xFF60778E), fontSize = 10.sp); Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    @Composable private fun HudStatusStrip(active: Boolean) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { StatusChip("CORE", if (active) "LIVE" else "IDLE", Modifier.weight(1f)); StatusChip("VOICE", if (hasMicPermission()) "READY" else "LOCKED", Modifier.weight(1f)); StatusChip("TTS", "NATIVE", Modifier.weight(1f)) } }
    @Composable private fun StatusChip(label: String, value: String, modifier: Modifier) { Column(modifier.border(1.dp, Color(0xFF123B54), RoundedCornerShape(8.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Color(0xFF5E7A90), fontSize = 8.sp); Text(value, color = Color(0xFF7DEFF2), fontSize = 9.sp) } }
    @Composable private fun TelemetryRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color(0xFF71879B), fontSize = 11.sp); Text(value, color = Color(0xFFD6E2F0), fontSize = 11.sp) } }
    @Composable private fun HudButton(label: String, modifier: Modifier, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = modifier.height(44.dp).border(1.dp, Color(0xFF164B68), RoundedCornerShape(10.dp)), shape = RoundedCornerShape(10.dp)) { Text(label, color = Color(0xFF9FEFF2), fontSize = 11.sp) } }

    private fun loadVoices(){val prefs=getSharedPreferences("jarvis",MODE_PRIVATE);selectedVoice=prefs.getString("tts_voice","").orEmpty();voiceRate=prefs.getFloat("speech_rate",0.72f);voicePitch=prefs.getFloat("speech_pitch",0.64f);voiceLoader=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){voiceNames=voiceLoader?.voices?.filter{it.locale?.language==Locale.ENGLISH.language&&!it.isNetworkConnectionRequired}?.sortedBy{it.name}?.map{it.name}?.take(3).orEmpty()}}}
    private fun selectGender(gender:String){selectedVoice="";getSharedPreferences("jarvis",MODE_PRIVATE).edit().putString("voice_gender",gender).remove("tts_voice").apply();message="${gender.replaceFirstChar{it.uppercase()}} voice selected. Deactivate and reactivate JARVIS to apply."}
    private fun selectVoice(name:String){selectedVoice=name;getSharedPreferences("jarvis",MODE_PRIVATE).edit().putString("tts_voice",name).apply();message="Voice selected. Restart JARVIS voice to apply."}
    private fun saveVoiceTuning(){getSharedPreferences("jarvis",MODE_PRIVATE).edit().putFloat("speech_rate",voiceRate).putFloat("speech_pitch",voicePitch).apply()}

    private fun runQuickCommand(command: String) {
        val result = JarvisNaturalCommandRouter.execute(this, command) ?: JarvisCommandEngine.execute(this, command)
        message = result
        commandHistory.add("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}  •  $command  →  $result")
        if (commandHistory.size > 12) commandHistory.removeAt(0)
    }

    private fun enrollOwnerVoice() {
        if (!hasMicPermission() || enrollingVoice) { message="Microphone permission is required for enrollment."; return }
        if (active) { message="Deactivate JARVIS before enrolling your owner voice."; return }
        enrollingVoice=true; message="Owner voice enrollment started. Say JARVIS three times when prompted."
        Thread {
            val samples=mutableListOf<ShortArray>()
            repeat(3) { OwnerVoiceProfile.recordPhrase(2)?.let(samples::add); Thread.sleep(500) }
            val ok=OwnerVoiceProfile.enroll(this,samples)
            runOnUiThread { enrollingVoice=false;ownerVoiceEnrolled=ok;message=if(ok)"Owner voice enrolled locally." else "Enrollment failed. Try again in a quiet room." }
        }.start()
    }

    private fun emergencyShutdown() {
        try { startService(Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_STOP)) } catch (_: Throwable) {}
        getSharedPreferences("jarvis", MODE_PRIVATE).edit()
            .putBoolean("active", false).putBoolean("standby", false)
            .putBoolean("emergency_shutdown", true).apply()
        active = false
        message = "Emergency shutdown engaged. Voice service disabled."
        serviceStage = "EMERGENCY_SHUTDOWN"
    }

    private fun toggleVoice() {
        if (!active && getSharedPreferences("jarvis", MODE_PRIVATE).getBoolean("emergency_shutdown", false)) {
            val km=getSystemService(android.app.KeyguardManager::class.java)
            val auth=km.createConfirmDeviceCredentialIntent("JARVIS Security","Authenticate to clear emergency shutdown.")
            if(auth!=null){deviceAuthLauncher.launch(auth);message="Android authentication required to clear emergency shutdown."}else message="Set a secure Android screen lock before clearing emergency shutdown."
            return
        }
        if (!active && !hasMicPermission()) { requestPermissionsIfNeeded(); return }
        val activating = !active
        val action = if (activating) JarvisVoiceService.ACTION_START else JarvisVoiceService.ACTION_STOP
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(action)
        try {
            if (activating) {
                startService(intent)
            } else {
                startService(intent)
            }
            active = activating
            message = if (activating) "JARVIS is listening. Awaiting your command." else "JARVIS is offline."
            commandHistory.add("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}  •  VOICE LINK  →  ${if (activating) "ACTIVATED" else "DEACTIVATED"}")
        } catch (e: Exception) {
            active = false
            getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("active", false).apply()
            message = "JARVIS could not activate: ${e.javaClass.simpleName}"
            commandHistory.add("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}  •  VOICE LINK  →  FAILED (${e.javaClass.simpleName})")
        }
    }

    override fun onDestroy(){try{voiceLoader?.shutdown()}catch(_:Throwable){};voiceLoader=null;super.onDestroy()}

    private fun hasMicPermission(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        val prefs=getSharedPreferences("jarvis", MODE_PRIVATE)
        active = prefs.getBoolean("active", active)
        standbyMode=prefs.getBoolean("standby",false)
        serviceStage=prefs.getString("service_stage",if(active)"ACTIVE" else "IDLE").orEmpty()
        lastHeard=prefs.getString("last_heard","").orEmpty()
        lastCommand=prefs.getString("last_command","").orEmpty()
        lastResult=prefs.getString("last_result","").orEmpty()
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
