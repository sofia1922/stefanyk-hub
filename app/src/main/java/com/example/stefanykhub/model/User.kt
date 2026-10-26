package com.example.stefanykhub.model

enum class UserRole(val label: String) {
    STUDENT("Студент"),
    TEACHER("Викладач"),
    ADMIN("Адміністратор")
}

data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    // Поля для студента:
    val course: String = "1 курс",
    val specialty: String? = null,
    val group: String? = null,
    // Поля для викладача:
    val department: String? = null
)
