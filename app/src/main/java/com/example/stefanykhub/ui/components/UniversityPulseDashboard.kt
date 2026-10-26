package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UniversityPulseDashboard() {
    val user = AppRepository.currentUser.value?.takeIf { it.role == UserRole.ADMIN } ?: return
    var presence by remember(user.id) { mutableStateOf<List<PulsePresence>?>(null) }
    var downloads by remember(user.id) { mutableStateOf<List<PulseDownload>?>(null) }
    var storage by remember(user.id) { mutableStateOf<StorageReport?>(null) }
    var presenceError by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var storageLoaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
    }
    DisposableEffect(user.id, refresh) {
        presence = null; downloads = null; storage = null; storageLoaded = false
        presenceError = null; downloadError = null; storageError = null
        val stop = AppRepository.observeUniversityPulse(
            presence = { value, error -> presence = value; presenceError = error },
            downloads = { value, error -> downloads = value; downloadError = error },
            storage = { value, error -> storage = value; storageError = error; storageLoaded = true })
        onDispose { stop() }
    }
    val materials = AppRepository.materials.toList()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text("📊 Пульс Університету", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { refresh++ }) { Text("Оновити дані") }
        }
        item { PulseCard("Активність користувачів") {
            presenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val data = presence
            if (data == null && presenceError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            data?.let {
                val online = it.count { entry -> entry.lastSeen in (now - 120_000)..now }
                val week = it.count { entry -> entry.lastSeen in (now - 7 * 86_400_000L)..now }
                Text("Онлайн ≈ $online", style = MaterialTheme.typography.headlineMedium)
                Text("Активні за 7 днів: $week", style = MaterialTheme.typography.titleMedium)
            }
            Text("Унікальні акаунти всіх ролей. Онлайн — сигнал відкритого додатка за останні 2 хвилини; можливе запізнення після виходу. Відлік починається з цієї версії.", style = MaterialTheme.typography.bodySmall)
        } }
        item { PulseCard("Firebase Storage") {
            storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!storageLoaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            val report = storage
            if (report != null) {
                Text("Використано ${formatStorageBytes(report.bytes)}", style = MaterialTheme.typography.titleLarge)
                val budget = report.budgetBytes?.takeIf { it > 0 }
                if (budget != null) {
                    val ratio = report.bytes.toDouble() / budget
                    Text("Плановий бюджет: ${formatStorageBytes(budget)} • ${String.format(Locale.ROOT, "%.1f", ratio * 100)}%")
                    LinearProgressIndicator(progress = { ratio.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (ratio > 1) Text("Плановий бюджет перевищено", color = MaterialTheme.colorScheme.error)
                } else Text("Плановий бюджет не задано")
                Text("Файлів: ${report.files}")
                val formatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(report.updatedAt))
                Text("Серверний звіт: $formatted", style = MaterialTheme.typography.labelSmall)
                if (now - report.updatedAt > 86_400_000) Text("Звіт старіший за добу. Оновіть серверний звіт.", color = MaterialTheme.colorScheme.error)
            } else if (storageLoaded && storageError == null) {
                Text("Серверного звіту ще немає. Запустіть tools/demo/storage-report.mjs за інструкцією UNIVERSITY_PULSE.md.")
            }
            Text("Обсяг актуальних об’єктів bucket. Плановий бюджет — ваша ціль, а не тарифна квота чи обмежувач оплати.", style = MaterialTheme.typography.bodySmall)
        } }
        item { PulseCard("Завантаження матеріалів") {
            downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (downloads == null && downloadError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            downloads?.let { events ->
                Text("Успішних завантажень: ${events.size}", style = MaterialTheme.typography.titleMedium)
                val currentIds = materials.map { it.id }.toSet()
                val orphaned = events.count { it.materialId !in currentIds }
                if (orphaned > 0) Text("Не включено в рейтинги: $orphaned подій матеріалів, яких немає в поточному каталозі.", style = MaterialTheme.typography.bodySmall)
                RankingChart("Популярні дисципліни", rankDownloads(events, materials, teachers = false))
                RankingChart("Топ викладачів", rankDownloads(events, materials, teachers = true))
            }
            Text("За весь час збору подій. Рейтинги враховують успішні завантаження студентів, а не кеш читання. Старі локальні лічильники не включено.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}

@Composable
private fun PulseCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun RankingChart(title: String, entries: List<RankedMetric>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (entries.isEmpty()) Text("Поки недостатньо даних")
    val max = entries.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    entries.forEachIndexed { index, metric ->
        Text("${index + 1}. ${metric.label}: ${metric.count}")
        LinearProgressIndicator(progress = { metric.count.toFloat() / max }, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatStorageBytes(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.ROOT, "%.2f %s", value, units[index])
}
