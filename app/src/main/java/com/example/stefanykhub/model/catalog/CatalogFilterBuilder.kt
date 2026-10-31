package com.example.stefanykhub.model.catalog

fun buildCatalogFilter(
    searchQuery: String,
    selectedCourses: Set<String>,
    selectedSemesters: Set<Int>,
    selectedTypes: Set<String>,
    selectedSubject: String? = null,
    favoritesOnly: Boolean = false,
    teacherId: String? = null
): CatalogFilter = AllOfFilter(buildList {
    add(SearchFilter(searchQuery))
    add(AnyOfFilter(selectedCourses.map { CourseFilter(it) }))
    add(AnyOfFilter(selectedSemesters.map { SemesterFilter(it) }))
    add(AnyOfFilter(selectedTypes.map { TypeFilter(it) }))
    selectedSubject?.let { add(SubjectFilter(it)) }
    if (favoritesOnly) add(FavoriteFilter())
    teacherId?.let { add(TeacherOwnershipFilter(it)) }
})
