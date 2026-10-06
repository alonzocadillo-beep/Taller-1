package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var textoListaContactos: TextView
    private lateinit var switchAutoSms: SwitchMaterial

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        leerContacto(uri)
    }

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) abrirPickerContactos()
        else Toast.makeText(this, "Se necesita permiso de contactos", Toast.LENGTH_SHORT).show()
    }

    private val requestSms = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            switchAutoSms.isChecked = false
            Toast.makeText(this, "Sin permiso SMS no se puede activar el envío automático", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = AppSettings(this)
        val group = findViewById<RadioGroup>(R.id.groupSensibilidad)
        val rbConservador = findViewById<RadioButton>(R.id.rbConservador)
        val rbNormal = findViewById<RadioButton>(R.id.rbNormal)
        val rbSensible = findViewById<RadioButton>(R.id.rbSensible)
        val switchIgp = findViewById<SwitchMaterial>(R.id.switchIgp)
        switchAutoSms = findViewById(R.id.switchAutoSms)
        textoListaContactos = findViewById(R.id.textoListaContactos)
        val inputZona = findViewById<EditText>(R.id.inputZonaFavoritaNombre)
        val textoCoords = findViewById<TextView>(R.id.textoZonaFavoritaCoords)

        when (settings.sensitivity) {
            SensitivityProfile.CONSERVADOR -> rbConservador.isChecked = true
            SensitivityProfile.NORMAL -> rbNormal.isChecked = true
            SensitivityProfile.SENSIBLE -> rbSensible.isChecked = true
        }
        switchIgp.isChecked = settings.igpPollingEnabled
        switchAutoSms.isChecked = settings.autoSmsEnabled
        inputZona.setText(settings.favoriteZoneName)
        textoCoords.text = if (settings.favoriteZoneLat == 0.0 && settings.favoriteZoneLon == 0.0) {
            "Coords: no configurada"
        } else {
            "Coords: ${settings.favoriteZoneLat}, ${settings.favoriteZoneLon}"
        }
        refrescarListaContactos()

        findViewById<MaterialButton>(R.id.btnAgregarContacto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                abrirPickerContactos()
            } else {
                requestContacts.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        findViewById<MaterialButton>(R.id.btnLimpiarContactos).setOnClickListener {
            settings.emergencyContacts = emptyList()
            refrescarListaContactos()
        }

        switchAutoSms.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestSms.launch(Manifest.permission.SEND_SMS)
                }
                if (settings.emergencyContacts.isEmpty()) {
                    Toast.makeText(this, "Agrega al menos un contacto de la agenda", Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnUsarUbicacionFavorita).setOnClickListener {
            val nombre = inputZona.text?.toString()?.trim().orEmpty()
            if (nombre.isBlank()) {
                Toast.makeText(this, "Escribe un nombre para la zona", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Falta permiso de ubicación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        Toast.makeText(this, "GPS no disponible aún", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    settings.favoriteZoneName = nombre
                    settings.favoriteZoneLat = loc.latitude
                    settings.favoriteZoneLon = loc.longitude
                    textoCoords.text = "Coords: ${loc.latitude}, ${loc.longitude}"
                    Toast.makeText(this, "Zona favorita guardada", Toast.LENGTH_SHORT).show()
                }
        }

        findViewById<MaterialButton>(R.id.btnBorrarFavorita).setOnClickListener {
            settings.clearFavoriteZone()
            inputZona.setText("")
            textoCoords.text = "Coords: no configurada"
        }

        findViewById<MaterialButton>(R.id.btnGuardarAjustes).setOnClickListener {
            settings.sensitivity = when (group.checkedRadioButtonId) {
                R.id.rbConservador -> SensitivityProfile.CONSERVADOR
                R.id.rbSensible -> SensitivityProfile.SENSIBLE
                else -> SensitivityProfile.NORMAL
            }
            settings.igpPollingEnabled = switchIgp.isChecked
            settings.autoSmsEnabled = switchAutoSms.isChecked
            val nombreZona = inputZona.text?.toString()?.trim().orEmpty()
            if (nombreZona.isNotBlank()) {
                settings.favoriteZoneName = nombreZona
            }
            startService(Intent(this, SensorService::class.java).apply {
                action = "ACTION_REFRESH_PROFILE"
            })
            Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.btnVolverAjustes).setOnClickListener { finish() }
    }

    private fun abrirPickerContactos() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContact.launch(intent)
    }

    private fun leerContacto(uri: Uri) {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Contacto" else "Contacto"
                val phone = if (phoneIdx >= 0) cursor.getString(phoneIdx) ?: "" else ""
                if (phone.isBlank()) {
                    Toast.makeText(this, "Ese contacto no tiene teléfono", Toast.LENGTH_SHORT).show()
                    return
                }
                settings.addEmergencyContact(EmergencyContact(name, phone))
                refrescarListaContactos()
                Toast.makeText(this, "Agregado: $name", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer el contacto", Toast.LENGTH_SHORT).show()
        } finally {
            cursor?.close()
        }
    }

    private fun refrescarListaContactos() {
        val list = settings.emergencyContacts
        textoListaContactos.text = if (list.isEmpty()) {
            "Ningún contacto aún"
        } else {
            list.mapIndexed { i, c -> "${i + 1}. ${c.displayLabel()}" }.joinToString("\n")
        }
    }
}
