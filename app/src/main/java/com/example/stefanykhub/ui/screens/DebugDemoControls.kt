package com.example.stefanykhub.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.BuildConfig
import com.example.stefanykhub.data.AppRepository


@Composable
fun DebugDemoControls(enabled: Boolean, onCredentials: (String, String) -> Unit) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val demo = AppRepository.isDemoMode.value
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Локальне тестування", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = demo,
                    enabled = enabled,
                    onCheckedChange = { AppRepository.setDemoMode(context, it) }
                )
            }
            if (demo) {
                Text(
                    "Працює з Firebase Emulator Suite на вашому комп’ютері. Спочатку запустіть емулятори та створіть тестові дані за DEMO_SETUP.md.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Студент" to "student.demo@pnu.edu.ua",
                        "Викладач" to "teacher.demo@pnu.edu.ua",
                        "Адмін" to "admin.demo@pnu.edu.ua"
                    ).forEach { (label, email) ->
                        OutlinedButton(enabled = enabled, onClick = { onCredentials(email, "DemoPass123!") }) { Text(label) }
                    }
                }
                Text("Кнопка заповнює пошту й пароль. Для входу натисніть «Увійти».", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
