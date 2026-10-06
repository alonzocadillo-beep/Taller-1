package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Pantalla de emergencia comercial:
 * - Silenciar sin cerrar (UC13)
 * - Compartir / avisar contacto (UC16)
 * - Marcar falsa alarma (UC14)
 * - Modo degradado sin GPS
 */
class AlertaSismoActivity : AppCompatActivity() {

    private var latDest: Double = 0.0
    private var lonDest: Double = 0.0
    private var latOrig: Double = 0.0
    private var lonOrig: Double = 0.0
    private var zonaNombre: String = "Zona segura"
    private var fuente: String = "local"
    private var detalle: String = ""
    private var modoDegradado: Boolean = false
    private var eventId: Long = 0L
    private var distanciaKm: Double = -1.0

    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private val beepHandler = Handler(Looper.getMainLooper())
    private var beepActivo = false
    private var silenciado = false

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!beepActivo || silenciado) return
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
            } catch (_: Exception) {
            }
            beepHandler.postDelayed(this, 450)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta_sismo)

        latDest = intent.getDoubleExtra("latDestino", 0.0)
        lonDest = intent.getDoubleExtra("lonDestino", 0.0)
        latOrig = intent.getDoubleExtra("latOrigen", 0.0)
        lonOrig = intent.getDoubleExtra("lonOrigen", 0.0)
        zonaNombre = intent.getStringExtra("zonaNombre") ?: "Zona segura"
        fuente = intent.getStringExtra("fuente") ?: "local"
        detalle = intent.getStringExtra("detalle") ?: ""
        modoDegradado = intent.getBooleanExtra("modoDegradado", false)
        eventId = intent.getLongExtra("eventId", System.currentTimeMillis())
        distanciaKm = intent.getDoubleExtra("distanciaKm", -1.0)

        val textoFuente = findViewById<TextView>(R.id.textoFuenteAlerta)
        textoFuente.text = when {
            fuente.contains("igp", ignoreCase = true) ->
                "Fuente: reporte oficial IGP"
            fuente.contains("simul", ignoreCase = true) -> "Fuente: simulación"
            else -> "Fuente: sensor local del dispositivo"
        }

        findViewById<TextView>(R.id.textoOrigen).text =
            if (modoDegradado || (latOrig == 0.0 && lonOrig == 0.0)) {
                "Tu ubicación: pendiente / imprecisa"
            } else {
                "Tu ubicación: ${fmt(latOrig)}, ${fmt(lonOrig)}"
            }

        val distTxt = if (distanciaKm >= 0) " (~${"%.1f".format(distanciaKm)} km)" else ""
        findViewById<TextView>(R.id.textoDestino).text =
            if (latDest == 0.0 && lonDest == 0.0) {
                "Zona segura: dirígete a un espacio abierto cercano"
            } else {
                "Zona segura: $zonaNombre$distTxt\n${fmt(latDest)}, ${fmt(lonDest)}"
            }

        if (detalle.isNotBlank()) {
            findViewById<TextView>(R.id.textoDetalleAlerta).text = detalle
        }

        iniciarAlarmaYVibracion()

        findViewById<MaterialButton>(R.id.btnAbrirRuta).setOnClickListener {
            abrirGoogleMapsNavegacion()
        }
        findViewById<MaterialButton>(R.id.btnSilenciar).setOnClickListener {
            silenciarAlarma()
            Toast.makeText(this, "Alarma silenciada (pantalla activa)", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnCompartir).setOnClickListener {
            compartirOAvisarContacto()
        }
        findViewById<MaterialButton>(R.id.btnFalsaAlarma).setOnClickListener {
            EventHistoryStore(this).markFalseAlarm(eventId)
            AppEventBus.publish(DomainEvent.FalseAlarmReported(eventId))
            detenerAlarmaYVibracion()
            Toast.makeText(this, "Marcado como falsa alarma", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<MaterialButton>(R.id.btnCerrar).setOnClickListener {
            detenerAlarmaYVibracion()
            finish()
        }
    }

    private fun fmt(value: Double): String = String.format("%.5f", value)

    private fun silenciarAlarma() {
        silenciado = true
        beepActivo = false
        beepHandler.removeCallbacks(beepRunnable)
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun compartirOAvisarContacto() {
        val settings = AppSettings(this)
        val zonaTxt = if (latDest == 0.0 && lonDest == 0.0) {
            "espacio abierto cercano"
        } else {
            "$zonaNombre ($latDest,$lonDest)"
        }
        val ubiTxt = if (latOrig == 0.0 && lonOrig == 0.0) {
            "ubicación aún imprecisa"
        } else {
            "https://maps.google.com/?q=$latOrig,$lonOrig"
        }
        val mensaje =
            "⚠️ Alerta sísmica ($fuente). Voy hacia zona segura: $zonaTxt. Mi ubicación: $ubiTxt"

        val phone = settings.emergencyContacts.firstOrNull()?.phone
            ?: settings.emergencyContactPhone
        if (!phone.isNullOrBlank()) {
            try {
                val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                    putExtra("sms_body", mensaje)
                }
                startActivity(sms)
                return
            } catch (_: Exception) {
            }
        }

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            putExtra(Intent.EXTRA_SUBJECT, "Alerta sísmica")
        }
        startActivity(Intent.createChooser(share, "Avisar contacto"))
    }

    private fun abrirGoogleMapsNavegacion() {
        detenerAlarmaYVibracion()
        if (latDest == 0.0 && lonDest == 0.0) {
            Toast.makeText(
                this,
                "Sin destino GPS. Busca un espacio abierto cercano.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val gmmIntentUri = Uri.parse("google.navigation:q=$latDest,$lonDest&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(mapIntent)
        } catch (_: Exception) {
            val browser = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination=$latDest,$lonDest&travelmode=walking"
                )
            )
            startActivity(browser)
        }
        finish()
    }

    private fun iniciarAlarmaYVibracion() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val patronVibracion = longArrayOf(0, 400, 150, 400, 150, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(patronVibracion, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(patronVibracion, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            beepActivo = true
            silenciado = false
            beepHandler.post(beepRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "No se pudo iniciar el beep de alerta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detenerAlarmaYVibracion() {
        silenciado = true
        beepActivo = false
        beepHandler.removeCallbacks(beepRunnable)
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        detenerAlarmaYVibracion()
        super.onDestroy()
    }
}
