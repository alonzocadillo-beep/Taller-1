package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Handler de eventos: envía SMS automático a la lista de contactos
 * cuando hay temblor/alerta y cuando se llega a zona segura.
 */
class SmsAutoHandler(context: Context) : DomainEventHandler {
    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)

    fun register() {
        AppEventBus.subscribe(this)
    }

    override fun onEvent(event: DomainEvent) {
        when (event) {
            is DomainEvent.SeismicAlertTriggered -> {
                val ubi = if (event.lat == 0.0 && event.lon == 0.0) {
                    "ubicación aún imprecisa"
                } else {
                    "https://maps.google.com/?q=${event.lat},${event.lon}"
                }
                val msg =
                    "⚠️ Alerta sísmica (${event.fuente}). Estoy evacuando hacia una zona segura. Mi ubicación: $ubi"
                enviarALista(msg, "alerta")
            }
            is DomainEvent.SafeZoneReached -> {
                val ubi = "https://maps.google.com/?q=${event.lat},${event.lon}"
                val msg =
                    "✅ Ya estoy en zona segura: ${event.zonaNombre}. Ubicación: $ubi"
                enviarALista(msg, "zona_segura")
            }
            is DomainEvent.FalseAlarmReported -> {
                // No envía; futuras alertas del mismo id se evitan en otros handlers
            }
            else -> Unit
        }
    }

    private fun enviarALista(mensaje: String, motivo: String) {
        if (!settings.autoSmsEnabled) {
            Log.i(TAG, "SMS auto desactivado ($motivo)")
            return
        }
        val contactos = settings.emergencyContacts
        if (contactos.isEmpty()) {
            Log.i(TAG, "Sin contactos para SMS auto ($motivo)")
            return
        }
        if (!tienePermisoSms()) {
            Toast.makeText(
                appContext,
                "Falta permiso SMS para avisar automáticamente",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        var enviados = 0
        for (c in contactos) {
            val phone = normalizarTelefono(c.phone)
            if (phone.isBlank()) continue
            try {
                smsManager().sendTextMessage(phone, null, mensaje, null, null)
                enviados++
            } catch (e: Exception) {
                Log.e(TAG, "Error SMS a ${c.name}: ${e.message}")
            }
        }
        if (enviados > 0) {
            Toast.makeText(
                appContext,
                "SMS automático enviado a $enviados contacto(s)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun tienePermisoSms(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun smsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun normalizarTelefono(raw: String): String =
        raw.filter { it.isDigit() || it == '+' }

    companion object {
        private const val TAG = "SmsAutoHandler"
    }
}
