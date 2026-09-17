package com.esn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class JarvisVoiceService : Service() {
    companion object {
        const val ACTION_START = "com.esn.jarvis.START"
        const val ACTION_STOP = "com.esn.jarvis.STOP"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
        private const val PREFS = "jarvis"
        private const val ACTIVE = "active"
    }

    override fun onCreate() {
        super.onCreate()
        checkpoint("SERVICE_CREATE")
        try {
            createChannel()
        } catch (t: Throwable) {
            checkpoint("CHANNEL_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkpoint("SERVICE_START")

        return when (intent?.action) {
            ACTION_STOP -> {
                deactivate()
                START_NOT_STICKY
            }
            ACTION_START -> {
                activateIsolation()
                START_NOT_STICKY
            }
            else -> {
                checkpoint("UNKNOWN_ACTION")
                stopSelf()
                START_NOT_STICKY
            }
        }
    }

    private fun activateIsolation() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkpoint("MIC_PERMISSION_MISSING")
            setActive(false)
            stopSelf()
            return
        }

        checkpoint("MIC_PERMISSION_OK")

        try {
            val notification = notification("JARVIS isolation test active")
            checkpoint("FGS_BEFORE")

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

            checkpoint("FGS_AFTER")
            setActive(true)
            checkpoint("ACTIVATION_COMPLETE")
        } catch (t: Throwable) {
            checkpoint("FGS_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")
            setActive(false)
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) { }
            stopSelf()
        }
    }

    private fun deactivate() {
        checkpoint("STOP_REQUESTED")
        setActive(false)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
            checkpoint("STOP_FG_EXCEPTION:${t.javaClass.simpleName}:${t.message.orEmpty()}")
        }
        checkpoint("STOP_COMPLETE")
        stopSelf()
    }

    private fun setActive(value: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(ACTIVE, value)
            .commit()
    }

    private fun checkpoint(stage: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString("service_stage", stage)
            .putLong("service_stage_time", System.currentTimeMillis())
            .commit()
    }

    private fun notification(text: String): Notification = if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        checkpoint("SERVICE_DESTROY")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
