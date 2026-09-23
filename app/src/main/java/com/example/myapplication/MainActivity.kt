package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var textoEstado: TextView
    private lateinit var textoEvento: TextView
    private lateinit var switchMonitoreo: SwitchMaterial
    private lateinit var btnSimular: MaterialButton

    private val eventoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ContextManager.ACTION_EVENTO_DETECTADO) return
            val etiqueta = intent.getStringExtra(ContextManager.EXTRA_ETIQUETA_EVENTO) ?: "—"
            val tipoNombre = intent.getStringExtra(ContextManager.EXTRA_TIPO_EVENTO)
            actualizarTextoEvento(etiqueta, tipoNombre)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textoEstado = findViewById(R.id.textoEstado)
        textoEvento = findViewById(R.id.textoEvento)
        switchMonitoreo = findViewById(R.id.switchMonitoreo)
        btnSimular = findViewById(R.id.btnSimular)

        solicitarPermisos()
        verificarPermisoSuperposicion()

        switchMonitoreo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                iniciarServicio()
            } else {
                detenerServicio()
            }
        }

        btnSimular.setOnClickListener {
            if (switchMonitoreo.isChecked) {
                val intent = Intent(this, SensorService::class.java).apply {
                    action = "ACTION_SIMULAR_SISMO"
                }
                startService(intent)
            } else {
                Toast.makeText(this, "Activa el monitoreo primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ContextManager.ACTION_EVENTO_DETECTADO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventoReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(eventoReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(eventoReceiver)
        } catch (_: IllegalArgumentException) {
            // Ya estaba desregistrado
        }
    }

    private fun actualizarTextoEvento(etiqueta: String, tipoNombre: String?) {
        textoEvento.text = "Evento: $etiqueta"
        textoEvento.setTextColor(
            when (tipoNombre) {
                TipoEvento.GOLPE.name -> Color.parseColor("#FFC107")
                TipoEvento.MOVIMIENTO_BRUSCO_AISLADO.name -> Color.parseColor("#FF9800")
                TipoEvento.EVENTO_SISMICO.name -> Color.parseColor("#FF5252")
                else -> Color.parseColor("#8A9BB4")
            }
        )
    }

    private fun solicitarPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), 100)
        }
    }

    private fun verificarPermisoSuperposicion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Permite 'Mostrar sobre otras apps' para las alertas de emergencia",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun iniciarServicio() {
        textoEstado.text = "Monitoreo ACTIVO"
        textoEstado.setTextColor(Color.parseColor("#00E676"))
        textoEvento.text = "Evento: —"
        textoEvento.setTextColor(Color.parseColor("#8A9BB4"))

        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "Servicio de monitoreo iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun detenerServicio() {
        textoEstado.text = "Sistema inactivo"
        textoEstado.setTextColor(Color.parseColor("#8A9BB4"))
        textoEvento.text = "Evento: —"
        textoEvento.setTextColor(Color.parseColor("#8A9BB4"))

        val intent = Intent(this, SensorService::class.java)
        stopService(intent)

        Toast.makeText(this, "Servicio detenido", Toast.LENGTH_SHORT).show()
    }
}
