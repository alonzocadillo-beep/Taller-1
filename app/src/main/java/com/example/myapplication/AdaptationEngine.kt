package com.example.myapplication

class AdaptationEngine {

    // Configuración de sensibilidad
    private val UMBRAL_SISMICO = 3.5 // m/s² de movimiento real adicional
    private var lecturasSostenidas = 0
    private val LECTURAS_REQUERIDAS = 5 // Confirmación de vibración continua

    // Variable estática para conectar con la interfaz visual
    companion object {
        var sismoDetectado = false
    }

    fun evaluarSismo(movimientoReal: Double) {
        if (movimientoReal > UMBRAL_SISMICO) {
            lecturasSostenidas++

            // La decisión debe producirse automáticamente
            if (lecturasSostenidas >= LECTURAS_REQUERIDAS) {
                sismoDetectado = true
                lecturasSostenidas = 0
            }
        } else {
            // Si el celular deja de temblar, el contador desciende
            if (lecturasSostenidas > 0) {
                lecturasSostenidas--
            }
        }
    }
}