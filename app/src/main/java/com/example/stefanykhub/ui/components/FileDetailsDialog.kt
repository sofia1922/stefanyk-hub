package com.example.stefanykhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.stefanykhub.data.AppRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.ui.theme.*

@Composable
fun FileDetailsDialog(
    item: CatalogItem,
    onDismissRequest: () -> Unit,
    onFavoriteToggle: (Int) -> Unit,
    onDownloadClick: (CatalogItem) -> Unit,
    onOpenReaderClick: (() -> Unit)? = null,
    showFavoriteAndDownload: Boolean = true
) {
    val downloaded = AppRepository.isMaterialDownloaded(item.id)
    LaunchedEffect(item.id) { AppRepository.recordMaterialOpened(item.id) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Деталі навчального матеріалу",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Тип і Формат
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LectureBadgeBg
                    ) {
                        Text(
                            text = item.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LectureBadgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryContainerIndigo
                    ) {
                        Text(
                            text = "${item.fileType} • ${item.fileSize}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryContainerIndigo,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                HorizontalDivider()

                Text(
                    text = "📚 Дисципліна: ${item.subject}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Text(
                    text = "👨‍🏫 Викладач: ${item.teacher}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Text(
                    text = "📅 Дата завантаження: ${item.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Text(
                    text = "🎓 Курс: ${item.course} | Семестр: ${item.semester}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Text(
                    text = "📥 Всього завантажень: ${item.downloadCount}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo
                )

                if (item.tags.isNotBlank()) {
                    Text(
                        text = "🏷️ Теги: ${item.tags}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                MaterialReactionsBar(materialId = item.id)
                AnnouncementsPanel(subject = item.subject, materialId = item.id)

                val showDesc = item.description.isNotBlank() &&
                        item.description != "Опис навчального матеріалу..." &&
                        item.description != "Опис матеріалу..."

                if (showDesc) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📝 Опис:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }

                val showWebLink = !item.linkUrl.isNullOrBlank() &&
                        (item.linkUrl.startsWith("http://") || item.linkUrl.startsWith("https://"))

                if (showWebLink) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Зовнішнє посилання: ${item.linkUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryIndigo
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Головна кнопка "Читати матеріал онлайн"
                if (onOpenReaderClick != null) {
                    Button(
                        onClick = onOpenReaderClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (downloaded) "📖 Читати офлайн" else "📖 Читати матеріал", fontWeight = FontWeight.Bold)
                    }
                }

                // 2. Дві нижні кнопки (Тільки для студентів!): Завантажити + Обране
                if (showFavoriteAndDownload) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (downloaded) onOpenReaderClick?.invoke()
                                else {
                                    onDownloadClick(item)
                                    onDismissRequest()
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(if (downloaded) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (downloaded) "Читати" else "Завантажити", style = MaterialTheme.typography.labelMedium)
                        }

                        Surface(
                            onClick = { onFavoriteToggle(item.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isFavorite) FavoriteBadgeBg else ScreenBackground,
                            contentColor = if (item.isFavorite) FavoriteRed else TextPrimary,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (item.isFavorite) FavoriteRed else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (item.isFavorite) "В обраному" else "+ В обране",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Кнопка закрити вікно
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Закрити", color = TextSecondary)
                }
            }
        },
        dismissButton = null
    )
}
