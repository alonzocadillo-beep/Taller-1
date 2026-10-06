package com.example.myapplication

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val store = EventHistoryStore(this)
        val listView = findViewById<ListView>(R.id.listaHistorial)
        val vacio = findViewById<TextView>(R.id.textoHistorialVacio)
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

        fun render() {
            val items = store.load()
            if (items.isEmpty()) {
                vacio.visibility = android.view.View.VISIBLE
                listView.adapter = null
                return
            }
            vacio.visibility = android.view.View.GONE
            val lines = items.map { r ->
                val t = fmt.format(Date(r.timestampMs))
                val fp = if (r.falsaAlarma) " · FALSA ALARMA" else ""
                val alert = if (r.disparoAlerta) " · ALERTA" else ""
                "$t · ${r.tipo}$alert$fp\n${r.fuente}: ${r.detalle}"
            }
            listView.adapter = ArrayAdapter(this, R.layout.item_historial, lines)
        }

        render()

        findViewById<MaterialButton>(R.id.btnLimpiarHistorial).setOnClickListener {
            store.clear()
            render()
            Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnVolverHistorial).setOnClickListener { finish() }
    }
}
