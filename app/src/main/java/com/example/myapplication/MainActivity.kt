package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchMonitoreo: SwitchMaterial
    private lateinit var textoEstado: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            iniciarServicio()
        } else {
            Toast.makeText(this, "Permisos necesarios denegados", Toast.LENGTH_SHORT).show()
            switchMonitoreo.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchMonitoreo = findViewById(R.id.switchMonitoreo)
        textoEstado = findViewById(R.id.textoEstado)

        switchMonitoreo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                verificarPermisos()
            } else {
                detenerServicio()
            }
        }
    }

    private fun verificarPermisos() {
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

        if (faltantes.isEmpty()) {
            iniciarServicio()
        } else {
            requestPermissionLauncher.launch(faltantes.toTypedArray())
        }
    }

    private fun iniciarServicio() {
        textoEstado.text = "Monitoreo ACTIVO en 2do plano"
        textoEstado.setTextColor(android.graphics.Color.parseColor("#4CAF50"))

        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun detenerServicio() {
        textoEstado.text = "Sistema inactivo"
        textoEstado.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))

        val intent = Intent(this, SensorService::class.java)
        stopService(intent)
    }
}