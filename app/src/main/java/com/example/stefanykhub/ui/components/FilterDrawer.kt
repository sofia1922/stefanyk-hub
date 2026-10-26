package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stefanykhub.model.SubjectCatalog
import com.example.stefanykhub.ui.theme.PrimaryIndigo
import com.example.stefanykhub.ui.theme.TextPrimary
import com.example.stefanykhub.ui.theme.TextSecondary

@Composable
fun FilterDrawerContent(
    selectedCourses: Set<String>,
    onCourseToggle: (String) -> Unit,
    selectedSemesters: Set<Int>,
    onSemesterToggle: (Int) -> Unit,
    selectedTypes: Set<String>,
    onTypeToggle: (String) -> Unit,
    selectedSubject: String?,
    onSubjectSelect: (String?) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var subjectQuery by remember { mutableStateOf("") }

    val filteredSubjects = remember(subjectQuery) {
        if (subjectQuery.isBlank()) {
            SubjectCatalog.ALL_SUBJECTS
        } else {
            SubjectCatalog.ALL_SUBJECTS.filter {
                it.contains(subjectQuery, ignoreCase = true)
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier.width(310.dp),
        drawerContainerColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            // Заголовок фільтрів
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Фільтри",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                TextButton(onClick = onResetFilters) {
                    Text(
                        text = "Скинути",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Блок 1: КУРС (1-4 курс, Магістратура)
                item {
                    Text(
                        text = "КУРС (1–4 КУРС, МАГІСТРАТУРА)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        SubjectCatalog.ALL_COURSES.chunked(2).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunk.forEach { crs ->
                                    FilterChip(
                                        selected = selectedCourses.contains(crs),
                                        onClick = { onCourseToggle(crs) },
                                        label = { Text(crs, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Блок 2: СЕМЕСТРИ (1 до 11)
                item {
                    Text(
                        text = "СЕМЕСТР (1 – 11)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        SubjectCatalog.ALL_SEMESTERS.chunked(3).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunk.forEach { sem ->
                                    FilterChip(
                                        selected = selectedSemesters.contains(sem),
                                        onClick = { onSemesterToggle(sem) },
                                        label = { Text("$sem сем") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Блок 3: ТИП МАТЕРІАЛУ
                item {
                    Text(
                        text = "ТИП МАТЕРІАЛУ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    listOf("Лекції", "Методички", "Лабораторні", "Практичні", "Література", "Відео/Аудіо").forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedTypes.contains(type),
                                onCheckedChange = { onTypeToggle(type) }
                            )
                            Text(
                                text = type,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Блок 4: ДИСЦИПЛІНА (58 ДИСЦИПЛІН)
                item {
                    Text(
                        text = "ДИСЦИПЛІНА (58 ДИСЦИПЛІН)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = subjectQuery,
                        onValueChange = { subjectQuery = it },
                        placeholder = { Text("Пошук дисципліни...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedSubject == null),
                                onClick = { onSubjectSelect(null) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedSubject == null),
                            onClick = { onSubjectSelect(null) }
                        )
                        Text(
                            text = "Всі дисципліни",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryIndigo
                        )
                    }
                }

                items(filteredSubjects) { subjectName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedSubject == subjectName),
                                onClick = { onSubjectSelect(subjectName) }
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedSubject == subjectName),
                            onClick = { onSubjectSelect(subjectName) }
                        )
                        Text(
                            text = subjectName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
