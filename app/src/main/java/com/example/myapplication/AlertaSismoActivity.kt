package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Pantalla de emergencia sin Maps SDK embebido (evita crash por falta de API key).
 * La ruta real se abre en Google Maps; aquí suena alarma tipo alerta sísmica.
 */
class AlertaSismoActivity : AppCompatActivity() {

    private var latDest: Double = 0.0
    private var lonDest: Double = 0.0
    private var latOrig: Double = 0.0
    private var lonOrig: Double = 0.0

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

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
            // Fallback si no hay app de Maps
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

            val patronVibracion = longArrayOf(0, 800, 200, 800, 200, 800, 200, 800)
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
            // Alarma del sistema (tono de alarma, stream ALARM — estilo alerta sísmica)
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            if (ringtone == null) {
                Toast.makeText(this, "No se pudo cargar tono de alarma", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                ringtone?.streamType = AudioManager.STREAM_ALARM
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Alarma no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detenerAlarmaYVibracion() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        detenerAlarmaYVibracion()
        super.onDestroy()
    }
}
