package com.example.stefanykhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@Composable
fun MaterialAnalyticsButton(item: CatalogItem) {
    val user = AppRepository.currentUser.value ?: return
    if (user.role != UserRole.ADMIN && !(user.role == UserRole.TEACHER && user.id == item.teacherId)) return
    var open by remember(item.id) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("📊 Аналітика матеріалу") }
    if (open) MaterialAnalyticsDialog(item) { open = false }
}

@Composable
private fun MaterialAnalyticsDialog(item: CatalogItem, dismiss: () -> Unit) {
    var data by remember { mutableStateOf<MaterialAnalytics?>(null) }
    var settings by remember { mutableStateOf<AnalyticsSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val userId = AppRepository.currentUser.value?.id
    DisposableEffect(item.id, userId) {
        val stop = AppRepository.observeMaterialAnalytics(item.id) { value, failure -> data = value; error = failure }
        val stopSettings = AppRepository.observeAnalyticsSettings(item.id) { value, failure -> settings = value; settingsError = failure }
        onDispose { stop(); stopSettings() }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Аналітика: ${item.title}") },
        confirmButton = { TextButton(onClick = dismiss) { Text("Закрити") } },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (data == null && error == null) CircularProgressIndicator()
                data?.let { analytics ->
                    Text("Перегляди: ${analytics.views} • Унікальні читачі: ${analytics.readers}")
                    Text("Успішні завантаження: ${analytics.downloads}")
                    Text("Повторні відкриття враховуються як нові перегляди. Збережений файл повторно не завантажується.", style = MaterialTheme.typography.bodySmall)
                    if (analytics.events.isEmpty()) Text("Активності ще немає. Історичні перегляди до ввімкнення аналітики не відновлюються.")
                    settings?.let { config ->
                        val count = analytics.groupDownloads(config.group)
                        val percent = analytics.percent(config)
                        if (percent != null) {
                            Text("Група ${config.group}: $percent% завантажили ($count/${config.groupSize})")
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        } else Text(if (config.groupSize > 0 && count > config.groupSize) "Завантажень від унікальних студентів більше, ніж задана чисельність. Уточніть склад групи." else "Задайте групу та чисельність для розрахунку відсотка.")
                        var beforeDeadline by remember { mutableStateOf(false) }
                        if (config.deadline > 0) {
                            Text("За 2 години до дедлайну: ${analytics.beforeDeadline(config.deadline)} завантажень")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(beforeDeadline, { beforeDeadline = it })
                                Text("24 години перед дедлайном")
                            }
                        }

                        val now = remember { System.currentTimeMillis() }
                        ActivityChart(analytics, if (beforeDeadline && config.deadline > 0) config.deadline else now)
                        AnalyticsSettingsEditor(item.id, config)
                    }
                }
            }
        })
}

@Composable
private fun ActivityChart(analytics: MaterialAnalytics, end: Long) {
    val counts = analytics.hours(end)
    val maximum = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())
    Text("Завантаження за 24 години", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { index, count ->
            val label = formatter.format(Instant.ofEpochMilli(end - (24 - index) * 3_600_000L))
            Box(Modifier.weight(1f).height((3 + 87f * count / maximum).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (count == 0) .15f else 1f))
                .semantics { contentDescription = "$label: $count завантажень" })
        }
    }
    Text("${formatter.format(Instant.ofEpochMilli(end - 24 * 3_600_000L))} — ${formatter.format(Instant.ofEpochMilli(end))}", style = MaterialTheme.typography.labelSmall)
    if (counts.any { it > 0 }) {
        val index = counts.indexOf(counts.maxOrNull())
        val peak = end - (24 - index) * 3_600_000L
        Text("Пікова година: ${formatter.format(Instant.ofEpochMilli(peak))}, завантажень: ${counts[index]}")
    } else Text("У цьому часовому вікні завантажень немає.")
}

@Composable
private fun AnalyticsSettingsEditor(itemId: Int, initial: AnalyticsSettings) {
    val formatter = remember { DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT) }
    var group by remember(initial) { mutableStateOf(initial.group) }
    var size by remember(initial) { mutableStateOf(initial.groupSize.takeIf { it > 0 }?.toString().orEmpty()) }
    var deadline by remember(initial) { mutableStateOf(if (initial.deadline > 0) formatter.format(Instant.ofEpochMilli(initial.deadline).atZone(ZoneId.systemDefault())) else "") }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    HorizontalDivider()
    Text("Налаштування матеріалу", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(group, { group = it }, label = { Text("Група, наприклад ІПЗ-21") }, singleLine = true)
    OutlinedTextField(size, { size = it }, label = { Text("Чисельність групи") }, singleLine = true)
    OutlinedTextField(deadline, { deadline = it }, label = { Text("Дедлайн: рррр-ММ-дд ГГ:хх") }, singleLine = true)
    Text("Чисельність задає викладач. Група студента береться з його профілю. Дедлайн — у часовому поясі пристрою (${ZoneId.systemDefault()}). Порожні поля вимикають відповідний розрахунок.", style = MaterialTheme.typography.bodySmall)
    Button(enabled = !saving, onClick = {
        val number = if (size.isBlank()) 0 else size.toIntOrNull()
        val epoch = if (deadline.isBlank()) 0L else runCatching { LocalDateTime.parse(deadline.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        if (number == null || number !in 0..10000 || (number > 0 && group.isBlank()) || epoch == null || epoch < 0) {
            message = "Перевірте групу, чисельність (1–10000) і формат дати."
        } else {
            saving = true
            AppRepository.saveAnalyticsSettings(itemId, AnalyticsSettings(group.trim(), number, epoch)) {
                saving = false; message = it ?: "Збережено"
            }
        }
    }) { Text(if (saving) "Зберігаємо…" else "Зберегти налаштування") }
    message?.let { Text(it) }
}
