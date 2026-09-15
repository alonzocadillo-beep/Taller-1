package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SensorService : Service() {

    private lateinit var contextManager: ContextManager

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()

        val notificacion = NotificationCompat.Builder(this, "canal_sismo")
            .setContentTitle("Monitor Sísmico Activo")
            .setContentText("Detectando vibraciones y ubicación...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notificacion)

        contextManager = ContextManager(this)
        contextManager.iniciarMonitoreo()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SIMULAR_SISMO") {
            if (::contextManager.isInitialized) {
                contextManager.ejecutarSimulacion()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::contextManager.isInitialized) {
            contextManager.detenerMonitoreo()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "canal_sismo",
                "Monitoreo de Sismos",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }
}