package com.example.stefanykhub

import com.example.stefanykhub.model.*
import org.junit.Assert.*
import org.junit.Test

class DifficultyFeedbackTest {
    @Test fun positiveResponsesProduceAGoodStatus() {
        val feedback = ReactionSummary(mapOf(MaterialReaction.FIRE to 2, MaterialReaction.CLEAR to 2, MaterialReaction.QUESTION to 1))
        assertEquals(4, feedback.positiveCount)
        assertTrue(feedback.isWellReceived)
        assertEquals("Матеріал добре сприймається", feedback.feedbackLabel)
        assertFalse(feedback.needsExplanation)
    }

    @Test fun smallPositiveSampleIsLabelledAsEarlyFeedback() {
        val feedback = ReactionSummary(mapOf(MaterialReaction.CLEAR to 1))
        assertFalse(feedback.isWellReceived)
        assertEquals("Перші відгуки позитивні", feedback.feedbackLabel)
    }

    @Test fun noFeedbackIsNotConfusedWithAGoodRating() {
        assertEquals("Відгуків ще немає", ReactionSummary().feedbackLabel)
        assertFalse(ReactionSummary().isWellReceived)
    }

    @Test fun equalPositiveAndConcernVotesAreMixed() {
        val feedback = ReactionSummary(mapOf(MaterialReaction.FIRE to 3, MaterialReaction.DIFFICULT to 3))
        assertFalse(feedback.isWellReceived)
        assertFalse(feedback.needsExplanation)
        assertEquals("Відгуки різні", feedback.feedbackLabel)
    }
    private fun material(id: Int, subject: String = "Бази даних") = CatalogItem(
        id = id, title = "Room DB $id", type = "Лабораторна", subject = subject,
        teacher = "Викладач", teacherId = "teacher", date = "2026-10-20", fileType = "PDF", fileSize = "1 MB"
    )

    @Test fun combinedDifficultAndQuestionVotesTriggerAttention() {
        val feedback = ReactionSummary(mapOf(
            MaterialReaction.DIFFICULT to 2, MaterialReaction.QUESTION to 1, MaterialReaction.CLEAR to 1
        ))
        assertEquals(3, feedback.concernCount)
        assertEquals(75, feedback.difficultyPercent)
        assertTrue(feedback.needsExplanation)
    }

    @Test fun oneQuestionIsNotEnoughForAnAlertDespiteHighPercentage() {
        val feedback = ReactionSummary(mapOf(MaterialReaction.QUESTION to 1))
        assertEquals(100, feedback.difficultyPercent)
        assertFalse(feedback.needsExplanation)
    }

    @Test fun emptyFeedbackHasZeroDifficulty() {
        assertEquals(0, ReactionSummary().difficultyPercent)
        assertFalse(ReactionSummary().needsExplanation)
    }

    @Test fun rankingPrioritizesActionableSignalsOverTinySamples() {
        val items = listOf(material(1), material(2), material(3))
        val reports = rankMaterialDifficulty(items, mapOf(
            1 to ReactionSummary(mapOf(MaterialReaction.QUESTION to 1)),
            2 to ReactionSummary(mapOf(MaterialReaction.QUESTION to 3, MaterialReaction.FIRE to 1))
        ))
        assertEquals(listOf(2, 1), reports.map { it.item.id })
    }

    @Test fun subjectIndexIsWeightedByNumberOfResponses() {
        val reports = listOf(
            MaterialDifficulty(material(1), ReactionSummary(mapOf(MaterialReaction.QUESTION to 1))),
            MaterialDifficulty(material(2), ReactionSummary(mapOf(MaterialReaction.CLEAR to 9)))
        )
        val subject = subjectDifficulty(reports).getValue("Бази даних")
        assertEquals(10, subject.total)
        assertEquals(10, subject.difficultyPercent)
        assertFalse(subject.needsExplanation)
    }
}
