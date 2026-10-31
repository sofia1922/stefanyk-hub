package com.example.stefanykhub.model.catalog

import com.example.stefanykhub.model.CatalogItem

fun interface CatalogFilter {
    fun matches(item: CatalogItem): Boolean
}

class AllOfFilter(private val children: List<CatalogFilter>) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = children.all { it.matches(item) }
}

class AnyOfFilter(private val children: List<CatalogFilter>) : CatalogFilter {
    override fun matches(item: CatalogItem): Boolean = children.any { it.matches(item) }
}
