package com.example.myapplication

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SafeZone(
    val nombre: String,
    val lat: Double,
    val lon: Double,
    val tipo: String = "espacio_abierto"
)

/**
 * Criterio de evacuación (prioridad):
 * 1. Zona favorita del usuario (si está a ≤ 1.5 km)
 * 2. Parques/plazas reales cerca vía OpenStreetMap (≤ 1.2 km)
 * 3. Catálogo embebido solo si está a ≤ 500 m
 * 4. Estimado local (~100 m) si no hay nada útil cerca
 *
 * Evita mandarte a un parque famoso lejano cuando hay uno vecinal cerca.
 */
class SafeZoneRepository {

    fun encontrarMejor(
        lat: Double,
        lon: Double,
        favorita: SafeZone? = null
    ): Pair<SafeZone, Double> {
        val candidatos = mutableListOf<ScoredZone>()

        if (favorita != null && favorita.lat != 0.0 && favorita.lon != 0.0) {
            val d = distanciaKm(lat, lon, favorita.lat, favorita.lon)
            if (d <= RADIO_FAVORITA_KM) {
                candidatos += ScoredZone(favorita, d, prioridad = 0)
            }
        }

        val osm = consultarOsmCercanos(lat, lon, RADIO_OSM_M)
        for (zona in osm) {
            val d = distanciaKm(lat, lon, zona.lat, zona.lon)
            if (d <= RADIO_OSM_KM) {
                val prio = when (zona.tipo) {
                    "park", "square", "recreation_ground" -> 1
                    "garden" -> 2
                    else -> 3
                }
                candidatos += ScoredZone(zona, d, prioridad = prio)
            }
        }

        for (zona in ZONAS) {
            val d = distanciaKm(lat, lon, zona.lat, zona.lon)
            if (d <= RADIO_CATALOGO_KM) {
                candidatos += ScoredZone(zona.copy(tipo = "catalogo"), d, prioridad = 4)
            }
        }

        if (candidatos.isNotEmpty()) {
            val mejor = candidatos.minWith(
                compareBy<ScoredZone> { it.distKm }
                    .thenBy { it.prioridad }
            )
            return mejor.zona to mejor.distKm
        }

        // Nada cerca: estimado local corto (~100 m NE)
        val local = SafeZone(
            nombre = "Espacio abierto cercano (estimado)",
            lat = lat + 0.00075,
            lon = lon + 0.00055,
            tipo = "estimado_local"
        )
        return local to distanciaKm(lat, lon, local.lat, local.lon)
    }

    private fun consultarOsmCercanos(lat: Double, lon: Double, radioM: Int): List<SafeZone> {
        val query = """
            [out:json][timeout:8];
            (
              node["leisure"="park"](around:$radioM,$lat,$lon);
              way["leisure"="park"](around:$radioM,$lat,$lon);
              relation["leisure"="park"](around:$radioM,$lat,$lon);
              node["place"="square"](around:$radioM,$lat,$lon);
              way["place"="square"](around:$radioM,$lat,$lon);
              way["leisure"="garden"](around:$radioM,$lat,$lon);
              way["landuse"="recreation_ground"](around:$radioM,$lat,$lon);
              node["leisure"="pitch"](around:${radioM.coerceAtMost(800)},$lat,$lon);
              way["leisure"="pitch"](around:${radioM.coerceAtMost(800)},$lat,$lon);
            );
            out center 25;
        """.trimIndent()

        return try {
            val url = URL(OVERPASS_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 9_000
                readTimeout = 9_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                OutputStreamWriter(conn.outputStream).use { w ->
                    w.write("data=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    w.flush()
                }
                if (conn.responseCode !in 200..299) return emptyList()
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                parseOverpass(body)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseOverpass(body: String): List<SafeZone> {
        val root = JSONObject(body)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val out = mutableListOf<SafeZone>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            val nombre = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("name:es")?.takeIf { it.isNotBlank() }
                ?: defaultName(tags)

            val tipo = when {
                tags?.optString("leisure") == "park" -> "park"
                tags?.optString("place") == "square" -> "square"
                tags?.optString("landuse") == "recreation_ground" -> "recreation_ground"
                tags?.optString("leisure") == "garden" -> "garden"
                tags?.optString("leisure") == "pitch" -> "pitch"
                else -> "espacio_abierto"
            }

            val (zLat, zLon) = when {
                el.has("lat") && el.has("lon") ->
                    el.getDouble("lat") to el.getDouble("lon")
                el.has("center") -> {
                    val c = el.getJSONObject("center")
                    c.getDouble("lat") to c.getDouble("lon")
                }
                else -> continue
            }
            out += SafeZone(nombre = nombre, lat = zLat, lon = zLon, tipo = tipo)
        }
        return out
    }

    private fun defaultName(tags: JSONObject?): String {
        return when {
            tags?.optString("leisure") == "park" -> "Parque cercano"
            tags?.optString("place") == "square" -> "Plaza cercana"
            tags?.optString("landuse") == "recreation_ground" -> "Área recreativa"
            tags?.optString("leisure") == "garden" -> "Jardín / área abierta"
            tags?.optString("leisure") == "pitch" -> "Cancha / espacio abierto"
            else -> "Espacio abierto cercano"
        }
    }

    private data class ScoredZone(
        val zona: SafeZone,
        val distKm: Double,
        val prioridad: Int
    )

    companion object {
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val RADIO_OSM_M = 1200
        private const val RADIO_OSM_KM = 1.2
        private const val RADIO_CATALOGO_KM = 0.5
        private const val RADIO_FAVORITA_KM = 1.5

        val ZONAS = listOf(
            SafeZone("Parque Kennedy (Miraflores)", -12.1211, -77.0297),
            SafeZone("Campo de Marte (Jesús María)", -12.0689, -77.0425),
            SafeZone("Plaza San Martín (Cercado)", -12.0516, -77.0350),
            SafeZone("Parque de la Exposición", -12.0608, -77.0369),
            SafeZone("Parque Universitario", -12.0528, -77.0306),
            SafeZone("Costa Verde – Bajada Balta", -12.1305, -77.0308),
            SafeZone("Parque Reducto Nº2", -12.1278, -77.0215),
            SafeZone("Óvalo Gutiérrez", -12.1120, -77.0365),
            SafeZone("Parque de la Amistad (SJL)", -11.9790, -76.9990),
            SafeZone("Alameda Sur (Chorrillos)", -12.1685, -76.9950),
            SafeZone("Plaza Grau (Callao)", -12.0566, -77.1185),
            SafeZone("Plaza de Armas de Ica", -14.0678, -75.7286),
            SafeZone("Plaza de Armas de Arequipa", -16.3988, -71.5369),
            SafeZone("Plaza de Armas de Trujillo", -8.1116, -79.0288),
            SafeZone("Plaza de Armas de Piura", -5.1945, -80.6328),
            SafeZone("Plaza de Armas de Tacna", -18.0138, -70.2506),
            SafeZone("Plaza de Armas de Chiclayo", -6.7714, -79.8409)
        )

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
    }
}
