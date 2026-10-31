package com.example.stefanykhub.model


data class CatalogItem(
    val id: Int,
    val title: String,
    val type: String,
    val subject: String,
    val teacher: String,
    val teacherId: String = "",
    val date: String,
    val fileType: String,
    val fileSize: String,
    val semester: Int = 7,
    val course: String = "4 курс",
    val tags: String = "",
    val description: String = "",
    val linkUrl: String? = null,
    val isFavorite: Boolean = false,

    var isCompleted: Boolean = false,
    val downloadCount: Int = 0,
)
