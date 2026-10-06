package com.example.myapplication

data class EmergencyContact(
    val name: String,
    val phone: String
) {
    fun displayLabel(): String {
        val n = name.trim().ifBlank { "Contacto" }
        return "$n ($phone)"
    }
}
