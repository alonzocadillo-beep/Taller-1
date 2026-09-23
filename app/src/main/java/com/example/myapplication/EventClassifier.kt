package com.example.myapplication

/**
 * Criterio académico (taller):
 * - Umbral de pico: 13.0 m/s²
 * - Ventana: 2000 ms
 * - Refractario entre picos contados: 400 ms (reduce rebotes mecánicos de un golpe)
 * - GOLPE: 1 pico y duración sobre umbral < 250 ms
 * - MOVIMIENTO_BRUSCO_AISLADO: 2 picos, o 1 pico “ancho” (≥ 250 ms)
 * - EVENTO_SISMICO: ≥ 3 picos en la ventana (o simulación explícita)
 *
 * Solo EVENTO_SISMICO debe disparar evacuación.
 * Un pico = un cruce de umbral (flanco de subida), no cada sample sobre el umbral.
 *
 * Métodos sincronizados: SensorManager entrega onSensorChanged en hilo de sensor
 * y la simulación corre en el hilo principal.
 */
enum class TipoEvento(val etiqueta: String) {
    NINGUNO("—"),
    GOLPE("GOLPE"),
    MOVIMIENTO_BRUSCO_AISLADO("MOVIMIENTO BRUSCO AISLADO"),
    EVENTO_SISMICO("EVENTO SÍSMICO")
}

class EventClassifier(
    private val umbral: Double = 13.0,
    private val ventanaMs: Long = 2000L,
    private val refractarioMs: Long = 400L,
    private val duracionGolpeMaxMs: Long = 250L
) {
    private data class Pico(val timestampMs: Long, val duracionMs: Long)

    private val picos = mutableListOf<Pico>()
    private var ultimoPicoContadoMs: Long = 0L
    private var enPico: Boolean = false
    private var inicioPicoMs: Long = 0L
    /** True solo si el flanco de subida actual registró un pico en la lista. */
    private var picoActualRegistrado: Boolean = false

    @Synchronized
    fun reset() {
        picos.clear()
        ultimoPicoContadoMs = 0L
        enPico = false
        inicioPicoMs = 0L
        picoActualRegistrado = false
    }

    /**
     * Procesa una muestra. Devuelve clasificación cuando hay flanco
     * relevante; null si no hay cambio a mostrar.
     */
    @Synchronized
    fun procesar(aceleracion: Double, ahoraMs: Long): TipoEvento? {
        if (aceleracion > umbral) {
            if (!enPico) {
                enPico = true
                inicioPicoMs = ahoraMs
                picoActualRegistrado = false
                if (ahoraMs - ultimoPicoContadoMs >= refractarioMs) {
                    registrarPico(ahoraMs, duracionProvisional = 1L)
                    ultimoPicoContadoMs = ahoraMs
                    picoActualRegistrado = true
                    val tipo = clasificar(ahoraMs)
                    if (tipo == TipoEvento.EVENTO_SISMICO) return tipo
                }
            }
            return null
        }

        if (enPico) {
            val duracion = (ahoraMs - inicioPicoMs).coerceAtLeast(1L)
            enPico = false
            if (picoActualRegistrado && picos.isNotEmpty()) {
                val ultimo = picos.removeAt(picos.lastIndex)
                picos.add(Pico(ultimo.timestampMs, duracion))
                picoActualRegistrado = false
                return clasificar(ahoraMs)
            }
            picoActualRegistrado = false
            // Flanco de bajada de un rebote dentro del refractario: no reclasificar
            return null
        }

        return null
    }

    @Synchronized
    fun forzarEventoSismico(): TipoEvento {
        reset()
        return TipoEvento.EVENTO_SISMICO
    }

    private fun registrarPico(ahoraMs: Long, duracionProvisional: Long) {
        purgar(ahoraMs)
        picos.add(Pico(ahoraMs, duracionProvisional.coerceAtLeast(1L)))
    }

    private fun purgar(ahoraMs: Long) {
        picos.removeAll { ahoraMs - it.timestampMs > ventanaMs }
    }

    private fun clasificar(ahoraMs: Long): TipoEvento {
        purgar(ahoraMs)
        return when {
            picos.size >= 3 -> TipoEvento.EVENTO_SISMICO
            picos.size == 2 -> TipoEvento.MOVIMIENTO_BRUSCO_AISLADO
            picos.size == 1 -> {
                val d = picos[0].duracionMs
                if (d < duracionGolpeMaxMs) TipoEvento.GOLPE
                else TipoEvento.MOVIMIENTO_BRUSCO_AISLADO
            }
            else -> TipoEvento.NINGUNO
        }
    }
}
