package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class AdaptationEngine(private val context: Context) {

    private val safeZones = SafeZoneRepository()
    private val settings = AppSettings(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun evaluarAdaptacion(
        sismoDetectado: Boolean,
        latActual: Double,
        lonActual: Double,
        fuente: String = "local",
        detalle: String = "",
        eventId: Long = System.currentTimeMillis()
    ) {
        if (!sismoDetectado) return

        val sinGps = latActual == 0.0 && lonActual == 0.0
        if (sinGps) {
            publicarAlerta(
                latActual, lonActual,
                SafeZone("Ubicación pendiente de GPS", 0.0, 0.0, "sin_gps"),
                -1.0, fuente, detalle, true, eventId
            )
            return
        }

        // OSM/red en background para no bloquear UI / ANR
        executor.execute {
            val favorita = settings.favoriteSafeZone()
            val (zona, distKm) = try {
                safeZones.encontrarMejor(latActual, lonActual, favorita)
            } catch (_: Exception) {
                // Fallback inmediato si falla red
                SafeZone(
                    nombre = "Espacio abierto cercano (estimado)",
                    lat = latActual + 0.00075,
                    lon = lonActual + 0.00055,
                    tipo = "estimado_local"
                ) to SafeZoneRepository.distanciaKm(
                    latActual, lonActual,
                    latActual + 0.00075, lonActual + 0.00055
                )
            }
            mainHandler.post {
                publicarAlerta(
                    latActual, lonActual, zona, distKm,
                    fuente, detalle, false, eventId
                )
            }
        }
    }

    private fun publicarAlerta(
        latOri: Double,
        lonOri: Double,
        zona: SafeZone,
        distKm: Double,
        fuente: String,
        detalle: String,
        modoDegradado: Boolean,
        eventId: Long
    ) {
        lanzarActividadAlerta(
            latOri = latOri,
            lonOri = lonOri,
            latDest = zona.lat,
            lonDest = zona.lon,
            zonaNombre = zona.nombre,
            distanciaKm = distKm,
            fuente = fuente,
            detalle = detalle,
            modoDegradado = modoDegradado,
            eventId = eventId
        )
        mostrarNotificacionRespaldo(
            latOri = latOri,
            lonOri = lonOri,
            latDest = zona.lat,
            lonDest = zona.lon,
            zonaNombre = zona.nombre,
            fuente = fuente,
            modoDegradado = modoDegradado,
            eventId = eventId
        )
    }

    private fun lanzarActividadAlerta(
        latOri: Double,
        lonOri: Double,
        latDest: Double,
        lonDest: Double,
        zonaNombre: String,
        distanciaKm: Double,
        fuente: String,
        detalle: String,
        modoDegradado: Boolean,
        eventId: Long
    ) {
        val intent = Intent(context, AlertaSismoActivity::class.java).apply {
            putExtra("latOrigen", latOri)
            putExtra("lonOrigen", lonOri)
            putExtra("latDestino", latDest)
            putExtra("lonDestino", lonDest)
            putExtra("zonaNombre", zonaNombre)
            putExtra("distanciaKm", distanciaKm)
            putExtra("fuente", fuente)
            putExtra("detalle", detalle)
            putExtra("modoDegradado", modoDegradado)
            putExtra("eventId", eventId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mostrarNotificacionRespaldo(
        latOri: Double,
        lonOri: Double,
        latDest: Double,
        lonDest: Double,
        zonaNombre: String,
        fuente: String,
        modoDegradado: Boolean,
        eventId: Long
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sismo_emergency_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas de Emergencia Sísmicas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal prioritario de evacuación"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, AlertaSismoActivity::class.java).apply {
            putExtra("latOrigen", latOri)
            putExtra("lonOrigen", lonOri)
            putExtra("latDestino", latDest)
            putExtra("lonDestino", lonDest)
            putExtra("zonaNombre", zonaNombre)
            putExtra("fuente", fuente)
            putExtra("modoDegradado", modoDegradado)
            putExtra("eventId", eventId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titulo = if (fuente.contains("igp", ignoreCase = true)) {
            "¡ALERTA OFICIAL IGP!"
        } else {
            "¡SISMO DETECTADO!"
        }
        val texto = if (modoDegradado) {
            "Sin GPS preciso. Toca para ver instrucciones de evacuación."
        } else {
            "Zona segura: $zonaNombre. Toca para abrir la ruta."
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        notificationManager.notify(1001, builder.build())
    }
}
