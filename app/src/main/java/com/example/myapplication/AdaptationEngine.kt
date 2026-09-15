package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.math.*

class AdaptationEngine(private val context: Context) {

    fun evaluarAdaptacion(sismoDetectado: Boolean, latActual: Double, lonActual: Double) {
        if (sismoDetectado && latActual != 0.0 && lonActual != 0.0) {
            val zonasSegurasLocales = listOf(
                Pair(latActual + 0.0015, lonActual + 0.0010),
                Pair(latActual - 0.0010, lonActual - 0.0012),
                Pair(latActual + 0.0008, lonActual - 0.0015)
            )

            val mejorZona = encontrarZonaMasCercana(latActual, lonActual, zonasSegurasLocales)
            mostrarAlertaInteractiva(mejorZona.first, mejorZona.second)
        }
    }

    private fun encontrarZonaMasCercana(
        lat: Double,
        lon: Double,
        candidatas: List<Pair<Double, Double>>
    ): Pair<Double, Double> {
        var zonaOptima = candidatas[0]
        var distanciaMinima = Double.MAX_VALUE

        for (zona in candidatas) {
            val d = calcularDistancia(lat, lon, zona.first, zona.second)
            if (d < distanciaMinima) {
                distanciaMinima = d
                zonaOptima = zona
            }
        }
        return zonaOptima
    }

    private fun calcularDistancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun mostrarAlertaInteractiva(latDestino: Double, lonDestino: Double) {
        val intent = Intent(context, AlertaSismoActivity::class.java).apply {
            putExtra("LATITUD", latDestino)
            putExtra("LONGITUD", lonDestino)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "sismo_emergency_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Canal de notificación de alta prioridad (Alarma)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas de Emergencia Sísmicas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Configuración de FullScreenIntent para ejecutar sobre la pantalla actual o de bloqueo
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡SISMO DETECTADO!")
            .setContentText("Toca para ver la ruta de evacuación")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)

        notificationManager.notify(999, builder.build())

        // Lanzamiento directo secundario
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}