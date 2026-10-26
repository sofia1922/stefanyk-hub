package com.example.stefanykhub.model

// Модель даних навчального матеріалу / документа
data class CatalogItem(
    val id: Int,
    val title: String,
    val type: String,          // "Лекції", "Лабораторні", "Практичні", "Література", "Відео/Аудіо"
    val subject: String,       // Предмет із довідника 58 дисциплін
    val teacher: String,       // Викладач
    val teacherId: String = "",// ID автора викладача
    val date: String,          // Дата публікації
    val fileType: String,      // "PDF", "DOCX", "ZIP", "MP4", "URL"
    val fileSize: String,      // "3.2 MB"
    val semester: Int = 7,     // Семестр 1..11
    val course: String = "4 курс", // "1 курс", "2 курс", "3 курс", "4 курс", "Магістратура"
    val tags: String = "",     // Теги, наприклад: #Android #Kotlin
    val description: String = "", // Опис матеріалу
    val linkUrl: String? = null,  // Посилання на зовнішній ресурс
    val isFavorite: Boolean = false,
    // AppRepository replaces the list item with copy(...) to notify Compose about changes.
    var isCompleted: Boolean = false, // "Опрацьовано / Виконано", окремо для кожного студента
    val downloadCount: Int = 0,
)
