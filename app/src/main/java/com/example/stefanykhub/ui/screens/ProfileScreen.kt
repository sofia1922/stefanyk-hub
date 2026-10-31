package com.example.stefanykhub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.ui.components.CatalogItemCard
import com.example.stefanykhub.ui.components.AccountSecurity
import com.example.stefanykhub.ui.components.TeacherDifficultyPanel
import com.example.stefanykhub.ui.components.AnnouncementsPanel
import com.example.stefanykhub.ui.components.SystemAlerts
import com.example.stefanykhub.ui.components.UniversityPulseDashboard
import com.example.stefanykhub.ui.components.FileDetailsDialog
import com.example.stefanykhub.ui.components.ReadMaterialDialog
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.materialFolder
import com.example.stefanykhub.model.materialFolderOrder
import com.example.stefanykhub.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val expandedFolders = remember(user.id) { mutableStateMapOf<String, Boolean>() }
    var newTeacherEmailInput by remember { mutableStateOf("") }
    var detailItemId by remember { mutableStateOf<Int?>(null) }
    var readingItemId by remember { mutableStateOf<Int?>(null) }

    fun downloadOrRead(item: CatalogItem) {
        if (AppRepository.isMaterialDownloaded(item.id)) {
            readingItemId = item.id
        } else scope.launch {
            AppRepository.downloadMaterial(context, item.id)
                .onSuccess { Toast.makeText(context, "Збережено. Натисніть «Читати» для відкриття.", Toast.LENGTH_SHORT).show() }
                .onFailure { error -> Toast.makeText(context, error.localizedMessage ?: "Не вдалося завантажити файл", Toast.LENGTH_LONG).show() }
        }
    }

    AppRepository.materials.firstOrNull { it.id == detailItemId }?.let { item ->
        FileDetailsDialog(
            item = item,
            onDismissRequest = { detailItemId = null },
            onFavoriteToggle = AppRepository::toggleFavorite,
            onDownloadClick = ::downloadOrRead,
            onOpenReaderClick = { detailItemId = null; readingItemId = item.id },
            showFavoriteAndDownload = user.role != UserRole.TEACHER
        )
    }
    AppRepository.materials.firstOrNull { it.id == readingItemId }?.let { item ->
        ReadMaterialDialog(
            item = item,
            onDismissRequest = { readingItemId = null },
            onFavoriteToggle = AppRepository::toggleFavorite,
            onToggleCompleted = AppRepository::toggleCompleted,
            onDownloadClick = ::downloadOrRead,
            showFavoriteAndDownload = user.role != UserRole.TEACHER
        )
    }

    val favoriteMaterials = remember {
        derivedStateOf {
            AppRepository.materials.filter { it.isFavorite }
        }
    }

    val downloadedMaterials = remember {
        derivedStateOf {
            AppRepository.materials.filter { AppRepository.isMaterialDownloaded(it.id) }
        }
    }

    val teacherPublishedMaterials = remember {
        derivedStateOf {
            when (user.role) {
                UserRole.ADMIN -> AppRepository.materials.toList()
                UserRole.TEACHER -> AppRepository.materials.filter { it.teacherId == user.id }
                UserRole.STUDENT -> emptyList()
            }
        }
    }

    val totalDownloadsCount = remember {
        derivedStateOf {
            teacherPublishedMaterials.value.sumOf { it.downloadCount }
        }
    }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = { Text("Особистий кабінет", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Вийти",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    val initials = user.fullName
                        .split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .ifEmpty { "У" }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainerIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryContainerIndigo
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (user.role) {
                            UserRole.ADMIN -> FavoriteBadgeBg
                            UserRole.TEACHER -> LabBadgeBg
                            UserRole.STUDENT -> LectureBadgeBg
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = when (user.role) {
                                UserRole.ADMIN -> "🛡️ Адміністратор"
                                UserRole.TEACHER -> "👨‍🏫 Викладач"
                                UserRole.STUDENT -> "🎓 Студент"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (user.role) {
                                UserRole.ADMIN -> FavoriteBadgeText
                                UserRole.TEACHER -> LabBadgeText
                                UserRole.STUDENT -> LectureBadgeText
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))


                    Text("Електронну пошту підтверджено • акаунт активовано", style = MaterialTheme.typography.bodySmall)
                    AccountSecurity(user, onSessionEnded = { AppRepository.logout(); onLogoutClick() })
                    Card(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        colors = CardDefaults.cardColors(containerColor = ScreenBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Email: ${user.email}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            if (user.role == UserRole.STUDENT) {
                                Text(
                                    text = "Курс: ${user.course}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Спеціальність: ${user.specialty ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Група: ${user.group ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Підрозділ: ${user.department ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Всього завантажень матеріалів: ${totalDownloadsCount.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))


            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.White
            ) {
                if (user.role == UserRole.ADMIN) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.People, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                                Text("База викладачів")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                                Text("Модерація (${teacherPublishedMaterials.value.size})")
                            }
                        }
                    )
                } else if (user.role == UserRole.STUDENT) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = FavoriteRed, modifier = Modifier.size(16.dp))
                                Text("Обрані (${favoriteMaterials.value.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                                Text("Завантажені (${downloadedMaterials.value.size})")
                            }
                        }
                    )
                } else {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                                Text("Мої публікації (${teacherPublishedMaterials.value.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📈 Складність") }
                    )
                }
                if (user.role != UserRole.STUDENT) {
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("📢 Оголошення") })
                }
                if (user.role == UserRole.ADMIN) {
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("🚨 Системні") })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("📊 Пульс") })
                }
            }


            if (user.role == UserRole.ADMIN && selectedTab == 4) {
                UniversityPulseDashboard()
            } else if (user.role == UserRole.ADMIN && selectedTab == 3) {
                SystemAlerts(manage = true)
            } else if (user.role != UserRole.STUDENT && selectedTab == 2) {
                AnnouncementsPanel(manage = true)
            } else if (user.role == UserRole.TEACHER && selectedTab == 1) {
                TeacherDifficultyPanel(teacherPublishedMaterials.value)
            } else if (user.role == UserRole.ADMIN && selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Додати пошту викладача в дозволену базу:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newTeacherEmailInput,
                                    onValueChange = { newTeacherEmailInput = it },
                                    placeholder = { Text("teacher@pnu.edu.ua") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        AppRepository.addTeacherEmail(newTeacherEmailInput) { result ->
                                            result.onSuccess {
                                                Toast.makeText(context, "Пошту '$newTeacherEmailInput' додано до дозволених викладачів.", Toast.LENGTH_SHORT).show()
                                                newTeacherEmailInput = ""
                                            }.onFailure { error ->
                                                Toast.makeText(context, error.localizedMessage ?: "Не вдалося додати пошту.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Дозволені пошти викладачів у базі:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AppRepository.allowedTeacherEmails) { emailItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = emailItem,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )

                                    IconButton(
                                        onClick = {
                                            AppRepository.removeTeacherEmail(emailItem) { result ->
                                                result.onSuccess {
                                                    Toast.makeText(context, "Дозвіл для '$emailItem' відкликано.", Toast.LENGTH_SHORT).show()
                                                }.onFailure { error ->
                                                    Toast.makeText(context, error.localizedMessage ?: "Не вдалося відкликати дозвіл.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Видалити",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {

                val currentList = if (user.role == UserRole.STUDENT) {
                    if (selectedTab == 0) favoriteMaterials.value else downloadedMaterials.value
                } else if (user.role == UserRole.ADMIN) {
                    teacherPublishedMaterials.value
                } else {
                    if (selectedTab == 0) teacherPublishedMaterials.value else favoriteMaterials.value
                }

                if (currentList.isEmpty() && user.role != UserRole.TEACHER) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Тут поки немає матеріалів.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val teacherGroups = if (user.role == UserRole.ADMIN) {
                            currentList.groupBy { material ->
                                material.teacherId.takeIf { it.isNotBlank() }?.let { "uid:$it" }
                                    ?: "legacy:${material.teacher.trim().ifBlank { "Невідомий викладач" }}"
                            }.toList().sortedWith(compareBy<Pair<String, List<CatalogItem>>> {
                                it.second.first().teacher.trim().lowercase()
                            }.thenBy { it.first })
                        } else listOf("self" to currentList)
                        teacherGroups.forEach { (teacherKey, teacherMaterials) ->
                            val teacherStateKey = "teacher:$teacherKey"
                            if (user.role == UserRole.ADMIN) item(key = teacherStateKey) {
                                val teacherName = teacherMaterials.first().teacher.trim().ifBlank { "Невідомий викладач" }
                                MaterialFolderHeader(
                                    title = teacherName,
                                    count = teacherMaterials.size,
                                    expanded = expandedFolders[teacherStateKey] == true,
                                    onClick = { expandedFolders[teacherStateKey] = expandedFolders[teacherStateKey] != true }
                                )
                            }
                            if (user.role != UserRole.ADMIN || expandedFolders[teacherStateKey] == true) {
                        val folders = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) {
                            val grouped = teacherMaterials.groupBy { materialFolder(it.type) }
                            materialFolderOrder.map { it to grouped[it].orEmpty() }
                        } else listOf("" to teacherMaterials)
                        folders.forEach { (folder, folderItems) ->
                            val folderKey = "$teacherStateKey/type:$folder"
                            if (folder.isNotEmpty()) item(key = folderKey) {
                                MaterialFolderHeader(
                                    title = folder,
                                    count = folderItems.size,
                                    expanded = expandedFolders[folderKey] == true,
                                    onClick = { expandedFolders[folderKey] = expandedFolders[folderKey] != true },
                                    modifier = Modifier.padding(start = if (user.role == UserRole.ADMIN) 16.dp else 0.dp)
                                )
                            }
                            if (folder.isEmpty() || expandedFolders[folderKey] == true) items(
                                items = folderItems,
                                key = { item -> item.id }
                            ) { item ->
                            CatalogItemCard(
                                item = item,
                                modifier = Modifier.padding(start = if (user.role == UserRole.ADMIN) 24.dp else 0.dp),
                                onCardClick = { detailItemId = it.id },
                                onReadClick = { readingItemId = it.id },
                                onToggleCompleted = if (user.role == UserRole.STUDENT) {
                                    { id -> AppRepository.toggleCompleted(id) }
                                } else null,
                                onFavoriteToggle = { id -> AppRepository.toggleFavorite(id) },
                                onDownloadClick = ::downloadOrRead,
                                onEditClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { } } else null,
                                onDeleteClick = if (user.role == UserRole.ADMIN || user.role == UserRole.TEACHER) { {
                                    AppRepository.deleteMaterial(it.id)
                                    Toast.makeText(context, "Матеріал видалено з бази!", Toast.LENGTH_SHORT).show()
                                } } else null
                            )
                            }
                            if (folder.isNotEmpty() && expandedFolders[folderKey] == true && folderItems.isEmpty()) item(key = "empty:$folderKey") {
                                Text("У цій папці поки немає матеріалів", modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialFolderHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📁 $title ($count)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (expanded) "Згорнути ▴" else "Відкрити ▾", style = MaterialTheme.typography.labelMedium)
        }
    }
}
