package com.example.myapplication

/**
 * Eventos de dominio del sistema adaptativo.
 * Toda reacción (UI, SMS, historial, geofence) se engancha al bus, no a llamadas cruzadas.
 */
sealed class DomainEvent {
    data class MotionClassified(
        val tipo: TipoEvento,
        val detalle: String = ""
    ) : DomainEvent()

    data class SeismicAlertTriggered(
        val eventId: Long,
        val fuente: String,
        val mensaje: String,
        val detalle: String,
        val lat: Double,
        val lon: Double,
        val tipoHistorial: String
    ) : DomainEvent()

    data class SafeZoneSelected(
        val eventId: Long,
        val zonaNombre: String,
        val latDest: Double,
        val lonDest: Double,
        val latOri: Double,
        val lonOri: Double,
        val distanciaKm: Double,
        val fuente: String,
        val modoDegradado: Boolean
    ) : DomainEvent()

    data class SafeZoneReached(
        val eventId: Long,
        val zonaNombre: String,
        val lat: Double,
        val lon: Double
    ) : DomainEvent()

    data class FalseAlarmReported(
        val eventId: Long
    ) : DomainEvent()
}
