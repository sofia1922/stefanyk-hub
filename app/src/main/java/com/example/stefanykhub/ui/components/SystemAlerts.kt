package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SystemAlerts(manage: Boolean = false) {
    val user = AppRepository.currentUser.value ?: return
    if (manage && user.role != UserRole.ADMIN) return
    var alerts by remember(user.id, manage) { mutableStateOf<List<SystemAlert>>(emptyList()) }
    var error by remember(user.id, manage) { mutableStateOf<String?>(null) }
    var loading by remember(user.id, manage) { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<SystemAlert?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(user.id, manage, attempt) {
        val stop = AppRepository.observeSystemAlerts(manage) { list, failure -> alerts = list; error = failure; loading = false }
        onDispose { stop() }
    }
    if (manage) Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { composing = true }) { Text("🚨 Глобальне сповіщення") }
        Text("Банер для всіх ролей у відкритому додатку. Термінові повідомлення показуються першими.", style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && error == null && alerts.isEmpty()) Text("Глобальних сповіщень ще немає")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(alerts, key = { it.id }) { alert -> AlertCard(alert) {
                TextButton(enabled = !busy, onClick = { changing = alert }) { Text(if (alert.active) "Зняти банер" else "Закріпити знову") }
            } }
        }
    } else {
        if (alerts.isNotEmpty()) {
            val first = alerts.first()
            Surface(color = if (first.level == AlertLevel.EMERGENCY) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${first.level.label} • Системне сповіщення" , style = MaterialTheme.typography.labelLarge)
                        Text(first.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Прочитати повністю • Активних: ${alerts.size}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        error?.let {
            TextButton(onClick = { attempt++; loading = true }) { Text("Не вдалося оновити системні сповіщення. Повторити") }
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Системні сповіщення") },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Закрити") } },
        text = { LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(alerts, key = { it.id }) { AlertCard(it) }
            if (alerts.isEmpty()) item { Text("Активних сповіщень немає") }
        } })
    if (composing) SystemAlertComposer { composing = false }
    changing?.let { alert ->
        AlertDialog(onDismissRequest = { if (!busy) changing = null },
            title = { Text(if (alert.active) "Зняти глобальний банер?" else "Показати банер усім користувачам?") },
            text = { Text(alert.text) },
            dismissButton = { TextButton(enabled = !busy, onClick = { changing = null }) { Text("Скасувати") } },
            confirmButton = { Button(enabled = !busy, onClick = {
                busy = true
                AppRepository.setSystemAlertActive(alert.id, !alert.active) { failure -> busy = false; changing = null; error = failure }
            }) { Text(if (busy) "Зберігаємо…" else "Підтвердити") } })
    }
}

@Composable
private fun AlertCard(alert: SystemAlert, actions: @Composable () -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = if (alert.level == AlertLevel.EMERGENCY) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(alert.level.label, style = MaterialTheme.typography.titleSmall)
            Text(alert.text)
            val date = if (alert.createdAt == 0L) "Синхронізація…" else DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(alert.createdAt))
            Text("${if (alert.active) "Закріплено" else "Знято"} • $date", style = MaterialTheme.typography.labelSmall)
            actions()
        }
    }
}

@Composable
private fun SystemAlertComposer(dismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(AlertLevel.INFO) }
    var dropdown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Сповіщення для всіх") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(enabled = !busy, onClick = { dropdown = true }) { Text(level.label) }
                DropdownMenu(dropdown, { dropdown = false }) {
                    AlertLevel.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label) }, onClick = { level = value; dropdown = false }) }
                }
            }
            OutlinedTextField(text, { if (it.length <= 1000) text = it }, enabled = !busy, minLines = 3, maxLines = 6,
                label = { Text("Текст") }, supportingText = { Text("${text.length}/1000") })
            Row { Checkbox(confirm, { confirm = it }, enabled = !busy); Text("Показати всім студентам, викладачам і адміністраторам") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Скасувати") } },
        confirmButton = { Button(enabled = !busy && confirm && text.isNotBlank(), onClick = {
            busy = true
            AppRepository.publishSystemAlert(text, level) { failure -> busy = false; if (failure == null) dismiss() else error = failure }
        }) { Text(if (busy) "Публікуємо…" else "Опублікувати") } })
}
