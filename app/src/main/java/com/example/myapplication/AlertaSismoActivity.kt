package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton

class AlertaSismoActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var latDest: Double = 0.0
    private var lonDest: Double = 0.0
    private var latOrig: Double = 0.0
    private var lonOrig: Double = 0.0

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Encender pantalla y mostrar sobre el bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta_sismo)

        latDest = intent.getDoubleExtra("latDestino", 0.0)
        lonDest = intent.getDoubleExtra("lonDestino", 0.0)
        latOrig = intent.getDoubleExtra("latOrigen", 0.0)
        lonOrig = intent.getDoubleExtra("lonOrigen", 0.0)

        iniciarAlarmaYVibracion()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<MaterialButton>(R.id.btnAbrirRuta).setOnClickListener {
            detenerAlarmaYVibracion()
            val gmmIntentUri = Uri.parse("google.navigation:q=$latDest,$lonDest&mode=w")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            startActivity(mapIntent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnCerrar).setOnClickListener {
            detenerAlarmaYVibracion()
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val styleJson = """
            [
              { "featureType": "all", "elementType": "all", "stylers": [ { "saturation": -100 } ] },
              { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#c0c0c0" } ] }
            ]
        """.trimIndent()
        mMap.setMapStyle(MapStyleOptions(styleJson))

        val origen = LatLng(latOrig, lonOrig)
        val destino = LatLng(latDest, lonDest)

        mMap.addPolyline(
            PolylineOptions()
                .add(origen, destino)
                .width(12f)
                .color(Color.GREEN)
                .geodesic(true)
        )

        mMap.addMarker(
            MarkerOptions()
                .position(destino)
                .title("ZONA SEGURA")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        mMap.addMarker(
            MarkerOptions()
                .position(origen)
                .title("Tu ubicación")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        val puntosRojos = listOf(
            LatLng(latOrig + 0.0003, lonOrig + 0.0003),
            LatLng(latOrig - 0.0004, lonOrig - 0.0002)
        )
        for (punto in puntosRojos) {
            mMap.addCircle(
                CircleOptions()
                    .center(punto)
                    .radius(25.0)
                    .fillColor(0x55FF0000)
                    .strokeColor(Color.RED)
                    .strokeWidth(2f)
            )
        }

        val bounds = LatLngBounds.Builder().include(origen).include(destino).build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
    }

    private fun iniciarAlarmaYVibracion() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val patronVibracion = longArrayOf(0, 500, 200, 500, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(patronVibracion, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(patronVibracion, 0)
        }

        var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
        ringtone?.play()
    }

    private fun detenerAlarmaYVibracion() {
        vibrator?.cancel()
        ringtone?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerAlarmaYVibracion()
    }
}
