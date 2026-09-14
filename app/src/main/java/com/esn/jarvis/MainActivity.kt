package com.esn.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var listening by mutableStateOf(false)
    private val speechPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speechPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF061426)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("JARVIS", color = Color(0xFF7DEFF2), fontSize = 42.sp)
                        Text("ESN Mobile Assistant", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(32.dp))
                        Surface(
                            modifier = Modifier.size(190.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (listening) Color(0xFF168CFF) else Color(0xFF0A2038)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(if (listening) "LISTENING" else "STANDBY", color = Color.White, fontSize = 20.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("●", color = Color(0xFF42E8F4), fontSize = 48.sp)
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { listening = !listening }) {
                                Text(if (listening) "Stop" else "Talk")
                            }
                            TextButton(modifier = Modifier.weight(1f), onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) {
                                Text("Phone Access")
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Enable Phone Access to let JARVIS navigate supported Android interfaces. Sensitive actions will require confirmation.",
                            color = Color(0xFFB8C7D9),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
