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
 * Pantalla de emergencia: alarma con beep/zumbido repetido (no tono de alarma del sistema).
 */
class AlertaSismoActivity : AppCompatActivity() {

    private var latDest: Double = 0.0
    private var lonDest: Double = 0.0
    private var latOrig: Double = 0.0
    private var lonOrig: Double = 0.0

    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private val beepHandler = Handler(Looper.getMainLooper())
    private var beepActivo = false

    /** Beep corto + pausa = efecto de zumbido de alerta. */
    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!beepActivo) return
            try {
                // Tono tipo sirena/alerta corta (beep insistente)
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

        findViewById<TextView>(R.id.textoOrigen).text =
            "Tu ubicación: ${fmt(latOrig)}, ${fmt(lonOrig)}"
        findViewById<TextView>(R.id.textoDestino).text =
            "Zona segura: ${fmt(latDest)}, ${fmt(lonDest)}"

        iniciarAlarmaYVibracion()

        findViewById<MaterialButton>(R.id.btnAbrirRuta).setOnClickListener {
            abrirGoogleMapsNavegacion()
        }

        findViewById<MaterialButton>(R.id.btnCerrar).setOnClickListener {
            detenerAlarmaYVibracion()
            finish()
        }
    }

    private fun fmt(value: Double): String = String.format("%.5f", value)

    private fun abrirGoogleMapsNavegacion() {
        detenerAlarmaYVibracion()
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
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latDest,$lonDest&travelmode=walking")
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
            // Volumen al máximo del stream de alarma (0–100 en ToneGenerator)
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            beepActivo = true
            beepHandler.post(beepRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "No se pudo iniciar el beep de alerta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detenerAlarmaYVibracion() {
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
