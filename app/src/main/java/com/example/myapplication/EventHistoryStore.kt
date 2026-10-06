package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EventRecord(
    val id: Long,
    val timestampMs: Long,
    val tipo: String,
    val fuente: String,
    val detalle: String,
    val lat: Double?,
    val lon: Double?,
    val disparoAlerta: Boolean,
    val falsaAlarma: Boolean = false
)

class EventHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(record: EventRecord) {
        val list = load().toMutableList()
        list.add(0, record)
        // Mantener últimos 100
        while (list.size > 100) list.removeAt(list.lastIndex)
        save(list)
    }

    fun markFalseAlarm(id: Long) {
        val list = load().map {
            if (it.id == id) it.copy(falsaAlarma = true) else it
        }
        save(list)
    }

    fun load(): List<EventRecord> {
        val raw = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        EventRecord(
                            id = o.getLong("id"),
                            timestampMs = o.getLong("timestampMs"),
                            tipo = o.getString("tipo"),
                            fuente = o.optString("fuente", "local"),
                            detalle = o.optString("detalle", ""),
                            lat = if (o.has("lat") && !o.isNull("lat")) o.getDouble("lat") else null,
                            lon = if (o.has("lon") && !o.isNull("lon")) o.getDouble("lon") else null,
                            disparoAlerta = o.optBoolean("disparoAlerta", false),
                            falsaAlarma = o.optBoolean("falsaAlarma", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().putString(KEY_EVENTS, "[]").apply()
    }

    private fun save(list: List<EventRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("timestampMs", r.timestampMs)
                    put("tipo", r.tipo)
                    put("fuente", r.fuente)
                    put("detalle", r.detalle)
                    if (r.lat != null) put("lat", r.lat) else put("lat", JSONObject.NULL)
                    if (r.lon != null) put("lon", r.lon) else put("lon", JSONObject.NULL)
                    put("disparoAlerta", r.disparoAlerta)
                    put("falsaAlarma", r.falsaAlarma)
                }
            )
        }
        prefs.edit().putString(KEY_EVENTS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "sismi_history"
        private const val KEY_EVENTS = "events"
    }
}
