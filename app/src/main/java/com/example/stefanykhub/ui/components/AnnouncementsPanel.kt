package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.Announcement
import com.example.stefanykhub.model.UserRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnnouncementsPanel(subject: String? = null, manage: Boolean = false, materialId: Int? = null) {
    val user = AppRepository.currentUser.value ?: return
    if (!manage && (user.role != UserRole.STUDENT || subject == null)) return
    var announcements by remember(subject, manage, user.id, materialId) { mutableStateOf<List<Announcement>>(emptyList()) }
    var error by remember(subject, manage, user.id, materialId) { mutableStateOf<String?>(null) }
    var loading by remember(subject, manage, user.id, materialId) { mutableStateOf(true) }
    var expanded by remember(subject, user.id, materialId) { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Announcement?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(subject, manage, user.id, materialId) {
        val stop = AppRepository.observeAnnouncements(subject, manage, materialId) { list, failure ->
            announcements = list; error = failure; loading = false
        }
        onDispose { stop() }
    }
    if (manage) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { composing = true }) { Text("📢 Створити оголошення") }
            Text("Для дисципліни — банер у каталозі; для матеріалу — банер у його картці та вікні читання.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (announcements.isEmpty() && error == null) Text("Оголошень ще немає")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(announcements, key = { it.id }) { item ->
                    AnnouncementCard(item) {
                        TextButton(onClick = { deleting = item }, enabled = !busy) { Text("Видалити") }
                    }
                }
            }
        }
    } else {
        if (announcements.isNotEmpty()) {
            val latest = announcements.first()
            Card(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📢 ${if (materialId == null) subject else "До цього матеріалу"} • Оголошення (${announcements.size})", style = MaterialTheme.typography.labelLarge)
                    Text(latest.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${latest.authorName} • Натисніть, щоб прочитати всі", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        error?.let { Text("Оголошення недоступні: $it", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("📢 $subject") },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Закрити") } },
        text = { LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(announcements, key = { it.id }) { AnnouncementCard(it) }
        } })
    if (composing) AnnouncementComposer { composing = false }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Видалити оголошення?") },
            text = { Text(item.text) }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Скасувати") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                AppRepository.deleteAnnouncement(item.id) { failure -> busy = false; error = failure; deleting = null }
            }) { Text(if (busy) "Видаляємо…" else "Видалити") } })
    }
}

@Composable
private fun AnnouncementCard(item: Announcement, actions: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.subject, style = MaterialTheme.typography.titleSmall)
            Text(if (item.scope == "MATERIAL") "До матеріалу: ${item.materialTitle ?: item.materialId}" else "Для всієї дисципліни", style = MaterialTheme.typography.labelMedium)
            Text(item.text)
            val date = if (item.createdAt == 0L) "Синхронізація…" else DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.createdAt))
            Text("${item.authorName} • $date", style = MaterialTheme.typography.labelSmall)
            actions()
        }
    }
}

@Composable
private fun AnnouncementComposer(dismiss: () -> Unit) {
    val user = AppRepository.currentUser.value ?: return
    val materials = AppRepository.materials.filter { user.role == UserRole.ADMIN || it.teacherId == user.id }
    val subjects = materials.map { it.subject }.distinct().sorted()
    var selected by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dropdown by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf("SUBJECT") }
    var selectedMaterial by remember { mutableStateOf<Int?>(null) }
    var materialDropdown by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Нове оголошення") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subjects.isEmpty()) Text("Спершу опублікуйте матеріал дисципліни, щоб створювати для неї оголошення.")
                Box {
                    OutlinedButton(onClick = { dropdown = true }, enabled = !busy && subjects.isNotEmpty()) { Text(selected ?: "Оберіть дисципліну") }
                    DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
                        subjects.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { selected = name; selectedMaterial = null; dropdown = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = scope == "SUBJECT", onClick = { scope = "SUBJECT" }, enabled = !busy, label = { Text("Дисципліна") })
                    FilterChip(selected = scope == "MATERIAL", onClick = { scope = "MATERIAL" }, enabled = !busy, label = { Text("Матеріал") })
                }
                if (scope == "MATERIAL") Box {
                    OutlinedButton(onClick = { materialDropdown = true }, enabled = !busy && selected != null) {
                        Text(materials.firstOrNull { it.id == selectedMaterial }?.title ?: "Оберіть матеріал")
                    }
                    DropdownMenu(materialDropdown, { materialDropdown = false }) {
                        materials.filter { it.subject == selected }.forEach { item ->
                            DropdownMenuItem(text = { Text("${item.type}: ${item.title}") }, onClick = { selectedMaterial = item.id; materialDropdown = false })
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 500) text = it }, enabled = !busy,
                    label = { Text("Текст оголошення") }, minLines = 3, supportingText = { Text("${text.length}/500") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Скасувати") } },
        confirmButton = { Button(enabled = !busy && text.isNotBlank() && materials.any { it.subject == selected && (scope == "SUBJECT" || it.id == selectedMaterial) }, onClick = {
            val material = materials.firstOrNull { it.subject == selected && (scope == "SUBJECT" || it.id == selectedMaterial) } ?: return@Button
            busy = true; error = null
            AppRepository.publishAnnouncement(material.id, text, scope) { failure ->
                busy = false
                if (failure == null) dismiss() else error = failure
            }
        }) { Text(if (busy) "Публікуємо…" else "Опублікувати") } })
}
