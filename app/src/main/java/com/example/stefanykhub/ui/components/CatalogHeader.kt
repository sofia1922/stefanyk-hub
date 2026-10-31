package com.example.stefanykhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.ui.theme.*

@Composable
fun CatalogHeader(
    user: User?,
    favoriteCount: Int,
    isFavoriteFilterActive: Boolean,
    onToggleFavoriteFilter: () -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleSearch: () -> Unit,
    onAvatarClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onToggleDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Меню фільтрів",
                            tint = TextPrimary
                        )
                    }

                    Text(
                        text = "Stefanyk.Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                }


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ScreenBackground)
                            .clickable { onToggleSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Пошук",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }


                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FavoriteBadgeBg)
                            .clickable { onLogoutClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Вийти з акаунту",
                            tint = FavoriteRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                if (user?.role != UserRole.TEACHER) {
                    Surface(
                        onClick = onToggleFavoriteFilter,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isFavoriteFilterActive) FavoriteRed else FavoriteBadgeBg,
                        contentColor = if (isFavoriteFilterActive) Color.White else FavoriteBadgeText
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isFavoriteFilterActive) Color.White else FavoriteRed
                            )
                            Text(
                                text = "Обране ($favoriteCount)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PrimaryContainerIndigo,
                        contentColor = PrimaryIndigo
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "👨‍🏫 Мої публікації",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }


                val userInitials = user?.fullName
                    ?.split(" ")
                    ?.mapNotNull { it.firstOrNull()?.toString() }
                    ?.take(2)
                    ?.joinToString("")
                    ?.ifEmpty { "У" } ?: "У"

                val displayName = when (user?.role) {
                    UserRole.ADMIN -> "Адміністратор"
                    UserRole.TEACHER -> if (user.fullName.length > 18) user.fullName.substringBefore(" ") else user.fullName
                    else -> if ((user?.fullName?.length ?: 0) > 18) user?.fullName?.substringBefore(" ") ?: "Студент" else user?.fullName ?: "Студент"
                }

                Surface(
                    onClick = onAvatarClick,
                    shape = RoundedCornerShape(16.dp),
                    color = ScreenBackground
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userInitials,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainerIndigo
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = user?.role?.label ?: "Студент",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
