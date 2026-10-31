package com.example.stefanykhub.model

data class MaterialDifficulty(val item: CatalogItem, val feedback: ReactionSummary)

fun rankMaterialDifficulty(
    materials: List<CatalogItem>,
    summaries: Map<Int, ReactionSummary>
): List<MaterialDifficulty> = materials.mapNotNull { item ->
    summaries[item.id]?.let { MaterialDifficulty(item, it) }
}.sortedWith(
    compareByDescending<MaterialDifficulty> { it.feedback.needsExplanation }
        .thenByDescending { it.feedback.difficultyRatio }
        .thenByDescending { it.feedback.concernCount }
        .thenByDescending { it.feedback.total }
        .thenBy { it.item.id }
)


fun subjectDifficulty(reports: List<MaterialDifficulty>): Map<String, ReactionSummary> =
    reports.groupBy { it.item.subject }.mapValues { (_, entries) ->
        ReactionSummary(counts = MaterialReaction.entries.associateWith { reaction ->
            entries.sumOf { it.feedback.counts[reaction] ?: 0 }
        })
    }
