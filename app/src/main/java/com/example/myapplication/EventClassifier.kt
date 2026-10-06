package com.example.myapplication

/**
 * Criterio académico (taller) + perfiles comerciales:
 * - Umbral / ventana / refractario / min picos vienen de [SensitivityProfile]
 * - GOLPE: 1 pico y duración sobre umbral < 250 ms
 * - MOVIMIENTO_BRUSCO_AISLADO: 2 picos, o 1 pico “ancho” (≥ 250 ms)
 * - EVENTO_SISMICO: ≥ minPicosSismo en la ventana (o simulación / alerta IGP)
 *
 * Solo EVENTO_SISMICO (local o oficial) debe disparar evacuación.
 */
enum class TipoEvento(val etiqueta: String) {
    NINGUNO("—"),
    GOLPE("GOLPE"),
    MOVIMIENTO_BRUSCO_AISLADO("MOVIMIENTO BRUSCO AISLADO"),
    EVENTO_SISMICO("EVENTO SÍSMICO"),
    ALERTA_OFICIAL_IGP("ALERTA OFICIAL IGP/INDECI")
}

class EventClassifier(
    private var umbral: Double = 13.0,
    private var ventanaMs: Long = 2000L,
    private var refractarioMs: Long = 400L,
    private var minPicosSismo: Int = 3,
    private val duracionGolpeMaxMs: Long = 250L
) {
    private data class Pico(val timestampMs: Long, val duracionMs: Long)

    private val picos = mutableListOf<Pico>()
    private var ultimoPicoContadoMs: Long = 0L
    private var enPico: Boolean = false
    private var inicioPicoMs: Long = 0L
    private var picoActualRegistrado: Boolean = false

    @Synchronized
    fun aplicarPerfil(profile: SensitivityProfile, usarLinear: Boolean) {
        umbral = if (usarLinear) profile.umbralLinear else profile.umbralAccel
        ventanaMs = profile.ventanaMs
        refractarioMs = profile.refractarioMs
        minPicosSismo = profile.minPicosSismo
        reset()
    }

    @Synchronized
    fun reset() {
        picos.clear()
        ultimoPicoContadoMs = 0L
        enPico = false
        inicioPicoMs = 0L
        picoActualRegistrado = false
    }

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
            picos.size >= minPicosSismo -> TipoEvento.EVENTO_SISMICO
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
