package com.example.stefanykhub

import com.example.stefanykhub.model.*
import org.junit.Assert.*
import org.junit.Test

class DifficultyAnalysisFacadeTest {
    private val facade = DifficultyAnalysisFacade()

    private fun material(id: Int, subject: String = "Бази даних") = CatalogItem(
        id = id, title = "Матеріал $id", type = "Лабораторна", subject = subject,
        teacher = "Викладач", teacherId = "teacher", date = "2026-10-31",
        fileType = "PDF", fileSize = "1 MB"
    )

    private fun feedback(concern: Int = 0, positive: Int = 0) = ReactionSummary(mapOf(
        MaterialReaction.QUESTION to concern, MaterialReaction.CLEAR to positive
    ))

    @Test fun emptyInputHasNoInventedResults() {
        val result = facade.analyze(emptyList(), emptyMap())
        assertTrue(result.rankedMaterials.isEmpty())
        assertTrue(result.attentionMaterials.isEmpty())
        assertTrue(result.positiveMaterials.isEmpty())
        assertTrue(result.subjectSummaries.isEmpty())
        assertNull(result.mostDifficultSubject)
        assertNull(result.bestReceivedMaterial)
    }

    @Test fun facadeRanksAndSeparatesActionableAndPositiveMaterials() {
        val result = facade.analyze((1..4).map { material(it) }, mapOf(
            1 to feedback(concern = 1), 2 to feedback(concern = 3, positive = 1),
            3 to feedback(positive = 4), 4 to feedback(concern = 3, positive = 3)
        ))
        assertEquals(listOf(2, 1, 4, 3), result.rankedMaterials.map { it.item.id })
        assertEquals(listOf(2), result.attentionMaterials.map { it.item.id })
        assertEquals(listOf(3), result.positiveMaterials.map { it.item.id })
        assertEquals(3, result.bestReceivedMaterial?.item?.id)
    }

    @Test fun subjectComparisonUsesWeightedResponsesRatherThanAveragePercentages() {
        val result = facade.analyze(listOf(material(1), material(2), material(3, "Математика")), mapOf(
            1 to feedback(concern = 1), 2 to feedback(positive = 9),
            3 to feedback(concern = 2, positive = 3)
        ))
        assertEquals(10, result.subjectSummaries.getValue("Бази даних").total)
        assertEquals(10, result.subjectSummaries.getValue("Бази даних").difficultyPercent)
        assertEquals("Математика", result.mostDifficultSubject?.subject)
        assertEquals(40, result.mostDifficultSubject?.feedback?.difficultyPercent)
    }

    @Test fun missingAndLoadedEmptyFeedbackRemainDistinguishable() {
        val result = facade.analyze(listOf(material(1), material(2)), mapOf(2 to ReactionSummary()))
        assertEquals(listOf(2), result.rankedMaterials.map { it.item.id })
        assertEquals(0, result.subjectSummaries.getValue("Бази даних").total)
        assertNull(result.mostDifficultSubject)
        assertNull(result.bestReceivedMaterial)
    }

    @Test fun summariesOutsideSelectedMaterialsDoNotAffectAnalysis() {
        val result = facade.analyze(listOf(material(1)), mapOf(
            1 to feedback(positive = 3), 99 to feedback(concern = 100)
        ))
        assertEquals(listOf(1), result.rankedMaterials.map { it.item.id })
        assertEquals(3, result.subjectSummaries.getValue("Бази даних").total)
        assertTrue(result.attentionMaterials.isEmpty())
    }

    @Test fun bestReceivedRequiresStablePositiveSignalAndUsesVoteCount() {
        val result = facade.analyze((1..3).map { material(it) }, mapOf(
            1 to feedback(positive = 2), 2 to feedback(positive = 4),
            3 to feedback(concern = 1, positive = 5)
        ))
        assertEquals(listOf(3, 2), result.positiveMaterials.map { it.item.id })
        assertEquals(3, result.bestReceivedMaterial?.item?.id)
    }

    @Test fun repeatedAnalysisUsesCurrentSnapshotWithoutLeakingPreviousResults() {
        val materials = listOf(material(1))
        val before = facade.analyze(materials, mapOf(1 to feedback(concern = 3)))
        val after = facade.analyze(materials, mapOf(1 to feedback(positive = 3)))
        assertEquals(1, before.attentionMaterials.size)
        assertTrue(after.attentionMaterials.isEmpty())
        assertEquals(1, after.positiveMaterials.size)
        assertEquals(100, before.rankedMaterials.single().feedback.difficultyPercent)
    }

    @Test fun tiedIndicatorsPreserveExistingRankingOrder() {
        val result = facade.analyze(listOf(material(2, "Математика"), material(1)), mapOf(
            1 to feedback(positive = 3), 2 to feedback(positive = 3)
        ))
        assertEquals(listOf(1, 2), result.rankedMaterials.map { it.item.id })
        assertEquals(1, result.bestReceivedMaterial?.item?.id)
        assertEquals("Бази даних", result.mostDifficultSubject?.subject)
    }
}
