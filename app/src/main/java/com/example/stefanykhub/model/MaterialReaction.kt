package com.example.stefanykhub.model

enum class MaterialReaction(val emoji: String, val label: String, val explanation: String) {
    FIRE("🔥", "Вогонь", "Дуже корисний матеріал"),
    DIFFICULT("🤯", "Складно", "Потрібно більше часу або додаткове пояснення"),
    CLEAR("💡", "Зрозуміло", "Пояснення допомогло розібратися"),
    QUESTION("❓", "Є питання", "Потрібна відповідь викладача або консультація")
}

data class ReactionVote(val studentId: String, val reaction: MaterialReaction)

data class ReactionSummary(
    val counts: Map<MaterialReaction, Int> = emptyMap(),
    val mine: MaterialReaction? = null,
    val pending: Boolean = false
) {
    val total: Int get() = counts.values.sum()
    val positiveCount: Int get() = (counts[MaterialReaction.FIRE] ?: 0) + (counts[MaterialReaction.CLEAR] ?: 0)
    val isWellReceived: Boolean get() = positiveCount >= 3 && positiveCount * 2 > total
    val feedbackLabel: String get() = when {
        total == 0 -> "Відгуків ще немає"
        needsExplanation -> "Потрібне додаткове пояснення"
        isWellReceived -> "Матеріал добре сприймається"
        positiveCount > concernCount -> "Перші відгуки позитивні"
        concernCount > positiveCount -> "Є питання — збираємо більше відгуків"
        else -> "Відгуки різні"
    }
    val concernCount: Int get() = (counts[MaterialReaction.DIFFICULT] ?: 0) + (counts[MaterialReaction.QUESTION] ?: 0)
    val difficultyRatio: Float get() = if (total == 0) 0f else concernCount.toFloat() / total
    val difficultyPercent: Int get() = (difficultyRatio * 100).toInt()
    val needsExplanation: Boolean get() = concernCount >= 3 && concernCount * 2 > total
}

fun summarizeReactions(votes: List<ReactionVote>, studentId: String): ReactionSummary {
    val unique = votes.associateBy { it.studentId }.values
    return ReactionSummary(
        counts = unique.groupingBy { it.reaction }.eachCount(),
        mine = unique.firstOrNull { it.studentId == studentId }?.reaction
    )
}

/** A second tap removes the vote; choosing a different emoji replaces it. */
fun nextReaction(current: MaterialReaction?, tapped: MaterialReaction): MaterialReaction? =
    tapped.takeUnless { it == current }
