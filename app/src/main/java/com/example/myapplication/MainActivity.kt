package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var sensorService: SensorService
    private lateinit var textoEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuración visual inicial
        textoEstado = findViewById(R.id.textoEstado) // Asegúrate de tener este ID en tu XML
        textoEstado.text = "Sistema en reposo"
        textoEstado.setTextColor(Color.GREEN)

        // Manejo de márgenes del sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Instanciar e iniciar el servicio de captura[cite: 1]
        sensorService = SensorService(this)
        sensorService.iniciarMonitoreo()

        // Hilo en segundo plano para revisar si el AdaptationEngine tomó una decisión
        Thread {
            while (true) {
                if (AdaptationEngine.sismoDetectado) {
                    runOnUiThread {
                        activarModoEvacuacion()
                    }
                    // Detener el bucle una vez que se activa el sismo para no sobresaturar la UI
                    break
                }
                Thread.sleep(500) // Revisa cada medio segundo
            }
        }.start()
    }

    // Modificar alguna funcionalidad o comportamiento del sistema de manera observable[cite: 1]
    private fun activarModoEvacuacion() {
        // 1. Cambiar el texto y el color de la interfaz
        textoEstado.text = "¡SISMO DETECTADO!\nRuta de evacuación activa"
        textoEstado.setTextColor(Color.RED)
        textoEstado.textSize = 28f

        // El fondo cambia a negro para resaltar
        findViewById<android.view.View>(R.id.main).setBackgroundColor(Color.BLACK)

        // 2. Modificación de hardware: Forzar el brillo de la pantalla al máximo
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

        // (Opcional) Aquí cargarías tu imagen de mapa de evacuación de la UNI
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos para evitar bloqueo innecesario[cite: 1]
        sensorService.detenerMonitoreo()
    }
}