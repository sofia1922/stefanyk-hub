package com.example.stefanykhub

import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class CatalogFilterTest {
    private val item = CatalogItem(
        id = 1, title = "Основи SQL", type = "Лабораторна", subject = "Бази даних",
        teacher = "Олена", teacherId = "teacher-a", date = "2026-10-31",
        fileType = "PDF", fileSize = "1 MB", course = "2 курс", semester = 3,
        tags = "Room", description = "Запити до таблиць", isFavorite = true
    )

    @Test fun allRequiresEveryChildToMatch() {
        assertTrue(AllOfFilter(listOf(CourseFilter("2 курс"), FavoriteFilter())).matches(item))
        assertFalse(AllOfFilter(listOf(CourseFilter("3 курс"), FavoriteFilter())).matches(item))
    }

    @Test fun anyAcceptsOneMatchingChild() {
        assertTrue(AnyOfFilter(listOf(CourseFilter("1 курс"), CourseFilter("2 курс"))).matches(item))
        assertFalse(AnyOfFilter(listOf(CourseFilter("1 курс"), CourseFilter("3 курс"))).matches(item))
    }

    @Test fun compositesCanContainOtherComposites() {
        val filter = AllOfFilter(listOf(
            AnyOfFilter(listOf(CourseFilter("2 курс"), CourseFilter("3 курс"))),
            AllOfFilter(listOf(FavoriteFilter(), SearchFilter("SQL")))
        ))
        assertTrue(filter.matches(item))
        assertFalse(filter.matches(item.copy(isFavorite = false)))
        assertFalse(filter.matches(item.copy(course = "4 курс")))
    }

    @Test fun emptyAllMatchesAndEmptyAnyRejects() {
        assertTrue(AllOfFilter(emptyList()).matches(item))
        assertFalse(AnyOfFilter(emptyList()).matches(item))
    }

    @Test fun searchTrimsAndIgnoresCaseAcrossAllExistingFields() {
        listOf(" SQL ", "лаБОРаторна", "БАЗИ", "олена", "ROOM", "ТАБЛИЦЬ").forEach {
            assertTrue(it, SearchFilter(it).matches(item))
        }
        assertTrue(SearchFilter("   ").matches(item))
        assertFalse(SearchFilter("missing").matches(item))
    }

    @Test fun typeAcceptsExistingSingularAndPluralNames() {
        assertTrue(TypeFilter("Лабораторні").matches(item))
        assertTrue(TypeFilter("Лабораторна").matches(item))
        assertTrue(TypeFilter("Лекції").matches(item.copy(type = "Лекція")))
        assertTrue(TypeFilter("Методички").matches(item.copy(type = "Методички")))
        assertFalse(TypeFilter("Лекції").matches(item))
    }

    @Test fun builderCombinesSelectionsAndOptionalRestrictions() {
        val filter = buildCatalogFilter(
            "sql", setOf("2 курс", "3 курс"), setOf(3, 4), setOf("Лабораторні"),
            selectedSubject = "Бази даних", favoritesOnly = true, teacherId = "teacher-a"
        )
        assertTrue(filter.matches(item))
        listOf(
            item.copy(course = "1 курс"), item.copy(semester = 1), item.copy(type = "Лекція"),
            item.copy(subject = "Математика"), item.copy(isFavorite = false),
            item.copy(teacherId = "teacher-b")
        ).forEach { assertFalse(it.toString(), filter.matches(it)) }
    }

    @Test fun emptySelectionInAnyRequiredCategoryRejectsMaterials() {
        assertFalse(buildCatalogFilter("", emptySet(), setOf(3), setOf("Лабораторні")).matches(item))
        assertFalse(buildCatalogFilter("", setOf("2 курс"), emptySet(), setOf("Лабораторні")).matches(item))
        assertFalse(buildCatalogFilter("", setOf("2 курс"), setOf(3), emptySet()).matches(item))
    }

    @Test fun absentOptionalRestrictionsAllowOtherTeachersAndNonFavorites() {
        val filter = buildCatalogFilter("", setOf("2 курс"), setOf(3), setOf("Лабораторні"))
        assertTrue(filter.matches(item.copy(teacherId = "teacher-b", isFavorite = false)))
    }

    @Test fun refactoredFilterPreservesLegacyResultsAcross2592Configurations() {
        val materials = listOf(
            item, item.copy(id = 2, course = "3 курс", semester = 4, teacherId = "teacher-b"),
            item.copy(id = 3, title = "Алгебра", subject = "Математика", type = "Лекція", isFavorite = false),
            item.copy(id = 4, type = "Методички", course = "1 курс", semester = 1),
            item.copy(id = 5, type = "Лабораторні", isFavorite = false)
        )
        var configurations = 0
        for (query in listOf("", " SQL ", "математика"))
        for (courses in listOf(emptySet(), setOf("1 курс"), setOf("2 курс"), setOf("2 курс", "3 курс")))
        for (semesters in listOf(emptySet(), setOf(3), setOf(1, 3, 4)))
        for (types in listOf(emptySet(), setOf("Лабораторні"), setOf("Лабораторна"), setOf("Лекції", "Методички")))
        for (subject in listOf(null, "Бази даних", "Математика"))
        for (favorites in listOf(false, true))
        for (teacherId in listOf(null, "teacher-a", "teacher-b")) {
            val normalized = query.trim().lowercase()
            val expected = materials.filter { material ->
                val plural = when (material.type) {
                    "Лекція" -> "Лекції"
                    "Лабораторна" -> "Лабораторні"
                    else -> material.type
                }
                (teacherId == null || material.teacherId == teacherId) &&
                    (!favorites || material.isFavorite) &&
                    (normalized.isEmpty() || material.title.lowercase().contains(normalized) ||
                        material.type.lowercase().contains(normalized) ||
                        material.subject.lowercase().contains(normalized) ||
                        material.teacher.lowercase().contains(normalized) ||
                        material.tags.lowercase().contains(normalized) ||
                        material.description.lowercase().contains(normalized)) &&
                    courses.contains(material.course) && semesters.contains(material.semester) &&
                    (types.contains(plural) || types.contains(material.type)) &&
                    (subject == null || material.subject == subject)
            }
            val filter = buildCatalogFilter(query, courses, semesters, types, subject, favorites, teacherId)
            assertEquals("Configuration $configurations", expected, materials.filter(filter::matches))
            configurations++
        }
        assertEquals(2592, configurations)
    }
}
