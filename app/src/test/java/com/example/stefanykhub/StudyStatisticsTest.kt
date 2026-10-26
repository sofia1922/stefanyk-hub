package com.example.stefanykhub

import com.example.stefanykhub.model.StudyProgress
import com.example.stefanykhub.model.StudyStreak
import com.example.stefanykhub.model.studyDayLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StudyStatisticsTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun firstActivityStartsAtOneAndRepeatedActionsDoNotIncreaseIt() {
        val first = StudyStreak().recordActivity(today)
        assertEquals(StudyStreak(1, "2026-10-20", 1), first)
        assertEquals(first, first.recordActivity(today))
    }

    @Test fun consecutiveCalendarDayExtendsStreakAndRecord() {
        assertEquals(StudyStreak(6, "2026-10-20", 6), StudyStreak(5, "2026-10-19", 5).recordActivity(today))
    }

    @Test fun missedDayResetsCurrentStreakButPreservesRecord() {
        val old = StudyStreak(5, "2026-10-18", 8)
        assertEquals(0, old.visibleDays(today))
        assertEquals(StudyStreak(1, "2026-10-20", 8), old.recordActivity(today))
    }

    @Test fun yesterdayRemainsVisibleBeforeTodaysActivity() {
        assertEquals(5, StudyStreak(5, "2026-10-19", 8).visibleDays(today))
    }

    @Test fun oldDemoDatesAndInvalidDatesRestartSafely() {
        assertEquals(0, StudyStreak(5, "Сьогодні", 8).visibleDays(today))
        assertEquals(StudyStreak(1, "2026-10-20", 8), StudyStreak(5, "invalid", 8).recordActivity(today))
    }

    @Test fun clockMovedBackwardsCannotIncreaseOrOverwriteStreak() {
        val future = StudyStreak(5, "2026-10-21", 8)
        assertEquals(future, future.recordActivity(today))
    }

    @Test fun leapDayAndYearBoundaryAreConsecutiveDates() {
        assertEquals(3, StudyStreak(2, "2024-02-29", 2).recordActivity(LocalDate.of(2024, 3, 1)).days)
        assertEquals(3, StudyStreak(2, "2025-12-31", 2).recordActivity(LocalDate.of(2026, 1, 1)).days)
    }

    @Test fun progressHandlesEmptyCatalogAndComputesPercentage() {
        assertEquals(0f, StudyProgress(0, 0).ratio)
        assertEquals(75, StudyProgress(3, 4).percent)
        assertEquals(100, StudyProgress(4, 4).percent)
    }

    @Test fun ukrainianDayLabelsHandleTeensAndCompoundNumbers() {
        assertEquals("1 день", studyDayLabel(1))
        assertEquals("2 дні", studyDayLabel(2))
        assertEquals("11 днів", studyDayLabel(11))
        assertEquals("21 день", studyDayLabel(21))
        assertEquals("24 дні", studyDayLabel(24))
    }
}
