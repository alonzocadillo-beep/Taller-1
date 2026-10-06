package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handler: tras elegir zona segura, monitorea GPS y publica SafeZoneReached
 * cuando el usuario está a menos de RADIO_M metros.
 */
class GeofenceHandler(context: Context) : DomainEventHandler {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)

    @Volatile private var activo = false
    @Volatile private var eventId: Long = 0L
    @Volatile private var zonaNombre: String = ""
    @Volatile private var latDest: Double = 0.0
    @Volatile private var lonDest: Double = 0.0
    @Volatile private var yaLlego = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!activo || yaLlego) return
            for (loc in result.locations) {
                val distM = distanciaMetros(loc.latitude, loc.longitude, latDest, lonDest)
                if (distM <= RADIO_M) {
                    yaLlego = true
                    detenerTracking()
                    AppEventBus.publish(
                        DomainEvent.SafeZoneReached(
                            eventId = eventId,
                            zonaNombre = zonaNombre,
                            lat = loc.latitude,
                            lon = loc.longitude
                        )
                    )
                    Log.i(TAG, "Zona segura alcanzada (${distM.toInt()} m)")
                    return
                }
            }
        }
    }

    fun register() {
        AppEventBus.subscribe(this)
    }

    override fun onEvent(event: DomainEvent) {
        when (event) {
            is DomainEvent.SafeZoneSelected -> {
                if (event.modoDegradado || (event.latDest == 0.0 && event.lonDest == 0.0)) {
                    detenerTracking()
                    return
                }
                eventId = event.eventId
                zonaNombre = event.zonaNombre
                latDest = event.latDest
                lonDest = event.lonDest
                yaLlego = false
                iniciarTracking()
            }
            is DomainEvent.FalseAlarmReported -> detenerTracking()
            else -> Unit
        }
    }

    private fun iniciarTracking() {
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Sin permiso GPS para geofence")
            return
        }
        detenerTracking()
        activo = true
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        try {
            fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException geofence", e)
            activo = false
        }
    }

    private fun detenerTracking() {
        activo = false
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        }
    }

    private fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "GeofenceHandler"
        private const val RADIO_M = 80.0
    }
}
