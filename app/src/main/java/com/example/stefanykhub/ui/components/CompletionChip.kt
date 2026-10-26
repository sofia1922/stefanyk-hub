package com.example.stefanykhub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CompletionChip(completed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = completed,
        onClick = onToggle,
        label = { Text(if (completed) "Опрацьовано" else "Опрацювати") },
        leadingIcon = if (completed) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
        modifier = modifier
    )
}
