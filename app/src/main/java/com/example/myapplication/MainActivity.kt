package com.example.myapplication

import android.Manifest
import android.content.Intent
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
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var textoEstado: TextView
    private lateinit var switchMonitoreo: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textoEstado = findViewById(R.id.textoEstado)
        switchMonitoreo = findViewById(R.id.switchMonitoreo)

        solicitarPermisos()
        verificarPermisoSuperposicion()

        // Escuchador de cambios en el switch de Material Design
        switchMonitoreo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                iniciarServicio()
            } else {
                detenerServicio()
            }
        }
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

        val intent = Intent(this, SensorService::class.java)
        stopService(intent)

        Toast.makeText(this, "Servicio detenido", Toast.LENGTH_SHORT).show()
    }
}