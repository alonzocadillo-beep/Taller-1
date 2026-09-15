package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AlertaSismoActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Forzar el encendido de la pantalla saltando el bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Mantener la pantalla encendida mientras suene la alarma
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta_sismo)

        // 2. Disparar los actuadores físicos (Ruido y Vibración)
        iniciarAlarmaYVibracion()

        // 3. Configurar el botón de evacuación
        val latDestino = intent.getDoubleExtra("LATITUD", 0.0)
        val lonDestino = intent.getDoubleExtra("LONGITUD", 0.0)
        val btnAbrirRuta = findViewById<Button>(R.id.btnAbrirRuta)

        btnAbrirRuta.setOnClickListener {
            detenerAlarmaYVibracion() // Apagamos el ruido al interactuar

            val gmmIntentUri = Uri.parse("google.navigation:q=$latDestino,$lonDestino&mode=w")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            startActivity(mapIntent)
            finish()
        }
    }

    private fun iniciarAlarmaYVibracion() {
        // --- CONFIGURACIÓN DE VIBRACIÓN ---
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Patrón: [Espera 0ms, Vibra 500ms, Pausa 200ms, Vibra 500ms...]
        val patronVibracion = longArrayOf(0, 500, 200, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // El '0' al final indica que el patrón se repetirá infinitamente
            vibrator?.vibrate(VibrationEffect.createWaveform(patronVibracion, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(patronVibracion, 0)
        }

        // --- CONFIGURACIÓN DE SONIDO ---
        // Buscamos el sonido de alarma por defecto del teléfono
        var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (alarmUri == null) {
            // Si no tiene alarma configurada, usamos el de notificación
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)

        // Forzamos que el sistema lo trate como una "Alarma" para que suene fuerte
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        ringtone?.play()
    }

    private fun detenerAlarmaYVibracion() {
        vibrator?.cancel()
        ringtone?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Medida de seguridad: Si el usuario cierra la app minimizándola o
        // usando el botón de atrás, nos aseguramos de apagar el ruido.
        detenerAlarmaYVibracion()
    }
}