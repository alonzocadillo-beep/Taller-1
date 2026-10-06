package com.example.myapplication

import android.content.Context

enum class SensitivityProfile(
    val label: String,
    val umbralAccel: Double,
    val umbralLinear: Double,
    val ventanaMs: Long,
    val refractarioMs: Long,
    val minPicosSismo: Int,
    val magnitudOficialMin: Double,
    val radioOficialKm: Double
) {
    CONSERVADOR(
        label = "Conservador",
        umbralAccel = 15.0,
        umbralLinear = 6.0,
        ventanaMs = 2000L,
        refractarioMs = 450L,
        minPicosSismo = 4,
        magnitudOficialMin = 5.0,
        radioOficialKm = 180.0
    ),
    NORMAL(
        label = "Normal",
        umbralAccel = 13.0,
        umbralLinear = 4.5,
        ventanaMs = 2000L,
        refractarioMs = 400L,
        minPicosSismo = 3,
        magnitudOficialMin = 4.5,
        radioOficialKm = 250.0
    ),
    SENSIBLE(
        label = "Sensible",
        umbralAccel = 11.0,
        umbralLinear = 3.5,
        ventanaMs = 2500L,
        refractarioMs = 350L,
        minPicosSismo = 3,
        magnitudOficialMin = 4.0,
        radioOficialKm = 320.0
    );

    companion object {
        fun fromName(name: String?): SensitivityProfile =
            entries.find { it.name == name } ?: NORMAL
    }
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var sensitivity: SensitivityProfile
        get() = SensitivityProfile.fromName(prefs.getString(KEY_SENSITIVITY, SensitivityProfile.NORMAL.name))
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value.name).apply()

    var emergencyContactName: String
        get() = prefs.getString(KEY_CONTACT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONTACT_NAME, value.trim()).apply()

    var emergencyContactPhone: String
        get() = prefs.getString(KEY_CONTACT_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONTACT_PHONE, value.trim()).apply()

    var igpPollingEnabled: Boolean
        get() = prefs.getBoolean(KEY_IGP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_IGP_ENABLED, value).apply()

    var favoriteZoneName: String
        get() = prefs.getString(KEY_FAV_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FAV_NAME, value.trim()).apply()

    var favoriteZoneLat: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_FAV_LAT, java.lang.Double.doubleToRawLongBits(0.0))
        )
        set(value) = prefs.edit().putLong(KEY_FAV_LAT, java.lang.Double.doubleToRawLongBits(value)).apply()

    var favoriteZoneLon: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_FAV_LON, java.lang.Double.doubleToRawLongBits(0.0))
        )
        set(value) = prefs.edit().putLong(KEY_FAV_LON, java.lang.Double.doubleToRawLongBits(value)).apply()

    fun favoriteSafeZone(): SafeZone? {
        val name = favoriteZoneName
        val lat = favoriteZoneLat
        val lon = favoriteZoneLon
        if (name.isBlank() || (lat == 0.0 && lon == 0.0)) return null
        return SafeZone(nombre = name, lat = lat, lon = lon, tipo = "favorita")
    }

    fun clearFavoriteZone() {
        prefs.edit()
            .remove(KEY_FAV_NAME)
            .remove(KEY_FAV_LAT)
            .remove(KEY_FAV_LON)
            .apply()
    }

    companion object {
        private const val PREFS = "sismi_settings"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_IGP_ENABLED = "igp_enabled"
        private const val KEY_FAV_NAME = "fav_zone_name"
        private const val KEY_FAV_LAT = "fav_zone_lat"
        private const val KEY_FAV_LON = "fav_zone_lon"
    }
}
