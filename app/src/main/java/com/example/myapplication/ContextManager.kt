package com.example.myapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import kotlin.math.sqrt

class ContextManager(private val context: Context) : SensorEventListener {

    private var sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var acelerometro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var adaptationEngine = AdaptationEngine(context)

    private var latActual: Double = 0.0
    private var lonActual: Double = 0.0
    private var alertaEjecutada = false

    private val UMBRAL_SISMO = 18.0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                latActual = location.latitude
                lonActual = location.longitude
            }
        }
    }

    fun iniciarMonitoreo() {
        alertaEjecutada = false

        // Intentar obtener la última ubicación guardada en el GPS del teléfono al iniciar
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latActual = location.latitude
                    lonActual = location.longitude
                }
            }
        } catch (e: SecurityException) {
            Log.e("ContextManager", "Sin permisos de GPS")
        }

        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("ContextManager", "Faltan permisos de GPS")
        }
    }

    fun detenerMonitoreo() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val aceleracion = sqrt((x * x + y * y + z * z).toDouble())

            if (aceleracion > UMBRAL_SISMO && !alertaEjecutada) {
                alertaEjecutada = true

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "¡Sismo detectado! Calculando ruta óptima cercana...", Toast.LENGTH_SHORT).show()
                }

                // Evalúa la adaptación con la posición actual detectada
                adaptationEngine.evaluarAdaptacion(true, latActual, lonActual)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}