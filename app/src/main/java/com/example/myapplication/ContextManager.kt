package com.example.myapplication

import kotlin.math.abs
import kotlin.math.sqrt

class ContextManager {
    // Conexión con la fase de Decisión del pipeline adaptativo[cite: 1]
    private val adaptationEngine = AdaptationEngine()

    fun procesarVibracion(x: Float, y: Float, z: Float) {
        // Calcular el vector de aceleración total usando los 3 ejes
        val aceleracionTotal = sqrt((x * x + y * y + z * z).toDouble())

        // Aislar la fuerza de gravedad terrestre (~9.8 m/s²) para obtener solo el movimiento físico extra
        val movimientoReal = abs(aceleracionTotal - 9.8)

        // Enviar el dato filtrado para tomar una decisión en función del contexto detectado[cite: 1]
        adaptationEngine.evaluarSismo(movimientoReal)
    }
}