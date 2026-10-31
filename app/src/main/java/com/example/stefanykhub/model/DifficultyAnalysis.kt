package com.example.stefanykhub.model

data class SubjectDifficulty(val subject: String, val feedback: ReactionSummary)

data class DifficultyAnalysis(
    val rankedMaterials: List<MaterialDifficulty>,
    val attentionMaterials: List<MaterialDifficulty>,
    val positiveMaterials: List<MaterialDifficulty>,
    val subjectSummaries: Map<String, ReactionSummary>,
    val mostDifficultSubject: SubjectDifficulty?,
    val bestReceivedMaterial: MaterialDifficulty?
)
