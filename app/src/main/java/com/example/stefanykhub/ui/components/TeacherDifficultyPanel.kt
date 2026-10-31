package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.*

@Composable
fun TeacherDifficultyPanel(materials: List<CatalogItem>, modifier: Modifier = Modifier) {
    val teacher = AppRepository.currentUser.value?.takeIf { it.role == UserRole.TEACHER } ?: return
    val owned = materials.filter { it.teacherId == teacher.id }
    val ids = owned.map { it.id }.sorted()
    DisposableEffect(teacher.id, ids) {
        val stops = ids.map(AppRepository::observeReactions)
        onDispose { stops.forEach { it() } }
    }
    val facade = remember { DifficultyAnalysisFacade() }
    val analysis = facade.analyze(owned, AppRepository.materialReactions.toMap())
    val reports = analysis.rankedMaterials
    val alerts = analysis.attentionMaterials
    val topSubject = analysis.mostDifficultSubject
    val hasErrors = ids.any { AppRepository.reactionErrors[it] != null }
    val positive = analysis.positiveMaterials
    var filter by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val visible = when (filter) { 1 -> positive; 2 -> alerts; else -> reports }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Індекс складності", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Частка 🤯 та ❓ серед усіх реакцій. Сигнал: понад 50% і щонайменше 3 такі відгуки.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Завантажено матеріалів: ${reports.size}/${owned.size}", style = MaterialTheme.typography.labelSmall)
            Text("Добре сприймаються: ${positive.size} • Потребують уваги: ${alerts.size}", style = MaterialTheme.typography.labelMedium)
        }
        if (hasErrors) {
            item {
                Text(
                    "Частина відгуків недоступна. Перевірте мережу й опублікуйте актуальні правила Firestore. Висновки нижче враховують лише завантажені відгуки.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        alerts.firstOrNull()?.let { mostDifficult ->
            item {
                DifficultyNotice(mostDifficult, onClick = { selectedId = mostDifficult.item.id })
            }
        }
        analysis.bestReceivedMaterial?.let { good ->
            item {
                Card(
                    onClick = { selectedId = good.item.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💡 Матеріал добре сприймається", fontWeight = FontWeight.Bold)
                        Text("«${good.item.title}»: ${good.feedback.positiveCount} позитивних із ${good.feedback.total} відгуків.")
                    }
                }
            }
        }
        if (topSubject != null && topSubject.feedback.concernCount > 0) {
            item {
                Text(
                    "Найвищий індекс серед дисциплін із відгуками: ${topSubject.subject} — ${topSubject.feedback.difficultyPercent}% (${topSubject.feedback.concernCount}/${topSubject.feedback.total}).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == 0, onClick = { filter = 0 }, label = { Text("Усі") })
                FilterChip(filter == 1, onClick = { filter = 1 }, label = { Text("Позитивні (${positive.size})") })
                FilterChip(filter == 2, onClick = { filter = 2 }, label = { Text("Увага (${alerts.size})") })
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(when {
                    owned.isEmpty() -> "Опублікуйте матеріал, щоб отримувати відгуки студентів."
                    reports.size < owned.size && !hasErrors -> "Завантажуємо відгуки…"
                    hasErrors -> "Не всі дані отримано. Повторно відкрийте вкладку після перевірки підключення."
                    filter == 2 -> "Наразі немає матеріалів, які перетнули поріг уваги."
                    filter == 1 -> "Поки недостатньо позитивних відгуків для стійкого сигналу."
                    else -> "Студенти ще не залишили реакцій на ваші матеріали."
                })
            }
        }
        items(visible, key = { it.item.id }) { report ->
            Card(onClick = { selectedId = report.item.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.item.title, fontWeight = FontWeight.Bold)
                    Text("${report.item.subject} • ${report.item.type}", style = MaterialTheme.typography.bodySmall)
                    DifficultyProgress(report.feedback)
                    Text(
                        MaterialReaction.entries.joinToString("   ") { "${it.emoji} ${report.feedback.counts[it] ?: 0}" } + "\nУсього відгуків: ${report.feedback.total}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        report.feedback.feedbackLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    reports.firstOrNull { it.item.id == selectedId }?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(report.item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DifficultyProgress(report.feedback)
                    MaterialReactionsBar(report.item.id)
                    Text(
                        if (report.feedback.isWellReceived) "Студенти позитивно оцінюють матеріал. Цей спосіб пояснення можна використати в наступних темах."
                        else if (report.feedback.needsExplanation) "Можна додати розібраний приклад, уточнити завдання або запропонувати консультацію."
                        else "Переглядайте всі типи реакцій. Індекс оновиться, коли студенти змінять свої відгуки."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text("Закрити") } }
        )
    }
}

@Composable
private fun DifficultyNotice(report: MaterialDifficulty, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🤯 Потрібна ваша увага", fontWeight = FontWeight.Bold)
            Text("Матеріал «${report.item.title}» має найвищий пріоритет допомоги серед завантажених відгуків.")
            DifficultyProgress(report.feedback)
            Text("Натисніть, щоб переглянути відгуки", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DifficultyProgress(feedback: ReactionSummary) {
    Text("Індекс: ${feedback.difficultyPercent}% (${feedback.concernCount}/${feedback.total})")
    LinearProgressIndicator(progress = { feedback.difficultyRatio }, modifier = Modifier.fillMaxWidth())
}
