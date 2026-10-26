package com.example.stefanykhub.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.MaterialReaction
import com.example.stefanykhub.model.ReactionSummary
import com.example.stefanykhub.model.UserRole

@Composable
fun MaterialReactionsBar(materialId: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val user = AppRepository.currentUser.value ?: return
    DisposableEffect(user.id, materialId) {
        val stop = AppRepository.observeReactions(materialId)
        onDispose { stop() }
    }
    val loaded = AppRepository.materialReactions[materialId]
    val summary = loaded ?: ReactionSummary()
    val error = AppRepository.reactionErrors[materialId]
    var showHelp by remember { mutableStateOf(false) }
    val canReact = user.role == UserRole.STUDENT && loaded != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Відгуки студентів", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Що означають реакції?", modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MaterialReaction.entries.forEach { reaction ->
                val count = summary.counts[reaction] ?: 0
                val selected = summary.mine == reaction
                if (user.role == UserRole.STUDENT) {
                    FilterChip(
                        selected = selected,
                        onClick = { AppRepository.toggleReaction(materialId, reaction) },
                        enabled = canReact,
                        label = { Text("${reaction.emoji} $count") },
                        modifier = Modifier.semantics {
                            contentDescription = "${reaction.label}: ${reaction.explanation}. Кількість: $count.${if (selected) " Ваша реакція. Повторне натискання прибирає її." else ""}"
                        }
                    )
                } else {
                    SuggestionChip(
                        onClick = { showHelp = true },
                        label = { Text("${reaction.emoji} $count") },
                        modifier = Modifier.semantics { contentDescription = "${reaction.label}: $count" }
                    )
                }
            }
        }
        val message = when {
            error != null -> error
            loaded == null -> "Завантажуємо реакції…"
            summary.pending -> "Зміна очікує синхронізації"
            user.role != UserRole.STUDENT && summary.needsExplanation -> "🤯 ❓ Студентам потрібне додаткове пояснення"
            summary.mine != null -> "${summary.mine.emoji} Ваша реакція: ${summary.mine.label}"
            !compact && user.role == UserRole.STUDENT -> "Оберіть одну реакцію. Повторне натискання прибере її."
            else -> null
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Реакція — короткий відгук") },
            text = {
                Text(MaterialReaction.entries.joinToString("\n\n") { "${it.emoji} ${it.label} — ${it.explanation}." } +
                    "\n\nОдин студент — одна реакція на матеріал. Її можна змінити або прибрати. Закладка зберігає матеріал у вашому обраному окремо від відгуку.")
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Зрозуміло") } }
        )
    }
}
