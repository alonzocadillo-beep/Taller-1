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
    private lateinit var textoIgp: TextView
    private lateinit var textoPermisos: TextView
    private lateinit var switchMonitoreo: SwitchMaterial
    private lateinit var btnSimular: MaterialButton

    private val eventoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ContextManager.ACTION_EVENTO_DETECTADO -> {
                    val etiqueta = intent.getStringExtra(ContextManager.EXTRA_ETIQUETA_EVENTO) ?: "—"
                    val tipoNombre = intent.getStringExtra(ContextManager.EXTRA_TIPO_EVENTO)
                    actualizarTextoEvento(etiqueta, tipoNombre)
                    intent.getStringExtra(ContextManager.EXTRA_IGP_RESUMEN)?.let {
                        textoIgp.text = "IGP: $it"
                    }
                }
                ContextManager.ACTION_ESTADO_MONITOREO -> {
                    val detalle = intent.getStringExtra(ContextManager.EXTRA_DETALLE).orEmpty()
                    val estado = intent.getStringExtra(ContextManager.EXTRA_ESTADO).orEmpty()
                    if (detalle.isNotBlank() && switchMonitoreo.isChecked) {
                        if (estado.startsWith("igp") || estado == "activo" || estado == "degradado") {
                            if (estado.startsWith("igp")) {
                                textoIgp.text = "IGP: $detalle"
                            } else if (estado == "degradado") {
                                textoEstado.text = "Monitoreo DEGRADADO"
                                textoEstado.setTextColor(Color.parseColor("#FFC107"))
                                textoPermisos.text = detalle
                            }
                        }
                    }
                    intent.getStringExtra(ContextManager.EXTRA_IGP_RESUMEN)?.let {
                        if (it.isNotBlank()) textoIgp.text = "IGP: $it"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textoEstado = findViewById(R.id.textoEstado)
        textoEvento = findViewById(R.id.textoEvento)
        textoIgp = findViewById(R.id.textoIgp)
        textoPermisos = findViewById(R.id.textoPermisos)
        switchMonitoreo = findViewById(R.id.switchMonitoreo)
        btnSimular = findViewById(R.id.btnSimular)

        solicitarPermisos()
        verificarPermisoSuperposicion()
        actualizarEstadoPermisos()

        switchMonitoreo.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoPermisos()
            if (isChecked) {
                val perm = evaluarPermisosLocal()
                if (perm.contains("ubicación")) {
                    Toast.makeText(this, "Se necesita ubicación para monitoreo completo", Toast.LENGTH_LONG).show()
                }
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

        findViewById<MaterialButton>(R.id.btnAjustes).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRepararPermisos).setOnClickListener {
            solicitarPermisos()
            verificarPermisoSuperposicion()
            actualizarEstadoPermisos()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoPermisos()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ContextManager.ACTION_EVENTO_DETECTADO)
            addAction(ContextManager.ACTION_ESTADO_MONITOREO)
        }
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
        }
    }

    private fun actualizarTextoEvento(etiqueta: String, tipoNombre: String?) {
        textoEvento.text = "Evento: $etiqueta"
        textoEvento.setTextColor(
            when (tipoNombre) {
                TipoEvento.GOLPE.name -> Color.parseColor("#FFC107")
                TipoEvento.MOVIMIENTO_BRUSCO_AISLADO.name -> Color.parseColor("#FF9800")
                TipoEvento.EVENTO_SISMICO.name -> Color.parseColor("#FF5252")
                TipoEvento.ALERTA_OFICIAL_IGP.name -> Color.parseColor("#E040FB")
                else -> Color.parseColor("#8A9BB4")
            }
        )
    }

    private fun solicitarPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
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

    private fun evaluarPermisosLocal(): String {
        val loc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val faltantes = mutableListOf<String>()
        if (!loc) faltantes.add("ubicación")
        if (!notifOk) faltantes.add("notificaciones")
        if (!overlay) faltantes.add("superposición")
        return if (faltantes.isEmpty()) "Permisos OK" else "Falta: ${faltantes.joinToString(", ")}"
    }

    private fun actualizarEstadoPermisos() {
        val estado = evaluarPermisosLocal()
        textoPermisos.text = estado
        textoPermisos.setTextColor(
            if (estado == "Permisos OK") Color.parseColor("#00E676")
            else Color.parseColor("#FFC107")
        )
    }

    private fun iniciarServicio() {
        textoEstado.text = "Monitoreo ACTIVO"
        textoEstado.setTextColor(Color.parseColor("#00E676"))
        textoEvento.text = "Evento: —"
        textoEvento.setTextColor(Color.parseColor("#8A9BB4"))
        textoIgp.text = "IGP: consultando…"

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
        textoIgp.text = "IGP: —"
        stopService(Intent(this, SensorService::class.java))
        Toast.makeText(this, "Servicio detenido", Toast.LENGTH_SHORT).show()
    }
}
