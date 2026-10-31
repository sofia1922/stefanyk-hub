package com.example.stefanykhub.model

class DifficultyAnalysisFacade {
    fun analyze(
        materials: List<CatalogItem>,
        summaries: Map<Int, ReactionSummary>
    ): DifficultyAnalysis {
        val reports = rankMaterialDifficulty(materials, summaries)
        val subjects = subjectDifficulty(reports)
        val positive = reports.filter { it.feedback.isWellReceived }
        val topSubject = subjects.entries.filter { it.value.total > 0 }
            .maxByOrNull { it.value.difficultyRatio }

        return DifficultyAnalysis(
            rankedMaterials = reports,
            attentionMaterials = reports.filter { it.feedback.needsExplanation },
            positiveMaterials = positive,
            subjectSummaries = subjects,
            mostDifficultSubject = topSubject?.let { SubjectDifficulty(it.key, it.value) },
            bestReceivedMaterial = positive.maxByOrNull { it.feedback.positiveCount }
        )
    }
}
