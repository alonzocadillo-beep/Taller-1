package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.sqrt

class ContextManager(private val context: Context) : SensorEventListener {

    companion object {
        const val ACTION_EVENTO_DETECTADO = "com.example.myapplication.EVENTO_DETECTADO"
        const val EXTRA_TIPO_EVENTO = "tipo_evento"
        const val EXTRA_ETIQUETA_EVENTO = "etiqueta_evento"
    }

    private var sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var acelerometro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var adaptationEngine = AdaptationEngine(context)
    private val classifier = EventClassifier()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var latActual: Double = 0.0
    private var lonActual: Double = 0.0
    @Volatile private var alertaEjecutada = false

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
        classifier.reset()
        publicarEvento(TipoEvento.NINGUNO)

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
        classifier.reset()
    }

    /**
     * Simulación determinista: fuerza EVENTO_SISMICO para demo de evacuación.
     * Permite repetir la simulación aunque ya hubo una alerta previa.
     */
    fun ejecutarSimulacion() {
        alertaEjecutada = false
        val tipo = classifier.forzarEventoSismico()
        publicarEvento(tipo)
        dispararAlerta("¡SIMULACIÓN DE SISMO! (EVENTO SÍSMICO)")
    }

    private fun publicarEvento(tipo: TipoEvento) {
        val intent = Intent(ACTION_EVENTO_DETECTADO).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TIPO_EVENTO, tipo.name)
            putExtra(EXTRA_ETIQUETA_EVENTO, tipo.etiqueta)
        }
        context.sendBroadcast(intent)
    }

    private fun dispararAlerta(mensaje: String) {
        if (alertaEjecutada) return

        val lat = latActual
        val lon = lonActual
        if (lat == 0.0 && lon == 0.0) {
            mainHandler.post {
                Toast.makeText(
                    context,
                    "Evento sísmico detectado, pero aún no hay GPS. Espera un momento e intenta de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // No marcar alertaEjecutada: permite reintentar cuando haya ubicación
            return
        }

        alertaEjecutada = true
        mainHandler.post {
            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
        }
        adaptationEngine.evaluarAdaptacion(true, lat, lon)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER || alertaEjecutada) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val aceleracion = sqrt((x * x + y * y + z * z).toDouble())
        val ahora = System.currentTimeMillis()

        val tipo = classifier.procesar(aceleracion, ahora) ?: return
        if (tipo == TipoEvento.NINGUNO) return

        publicarEvento(tipo)

        when (tipo) {
            TipoEvento.EVENTO_SISMICO -> {
                dispararAlerta("¡Evento sísmico detectado! Activando evacuación...")
            }
            TipoEvento.GOLPE,
            TipoEvento.MOVIMIENTO_BRUSCO_AISLADO -> {
                mainHandler.post {
                    Toast.makeText(
                        context,
                        "Detectado: ${tipo.etiqueta} (sin alerta)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            TipoEvento.NINGUNO -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
