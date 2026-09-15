package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton

class AlertaSismoActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var latDest: Double = 0.0
    private var lonDest: Double = 0.0
    private var latOrig: Double = 0.0
    private var lonOrig: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configuración para bypass de bloqueo de pantalla
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        setContentView(R.layout.activity_alerta_sismo)

        // Recuperar coordenadas de origen y destino
        latDest = intent.getDoubleExtra("latDestino", 0.0)
        lonDest = intent.getDoubleExtra("lonDestino", 0.0)
        latOrig = intent.getDoubleExtra("latOrigen", 0.0)
        lonOrig = intent.getDoubleExtra("lonOrigen", 0.0)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<MaterialButton>(R.id.btnCerrar).setOnClickListener {
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Requerimiento 2: Mapa en 3 colores (Gris, Rojo, Verde)
        // Aplicamos estilo Gris mediante JSON
        val styleJson = """
            [
              { "featureType": "all", "elementType": "all", "stylers": [ { "saturation": -100 } ] },
              { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#c0c0c0" } ] }
            ]
        """.trimIndent()
        mMap.setMapStyle(MapStyleOptions(styleJson))

        val origen = LatLng(latOrig, lonOrig)
        val destino = LatLng(latDest, lonDest)

        // Dibujar Ruta (Verde)
        mMap.addPolyline(PolylineOptions()
            .add(origen, destino)
            .width(12f)
            .color(Color.GREEN)
            .geodesic(true))

        // Marcador Zona Segura (Verde)
        mMap.addMarker(MarkerOptions()
            .position(destino)
            .title("ZONA SEGURA")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

        // Marcador Usuario (Azul/Gris para no romper la estética)
        mMap.addMarker(MarkerOptions()
            .position(origen)
            .title("Tu ubicación")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))

        // Zonas Inseguras Simuladas (Rojo)
        val puntosRojos = listOf(
            LatLng(latOrig + 0.0003, lonOrig + 0.0003),
            LatLng(latOrig - 0.0004, lonOrig - 0.0002)
        )
        for (punto in puntosRojos) {
            mMap.addCircle(CircleOptions()
                .center(punto)
                .radius(25.0)
                .fillColor(0x55FF0000)
                .strokeColor(Color.RED)
                .strokeWidth(2f))
        }

        // Ajustar cámara para ver ambos puntos
        val bounds = LatLngBounds.Builder().include(origen).include(destino).build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
    }
}