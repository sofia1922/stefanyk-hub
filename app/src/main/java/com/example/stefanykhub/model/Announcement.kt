package com.example.stefanykhub.model

data class Announcement(
    val id: String,
    val authorId: String,
    val authorName: String,
    val subject: String,
    val text: String,
    val createdAt: Long,
    val scope: String = "SUBJECT",
    val materialId: String? = null,
    val materialTitle: String? = null
)
