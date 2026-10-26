package com.example.stefanykhub

import com.example.stefanykhub.model.MaterialReaction
import com.example.stefanykhub.model.ReactionVote
import com.example.stefanykhub.model.nextReaction
import com.example.stefanykhub.model.summarizeReactions
import org.junit.Assert.*
import org.junit.Test

class MaterialReactionTest {
    @Test fun repeatedTapRemovesAndDifferentTapReplacesTheReaction() {
        assertEquals(MaterialReaction.FIRE, nextReaction(null, MaterialReaction.FIRE))
        assertNull(nextReaction(MaterialReaction.FIRE, MaterialReaction.FIRE))
        assertEquals(MaterialReaction.CLEAR, nextReaction(MaterialReaction.FIRE, MaterialReaction.CLEAR))
    }

    @Test fun countsAndOwnReactionAreIndependentForEachStudent() {
        val votes = listOf(
            ReactionVote("a", MaterialReaction.FIRE),
            ReactionVote("b", MaterialReaction.DIFFICULT),
            ReactionVote("c", MaterialReaction.FIRE)
        )
        val summary = summarizeReactions(votes, "b")
        assertEquals(3, summary.total)
        assertEquals(2, summary.counts[MaterialReaction.FIRE])
        assertEquals(MaterialReaction.DIFFICULT, summary.mine)
        assertNull(summarizeReactions(votes, "teacher").mine)
    }

    @Test fun oneStudentNeverAddsMoreThanOneVote() {
        val summary = summarizeReactions(listOf(
            ReactionVote("a", MaterialReaction.FIRE),
            ReactionVote("a", MaterialReaction.CLEAR)
        ), "a")
        assertEquals(1, summary.total)
        assertEquals(MaterialReaction.CLEAR, summary.mine)
        assertFalse(summary.counts.containsKey(MaterialReaction.FIRE))
    }

    @Test fun emptyCollectionHasNoOwnReactionOrSignal() {
        val summary = summarizeReactions(emptyList(), "a")
        assertEquals(0, summary.total)
        assertNull(summary.mine)
        assertFalse(summary.needsExplanation)
    }

    @Test fun difficultySignalRequiresThreeVotesAndStrictMajorityOfResponses() {
        fun votes(difficult: Int, clear: Int) =
            (1..difficult).map { ReactionVote("d$it", MaterialReaction.DIFFICULT) } +
                (1..clear).map { ReactionVote("c$it", MaterialReaction.CLEAR) }
        assertFalse(summarizeReactions(votes(2, 0), "teacher").needsExplanation)
        assertFalse(summarizeReactions(votes(3, 3), "teacher").needsExplanation)
        assertTrue(summarizeReactions(votes(3, 2), "teacher").needsExplanation)
        assertFalse(summarizeReactions(votes(3, 4), "teacher").needsExplanation)
    }
}
