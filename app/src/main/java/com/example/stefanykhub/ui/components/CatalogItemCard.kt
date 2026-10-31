package com.example.stefanykhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.ui.theme.*

@Composable
fun CatalogItemCard(
    item: CatalogItem,
    onFavoriteToggle: (Int) -> Unit,
    onDownloadClick: (CatalogItem) -> Unit,
    onCardClick: ((CatalogItem) -> Unit)? = null,
    onReadClick: ((CatalogItem) -> Unit)? = null,
    onToggleCompleted: ((Int) -> Unit)? = null,
    onEditClick: ((CatalogItem) -> Unit)? = null,
    onDeleteClick: ((CatalogItem) -> Unit)? = null,
    showFavoriteAndDownload: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isStudent = AppRepository.currentUser.value?.role == UserRole.STUDENT
    val downloaded = AppRepository.isMaterialDownloaded(item.id)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onCardClick != null) Modifier.clickable { onCardClick(item) } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStudent && item.isCompleted) Color(0xFFF0FDF4) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                val (badgeBg, badgeText) = when (item.type) {
                    "Лабораторні", "Лабораторна" -> LabBadgeBg to LabBadgeText
                    "Лекції", "Лекція" -> LectureBadgeBg to LectureBadgeText
                    "Відео/Аудіо", "Відео" -> FavoriteBadgeBg to FavoriteBadgeText
                    else -> ManualBadgeBg to ManualBadgeText
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = item.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }


                    if (isStudent && onToggleCompleted != null && showFavoriteAndDownload) {
                        CompletionChip(item.isCompleted, onToggle = { onToggleCompleted(item.id) })
                    }
                }


                if (showFavoriteAndDownload) {
                    IconButton(
                        onClick = { onFavoriteToggle(item.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Додати в обране",
                            tint = if (item.isFavorite) FavoriteRed else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))


            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))


            Text(
                text = "Дисципліна: ${item.subject}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )


            Text(
                text = "Викладач: ${item.teacher} • ${item.date}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (item.tags.isNotBlank()) {
                Text(
                    text = item.tags,
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            MaterialReactionsBar(materialId = item.id)
            AnnouncementsPanel(subject = item.subject, materialId = item.id)
            MaterialAnalyticsButton(item)

            Spacer(modifier = Modifier.height(8.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.fileType} • ${item.fileSize}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onEditClick != null) {
                        IconButton(
                            onClick = { onEditClick(item) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редагувати",
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = { onDeleteClick(item) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Видалити",
                                tint = FavoriteRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (showFavoriteAndDownload) {
                        Button(
                            onClick = {
                                if (downloaded) (onReadClick ?: onCardClick)?.invoke(item)
                                else onDownloadClick(item)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryIndigo,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (downloaded) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (downloaded) "Читати" else "Завантажити",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
