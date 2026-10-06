package com.example.myapplication

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cliente de reportes sísmicos del IGP (fuente oficial pública ArcGIS).
 * Narrativa académica: capa de datos oficiales (IGP) alineada a difusión INDECI/SASPe.
 */
data class IgpEarthquake(
    val objectId: Long,
    val magnitud: Double,
    val latitud: Double,
    val longitud: Double,
    val profundidadKm: Double?,
    val fechaLocal: String,
    val horaLocal: String,
    val referencia: String,
    val timestampMs: Long
) {
    val resumen: String
        get() = "M${"%.1f".format(magnitud)} · $referencia · $fechaLocal $horaLocal"
}

class IgpEarthquakeClient {

    fun fetchLatest(limit: Int = 5): List<IgpEarthquake> {
        val url = URL(
            "$BASE_URL/query?where=1%3D1&outFields=*" +
                "&returnGeometry=false&f=json" +
                "&orderByFields=objectid+DESC" +
                "&resultRecordCount=$limit"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return parseFeatures(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseFeatures(body: String): List<IgpEarthquake> {
        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: return emptyList()
        val out = mutableListOf<IgpEarthquake>()
        for (i in 0 until features.length()) {
            val attrs = features.getJSONObject(i).optJSONObject("attributes") ?: continue
            val mag = attrs.optDouble("magnitud", Double.NaN)
            val lat = attrs.optDouble("latitud", Double.NaN)
            val lon = attrs.optDouble("longitud", Double.NaN)
            if (mag.isNaN() || lat.isNaN() || lon.isNaN()) continue
            val fecha = attrs.optString("fecha_local", "")
            val hora = attrs.optString("hora_local", "")
            out.add(
                IgpEarthquake(
                    objectId = attrs.optLong("objectid", i.toLong()),
                    magnitud = mag,
                    latitud = lat,
                    longitud = lon,
                    profundidadKm = attrs.optDouble("profundidad").takeIf { !it.isNaN() },
                    fechaLocal = fecha,
                    horaLocal = hora,
                    referencia = attrs.optString("referencia", attrs.optString("epicentro", "IGP")),
                    timestampMs = parseLocalDateTime(fecha, hora)
                )
            )
        }
        return out
    }

    companion object {
        private const val BASE_URL =
            "https://ide.igp.gob.pe/arcgis/rest/services/monitoreocensis/UltimoSismo/MapServer/0"

        fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        private fun parseLocalDateTime(fecha: String, hora: String): Long {
            val candidates = listOf(
                "dd/MM/yyyy HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "dd-MM-yyyy HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss"
            )
            val raw = "${fecha.trim()} ${hora.trim()}".trim()
            for (pattern in candidates) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("America/Lima")
                        isLenient = true
                    }
                    return sdf.parse(raw)?.time ?: continue
                } catch (_: Exception) {
                }
            }
            return System.currentTimeMillis()
        }
    }
}
