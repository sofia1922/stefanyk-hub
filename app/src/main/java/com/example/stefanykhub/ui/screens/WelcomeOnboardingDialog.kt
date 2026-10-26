package com.example.stefanykhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.ui.theme.PrimaryIndigo
import com.example.stefanykhub.ui.theme.TextPrimary
import com.example.stefanykhub.ui.theme.TextSecondary

@Composable
fun WelcomeOnboardingDialog(
    user: User,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Text("Розпочати роботу", fontWeight = FontWeight.Bold)
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Вітаємо у Stefanyk.Hub!",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Раді вітати вас, ${user.fullName} (${user.role.label})!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryIndigo
                )

                if (user.role == UserRole.STUDENT) {
                    Text(
                        text = "Курс: ${user.course}\nСпеціальність: ${user.specialty ?: "—"}\nГрупа: ${user.group ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "🚀 Ваша інтерактивна платформа для навчання готова. Шукайте потрібні матеріали, завантажуйте їх на свій пристрій та додавайте до Обраного!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                } else {
                    Text(
                        text = "Кафедра: ${user.department ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "📚 Вітаємо у кабінеті викладача! Ви можете публікувати навчальні документи, лекції, лабораторні та відстежувати їх завантаження студентами.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    )
}
