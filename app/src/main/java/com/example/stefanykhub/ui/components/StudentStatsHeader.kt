package com.example.stefanykhub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AchievementBadge
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.StudyProgress
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.model.studyDayLabel
import kotlinx.coroutines.delay

@Composable
fun StudentStatsHeader(selectedSubject: String?, modifier: Modifier = Modifier) {
    val student = AppRepository.currentUser.value?.takeIf { it.role == UserRole.STUDENT } ?: return

    LaunchedEffect(student.id) {
        while (true) {
            AppRepository.refreshStudyStreak()
            delay(60_000)
        }
    }
    val progress = AppRepository.getStudyProgress(selectedSubject)
    val completed = AppRepository.getCompletedCount()
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Прогрес навчання", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                StudyStreakBadge(AppRepository.studyStreakDays.intValue)
            }
            StudyProgressSection(progress, selectedSubject)
            Text(
                if (progress.total == 0) "Коли з’являться матеріали, тут буде ваш прогрес."
                else "Рекорд активності: ${studyDayLabel(AppRepository.bestStudyStreakDays.intValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AchievementBadges(AppRepository.getAchievementBadges())
            Text(
                when {
                    completed == 0 -> "Наступна ціль: опрацюйте перший матеріал 🎯"
                    completed < 5 -> "До нагороди «Ерудит» залишилося: ${5 - completed}"
                    else -> "Нагороду «Ерудит» здобуто! Продовжуйте навчання 📚"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StudyStreakBadge(days: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "🔥 ${studyDayLabel(days)}${if (days > 0) " поспіль" else ""}",
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StudyProgressSection(progress: StudyProgress, subject: String?, modifier: Modifier = Modifier) {
    val animatedRatio by animateFloatAsState(progress.ratio, label = "studyProgress")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (subject == null) "Загальний прогрес" else "Прогрес дисципліни: $subject",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("${progress.percent}% (${progress.completed}/${progress.total})", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { animatedRatio },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun AchievementBadges(badges: List<AchievementBadge>, modifier: Modifier = Modifier) {
    var selectedBadge by remember { mutableStateOf<AchievementBadge?>(null) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { badge ->
            AchievementBadgeCard(badge, onClick = { selectedBadge = badge })
        }
    }
    selectedBadge?.let { selected ->

        val badge = badges.firstOrNull { it.id == selected.id } ?: selected
        AlertDialog(
            onDismissRequest = { selectedBadge = null },
            title = { Text("${badge.icon} ${badge.title}") },
            text = { Text("${badge.description}\n\n${if (badge.isUnlocked) "Нагороду здобуто!" else "Умову ще не виконано."}") },
            confirmButton = { TextButton(onClick = { selectedBadge = null }) { Text("Зрозуміло") } }
        )
    }
}

@Composable
fun AchievementBadgeCard(badge: AchievementBadge, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(136.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (badge.isUnlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (badge.isUnlocked) badge.icon else "🔒", style = MaterialTheme.typography.titleLarge)
            Text(badge.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(if (badge.isUnlocked) "Здобуто" else "Переглянути умову", style = MaterialTheme.typography.labelSmall)
        }
    }
}
