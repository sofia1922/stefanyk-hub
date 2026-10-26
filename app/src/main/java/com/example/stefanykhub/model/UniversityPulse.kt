package com.example.stefanykhub.model

data class PulsePresence(val uid: String, val lastSeen: Long)
data class PulseDownload(val materialId: Int)
data class StorageReport(val bytes: Long, val files: Long, val updatedAt: Long, val budgetBytes: Long?)
data class RankedMetric(val label: String, val count: Int)

fun rankDownloads(downloads: List<PulseDownload>, materials: List<CatalogItem>, teachers: Boolean): List<RankedMetric> {
    val byId = materials.associateBy { it.id }
    val groups = downloads.mapNotNull { event -> byId[event.materialId] }.groupBy {
        if (teachers) it.teacherId.ifBlank { "name:${it.teacher}" } else it.subject
    }
    return groups.map { (key, items) -> RankedMetric(if (teachers) items.first().teacher else key, items.size) }
        .sortedWith(compareByDescending<RankedMetric> { it.count }.thenBy { it.label }).take(5)
}
