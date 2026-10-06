package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.sqrt

class ContextManager(private val context: Context) : SensorEventListener {

    companion object {
        const val ACTION_EVENTO_DETECTADO = "com.example.myapplication.EVENTO_DETECTADO"
        const val ACTION_ESTADO_MONITOREO = "com.example.myapplication.ESTADO_MONITOREO"
        const val EXTRA_TIPO_EVENTO = "tipo_evento"
        const val EXTRA_ETIQUETA_EVENTO = "etiqueta_evento"
        const val EXTRA_ESTADO = "estado"
        const val EXTRA_DETALLE = "detalle"
        const val EXTRA_IGP_RESUMEN = "igp_resumen"

        private const val COOLDOWN_MS = 3 * 60 * 1000L
        private const val GPS_PENDING_TIMEOUT_MS = 25_000L
        private const val IGP_POLL_MS = 60_000L
        private const val IGP_MAX_EDAD_MS = 12 * 60 * 1000L
        private const val TAG = "ContextManager"
    }

    private var sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensorActivo: Sensor? = null
    private var usandoLinear = false
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val classifier = EventClassifier()
    private val settings = AppSettings(context)
    private val history = EventHistoryStore(context)
    private val igpClient = IgpEarthquakeClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var networkThread: HandlerThread? = null
    private var networkHandler: Handler? = null

    private var latActual: Double = 0.0
    private var lonActual: Double = 0.0
    private var accuracyActual: Float = Float.MAX_VALUE

    @Volatile private var alertaEjecutada = false
    @Volatile private var alertaPendientePorGps = false
    @Volatile private var pendienteFuente: String = "local"
    @Volatile private var pendienteDetalle: String = ""
    @Volatile private var pendienteMensaje: String = ""
    @Volatile private var ultimoAlertaMs: Long = 0L
    @Volatile private var ultimoIgpObjectId: Long = -1L
    @Volatile private var ultimoIgpFingerprint: String = ""
    @Volatile private var ultimoIgpResumen: String = "Sin consulta aún"
    @Volatile private var pendienteEventId: Long = 0L
    @Volatile private var igpPrimeraConsultaHecha: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                latActual = location.latitude
                lonActual = location.longitude
                accuracyActual = location.accuracy
            }
            if (alertaPendientePorGps && tieneGpsUtil) {
                tryDispararPendiente()
            }
        }
    }

    private val gpsTimeoutRunnable = Runnable {
        if (alertaPendientePorGps && !alertaEjecutada) {
            // Modo degradado: alerta sin GPS preciso
            alertaPendientePorGps = false
            ejecutarAlerta(
                mensaje = pendienteMensaje.ifBlank { "Evento sísmico (sin GPS preciso)" },
                fuente = pendienteFuente,
                detalle = pendienteDetalle + " | modo_degradado_sin_gps",
                forzarSinGps = true,
                eventId = pendienteEventId
            )
        }
    }

    private val igpPollRunnable = object : Runnable {
        override fun run() {
            try {
                consultarIgpYFusionar()
            } catch (e: Exception) {
                Log.e(TAG, "Error IGP", e)
                ultimoIgpResumen = "Error de red IGP"
                publicarEstado("igp_error", ultimoIgpResumen)
            }
            networkHandler?.postDelayed(this, IGP_POLL_MS)
        }
    }

    private val tieneGpsUtil: Boolean
        get() = !(latActual == 0.0 && lonActual == 0.0) && accuracyActual <= 150f

    fun iniciarMonitoreo() {
        alertaEjecutada = false
        alertaPendientePorGps = false
        igpPrimeraConsultaHecha = false
        ultimoIgpFingerprint = ""
        val profile = settings.sensitivity

        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linear != null) {
            sensorActivo = linear
            usandoLinear = true
        } else {
            sensorActivo = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            usandoLinear = false
        }
        classifier.aplicarPerfil(profile, usandoLinear)

        publicarEvento(TipoEvento.NINGUNO)
        publicarEstado(
            "activo",
            "Monitoreo activo · ${profile.label}" +
                if (usandoLinear) " · accel. lineal" else " · accel. total"
        )

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latActual = location.latitude
                    lonActual = location.longitude
                    accuracyActual = location.accuracy
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permisos de GPS")
        }

        sensorActivo?.let {
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
            Log.e(TAG, "Faltan permisos de GPS")
            publicarEstado("degradado", "Sin permiso de ubicación")
        }

        if (settings.igpPollingEnabled) {
            startIgpPolling()
        }
    }

    fun detenerMonitoreo() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        classifier.reset()
        mainHandler.removeCallbacks(gpsTimeoutRunnable)
        stopIgpPolling()
        alertaPendientePorGps = false
        publicarEstado("inactivo", "Monitoreo detenido")
    }

    fun refrescarPerfilSiActivo() {
        val profile = settings.sensitivity
        classifier.aplicarPerfil(profile, usandoLinear)
    }

    fun ejecutarSimulacion() {
        if (enCooldown()) {
            toast("Cooldown activo: espera antes de otra alerta")
            return
        }
        alertaEjecutada = false
        val tipo = classifier.forzarEventoSismico()
        publicarEvento(tipo)
        solicitarAlerta(
            mensaje = "¡SIMULACIÓN DE SISMO! (EVENTO SÍSMICO)",
            fuente = "simulacion",
            detalle = "Simulación manual",
            tipoHistorial = tipo.name
        )
    }

    private fun startIgpPolling() {
        stopIgpPolling()
        networkThread = HandlerThread("igp-poll").also { it.start() }
        networkHandler = Handler(networkThread!!.looper)
        networkHandler?.post(igpPollRunnable)
    }

    private fun stopIgpPolling() {
        networkHandler?.removeCallbacks(igpPollRunnable)
        networkThread?.quitSafely()
        networkHandler = null
        networkThread = null
    }

    private fun consultarIgpYFusionar() {
        val eventos = igpClient.fetchLatest(5)
        if (eventos.isEmpty()) {
            ultimoIgpResumen = "IGP sin datos"
            publicarEstado("igp_vacio", ultimoIgpResumen)
            return
        }
        val latest = eventos.first()
        ultimoIgpResumen = latest.resumen
        publicarEstado("igp_ok", ultimoIgpResumen)

        // UltimoSismo suele actualizar el mismo objectid: fingerprint por fecha/hora/mag/coords
        val fingerprint =
            "${latest.fechaLocal}|${latest.horaLocal}|${latest.magnitud}|${latest.latitud}|${latest.longitud}"

        if (!igpPrimeraConsultaHecha) {
            igpPrimeraConsultaHecha = true
            ultimoIgpFingerprint = fingerprint
            ultimoIgpObjectId = latest.objectId
            return
        }
        if (fingerprint == ultimoIgpFingerprint) return
        ultimoIgpFingerprint = fingerprint
        ultimoIgpObjectId = latest.objectId

        val profile = settings.sensitivity
        val ahora = System.currentTimeMillis()
        val edad = ahora - latest.timestampMs
        if (edad > IGP_MAX_EDAD_MS) return
        if (latest.magnitud < profile.magnitudOficialMin) return

        val hayGps = !(latActual == 0.0 && lonActual == 0.0)
        if (hayGps) {
            val dist = IgpEarthquakeClient.distanciaKm(
                latActual, lonActual, latest.latitud, latest.longitud
            )
            if (dist > profile.radioOficialKm) return
        }

        if (enCooldown() || alertaEjecutada) return

        mainHandler.post {
            publicarEvento(TipoEvento.ALERTA_OFICIAL_IGP)
            solicitarAlerta(
                mensaje = "¡Alerta oficial IGP! ${latest.resumen}",
                fuente = "igp",
                detalle = latest.resumen,
                tipoHistorial = TipoEvento.ALERTA_OFICIAL_IGP.name
            )
        }
    }

    private fun publicarEvento(tipo: TipoEvento) {
        val intent = Intent(ACTION_EVENTO_DETECTADO).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TIPO_EVENTO, tipo.name)
            putExtra(EXTRA_ETIQUETA_EVENTO, tipo.etiqueta)
            putExtra(EXTRA_IGP_RESUMEN, ultimoIgpResumen)
        }
        context.sendBroadcast(intent)
    }

    private fun publicarEstado(estado: String, detalle: String) {
        val intent = Intent(ACTION_ESTADO_MONITOREO).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ESTADO, estado)
            putExtra(EXTRA_DETALLE, detalle)
            putExtra(EXTRA_IGP_RESUMEN, ultimoIgpResumen)
        }
        context.sendBroadcast(intent)
    }

    private fun solicitarAlerta(
        mensaje: String,
        fuente: String,
        detalle: String,
        tipoHistorial: String
    ) {
        if (alertaEjecutada) return
        if (enCooldown()) return

        val eventId = System.currentTimeMillis()
        history.add(
            EventRecord(
                id = eventId,
                timestampMs = eventId,
                tipo = tipoHistorial,
                fuente = fuente,
                detalle = detalle,
                lat = latActual.takeIf { it != 0.0 },
                lon = lonActual.takeIf { it != 0.0 },
                disparoAlerta = true
            )
        )

        if (!tieneGpsUtil) {
            alertaPendientePorGps = true
            pendienteFuente = fuente
            pendienteDetalle = detalle
            pendienteMensaje = mensaje
            pendienteEventId = eventId
            toast("Evento detectado. Esperando GPS preciso…")
            mainHandler.removeCallbacks(gpsTimeoutRunnable)
            mainHandler.postDelayed(gpsTimeoutRunnable, GPS_PENDING_TIMEOUT_MS)
            return
        }

        ejecutarAlerta(mensaje, fuente, detalle, forzarSinGps = false, eventId = eventId)
    }

    private fun tryDispararPendiente() {
        if (!alertaPendientePorGps || alertaEjecutada) return
        mainHandler.removeCallbacks(gpsTimeoutRunnable)
        alertaPendientePorGps = false
        ejecutarAlerta(
            mensaje = pendienteMensaje,
            fuente = pendienteFuente,
            detalle = pendienteDetalle,
            forzarSinGps = false,
            eventId = pendienteEventId
        )
    }

    private fun ejecutarAlerta(
        mensaje: String,
        fuente: String,
        detalle: String,
        forzarSinGps: Boolean,
        eventId: Long = System.currentTimeMillis()
    ) {
        if (alertaEjecutada) return
        alertaEjecutada = true
        ultimoAlertaMs = System.currentTimeMillis()
        alertaPendientePorGps = false

        val lat = if (forzarSinGps && !tieneGpsUtil) 0.0 else latActual
        val lon = if (forzarSinGps && !tieneGpsUtil) 0.0 else lonActual

        mainHandler.post {
            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
        }
        // Arquitectura por eventos: no llamar UI/SMS directo; publicar al bus
        AppEventBus.publish(
            DomainEvent.SeismicAlertTriggered(
                eventId = eventId,
                fuente = fuente,
                mensaje = mensaje,
                detalle = detalle,
                lat = lat,
                lon = lon,
                tipoHistorial = if (fuente == "igp") {
                    TipoEvento.ALERTA_OFICIAL_IGP.name
                } else {
                    TipoEvento.EVENTO_SISMICO.name
                }
            )
        )
    }

    private fun enCooldown(): Boolean {
        val elapsed = System.currentTimeMillis() - ultimoAlertaMs
        return ultimoAlertaMs > 0 && elapsed < COOLDOWN_MS
    }

    private fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun evaluarPermisos(): String {
        val loc = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notifOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val overlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        return when {
            !loc -> "degradado: falta ubicación"
            !notifOk -> "degradado: faltan notificaciones"
            !overlay -> "degradado: falta superposición"
            else -> "ok"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || alertaEjecutada || alertaPendientePorGps) return
        val type = event.sensor?.type ?: return
        if (type != Sensor.TYPE_ACCELEROMETER && type != Sensor.TYPE_LINEAR_ACCELERATION) return

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
                if (enCooldown()) {
                    toast("Evento sísmico en cooldown (sin re-alerta)")
                    return
                }
                solicitarAlerta(
                    mensaje = "¡Evento sísmico detectado! Activando evacuación...",
                    fuente = "sensor_local",
                    detalle = "accel=${"%.2f".format(aceleracion)}",
                    tipoHistorial = tipo.name
                )
            }
            TipoEvento.GOLPE,
            TipoEvento.MOVIMIENTO_BRUSCO_AISLADO -> {
                history.add(
                    EventRecord(
                        id = ahora,
                        timestampMs = ahora,
                        tipo = tipo.name,
                        fuente = "sensor_local",
                        detalle = "sin alerta",
                        lat = null,
                        lon = null,
                        disparoAlerta = false
                    )
                )
                toast("Detectado: ${tipo.etiqueta} (sin alerta)")
            }
            else -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
