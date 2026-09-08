package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alerta_sismo_opcion"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas Interactivas de Evacuación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal prioritario con opción de evacuación"
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent que abre Google Maps con la ruta de evacuación
        val gmmIntentUri = Uri.parse("google.navigation:q=$latDestino,$lonDestino&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construcción de la notificación flotante con botón interactivo
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡SISMO DETECTADO!")
            .setContentText("¿Deseas ver la ruta de evacuación más cercana?")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Al tocar el cuerpo de la notificación abre el mapa
            .setFullScreenIntent(pendingIntent, true) // Muestra el banner emergente arriba de la pantalla
            .addAction(
                android.R.drawable.ic_menu_directions,
                "VER RUTA ÓPTIMA",
                pendingIntent
            ) // Botón explícito de acción

        notificationManager.notify(1001, builder.build())
    }
}