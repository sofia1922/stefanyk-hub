package com.example.stefanykhub.model

import java.time.LocalDate

/** Calendar dates use the device's local timezone and ISO-8601 format. */
data class StudyStreak(val days: Int = 0, val lastActiveDate: String = "", val bestDays: Int = 0) {
    fun visibleDays(today: LocalDate): Int {
        val last = runCatching { LocalDate.parse(lastActiveDate) }.getOrNull() ?: return 0
        return if (last.isBefore(today.minusDays(1))) 0 else days.coerceAtLeast(0)
    }

    fun recordActivity(today: LocalDate): StudyStreak {
        val last = runCatching { LocalDate.parse(lastActiveDate) }.getOrNull()
        // Repeated actions and a clock moved backwards must not inflate the streak.
        if (last != null && !last.isBefore(today)) return this
        val next = if (last == today.minusDays(1)) days.coerceAtLeast(0) + 1 else 1
        return StudyStreak(next, today.toString(), maxOf(bestDays, next))
    }
}

data class StudyProgress(val completed: Int, val total: Int) {
    val ratio: Float get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val percent: Int get() = (ratio * 100).toInt()
}

fun studyDayLabel(days: Int): String = when {
    days % 100 in 11..14 -> "$days днів"
    days % 10 == 1 -> "$days день"
    days % 10 in 2..4 -> "$days дні"
    else -> "$days днів"
}
