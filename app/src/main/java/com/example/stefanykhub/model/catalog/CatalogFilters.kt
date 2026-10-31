package com.example.stefanykhub.model.catalog

import com.example.stefanykhub.model.CatalogItem

class SearchFilter(query: String) : CatalogFilter {
    private val query = query.trim().lowercase()

    override fun matches(item: CatalogItem): Boolean = query.isEmpty() ||
        listOf(item.title, item.type, item.subject, item.teacher, item.tags, item.description)
            .any { it.lowercase().contains(query) }
}

class CourseFilter(private val course: String) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = item.course == course
}

class SemesterFilter(private val semester: Int) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = item.semester == semester
}

class TypeFilter(private val type: String) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean {
        val plural = when (item.type) {
            "Лекція" -> "Лекції"
            "Лабораторна" -> "Лабораторні"
            else -> item.type
        }
        return type == item.type || type == plural
    }
}

class SubjectFilter(private val subject: String) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = item.subject == subject
}

class FavoriteFilter : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = item.isFavorite
}

class TeacherOwnershipFilter(private val teacherId: String) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = item.teacherId == teacherId
}
