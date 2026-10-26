package com.example.stefanykhub.model

enum class AlertLevel(val label: String) {
    INFO("ℹ️ Інформація"), MAINTENANCE("🛠️ Технічні роботи"), EMERGENCY("⚠️ Важливо")
}

data class SystemAlert(
    val id: String,
    val text: String,
    val level: AlertLevel,
    val active: Boolean,
    val createdAt: Long
)
