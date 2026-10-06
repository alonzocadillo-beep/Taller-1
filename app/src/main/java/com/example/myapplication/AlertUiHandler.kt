package com.example.myapplication

import android.content.Context

/**
 * Handler de eventos: ante alerta sísmica, lanza el flujo de evacuación (UI + zona segura).
 */
class AlertUiHandler(context: Context) : DomainEventHandler {
    private val appContext = context.applicationContext
    private val engine = AdaptationEngine(appContext)

    fun register() {
        AppEventBus.subscribe(this)
    }

    override fun onEvent(event: DomainEvent) {
        when (event) {
            is DomainEvent.SeismicAlertTriggered -> {
                engine.evaluarAdaptacion(
                    sismoDetectado = true,
                    latActual = event.lat,
                    lonActual = event.lon,
                    fuente = event.fuente,
                    detalle = event.detalle,
                    eventId = event.eventId
                )
            }
            else -> Unit
        }
    }
}
