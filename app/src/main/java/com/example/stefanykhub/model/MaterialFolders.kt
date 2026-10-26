package com.example.stefanykhub.model

/** Folder keys depend on the material type; changing type moves the material automatically. */
fun materialFolder(type: String): String = when {
    type.trim().lowercase().startsWith("лекц") -> "Лекції"
    type.trim().lowercase().startsWith("метод") -> "Методички"
    type.trim().lowercase().startsWith("лаб") -> "Лабораторні"
    type.trim().lowercase().startsWith("практ") -> "Практичні"
    type.trim().lowercase().startsWith("літерат") -> "Література"
    type.contains("відео", ignoreCase = true) || type.contains("аудіо", ignoreCase = true) -> "Відео/Аудіо"
    else -> "Інше"
}

val materialFolderOrder = listOf("Лекції", "Методички", "Лабораторні", "Практичні", "Література", "Відео/Аудіо", "Інше")
