package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settings = AppSettings(this)
        val group = findViewById<RadioGroup>(R.id.groupSensibilidad)
        val rbConservador = findViewById<RadioButton>(R.id.rbConservador)
        val rbNormal = findViewById<RadioButton>(R.id.rbNormal)
        val rbSensible = findViewById<RadioButton>(R.id.rbSensible)
        val inputNombre = findViewById<EditText>(R.id.inputContactoNombre)
        val inputPhone = findViewById<EditText>(R.id.inputContactoTelefono)
        val switchIgp = findViewById<SwitchMaterial>(R.id.switchIgp)

        when (settings.sensitivity) {
            SensitivityProfile.CONSERVADOR -> rbConservador.isChecked = true
            SensitivityProfile.NORMAL -> rbNormal.isChecked = true
            SensitivityProfile.SENSIBLE -> rbSensible.isChecked = true
        }
        inputNombre.setText(settings.emergencyContactName)
        inputPhone.setText(settings.emergencyContactPhone)
        switchIgp.isChecked = settings.igpPollingEnabled

        findViewById<MaterialButton>(R.id.btnGuardarAjustes).setOnClickListener {
            settings.sensitivity = when (group.checkedRadioButtonId) {
                R.id.rbConservador -> SensitivityProfile.CONSERVADOR
                R.id.rbSensible -> SensitivityProfile.SENSIBLE
                else -> SensitivityProfile.NORMAL
            }
            settings.emergencyContactName = inputNombre.text?.toString().orEmpty()
            settings.emergencyContactPhone = inputPhone.text?.toString().orEmpty()
            settings.igpPollingEnabled = switchIgp.isChecked
            startService(Intent(this, SensorService::class.java).apply {
                action = "ACTION_REFRESH_PROFILE"
            })
            Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.btnVolverAjustes).setOnClickListener { finish() }
    }
}
