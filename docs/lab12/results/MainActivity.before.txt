package com.example.stefanykhub

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.ui.components.*
import com.example.stefanykhub.ui.screens.AuthScreen
import com.example.stefanykhub.ui.screens.ProfileScreen
import com.example.stefanykhub.ui.screens.WelcomeOnboardingDialog
import com.example.stefanykhub.ui.theme.PrimaryIndigo
import com.example.stefanykhub.ui.theme.ScreenBackground
import com.example.stefanykhub.ui.theme.StefanykHubTheme
import com.example.stefanykhub.ui.theme.TextPrimary
import com.example.stefanykhub.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

enum class ScreenState {
    AUTH,
    CATALOG,
    PROFILE
}

private const val CATALOG_PAGE_SIZE = 10

class MainActivity : ComponentActivity() {
    private var presenceJob: kotlinx.coroutines.Job? = null
    override fun onStart() {
        super.onStart()
        presenceJob = lifecycleScope.launch {
            while (true) {
                AppRepository.sendPresenceHeartbeat()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    override fun onStop() {
        presenceJob?.cancel()
        presenceJob = null
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepository.initStorage(this)
        enableEdgeToEdge()
        setContent {
            StefanykHubTheme {
                MainAppEntry()
            }
        }
    }
}

@Composable
fun MainAppEntry() {
    var screenState by remember { mutableStateOf(ScreenState.AUTH) }
    var welcomeUser by remember { mutableStateOf<User?>(null) }

    val user = AppRepository.currentUser.value

    if (!AppRepository.authInitialized.value) {
        Surface(modifier = Modifier.fillMaxSize(), color = ScreenBackground) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        }
        return
    }

    LaunchedEffect(user) {
        if (user != null) AppRepository.sendPresenceHeartbeat()
        if (user != null && screenState == ScreenState.AUTH) screenState = ScreenState.CATALOG
    }

    Column(Modifier.fillMaxSize()) {
        if (user != null) {
            Column(Modifier.statusBarsPadding()) { SystemAlerts() }
        }
        Box(Modifier.weight(1f).consumeWindowInsets(if (user != null) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0))) {
    when (screenState) {
        ScreenState.AUTH -> {
            AuthScreen(
                onAuthSuccess = { authUser, isNewUser ->
                    if (isNewUser) {
                        welcomeUser = authUser
                    }
                    screenState = ScreenState.CATALOG
                }
            )
        }

        ScreenState.CATALOG -> {
            if (user != null) {
                EducationalCatalogScreen(
                    user = user,
                    onOpenProfile = { screenState = ScreenState.PROFILE }
                )
            } else {
                screenState = ScreenState.AUTH
            }
        }

        ScreenState.PROFILE -> {
            if (user != null) {
                ProfileScreen(
                    user = user,
                    onBackClick = { screenState = ScreenState.CATALOG },
                    onLogoutClick = {
                        AppRepository.logout()
                        screenState = ScreenState.AUTH
                    }
                )
            } else {
                screenState = ScreenState.AUTH
            }
        }
    }

        }
    }


    welcomeUser?.let { newUser ->
        WelcomeOnboardingDialog(
            user = newUser,
            onDismiss = { welcomeUser = null }
        )
    }
}

@Composable
fun EducationalCatalogScreen(
    user: User,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }


    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isFavoriteFilterActive by remember { mutableStateOf(false) }

    var selectedCourses by remember { mutableStateOf(setOf("1 курс", "2 курс", "3 курс", "4 курс", "Магістратура")) }
    var selectedSemesters by remember { mutableStateOf((1..11).toSet()) }
    var selectedTypes by remember { mutableStateOf(setOf("Лекції", "Методички", "Лабораторні", "Практичні", "Література", "Відео/Аудіо")) }
    var selectedSubject by remember { mutableStateOf<String?>(null) }


    var isAddDialogOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<CatalogItem?>(null) }
    var selectedDetailItemId by remember { mutableStateOf<Int?>(null) }
    var readingItemId by remember { mutableStateOf<Int?>(null) }
    var itemToDeleteState by remember { mutableStateOf<CatalogItem?>(null) }
    var itemToDownloadState by remember { mutableStateOf<CatalogItem?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }

    val materials = AppRepository.materials
    val favoriteCount = materials.count { it.isFavorite }

    val isTeacher = user.role == UserRole.TEACHER


    val query = searchQuery.trim().lowercase()
    val filteredItems = materials.filter { item ->

        val matchesTeacherOwnership = !isTeacher || item.teacherId == user.id

        val matchesFavorite = !isFavoriteFilterActive || item.isFavorite

        val matchesSearch = query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.type.lowercase().contains(query) ||
                item.subject.lowercase().contains(query) ||
                item.teacher.lowercase().contains(query) ||
                item.tags.lowercase().contains(query) ||
                item.description.lowercase().contains(query)

        val matchesCourse = selectedCourses.contains(item.course)
        val matchesSemester = selectedSemesters.contains(item.semester)

        val itemTypePlural = when (item.type) {
            "Лекція" -> "Лекції"
            "Лабораторна" -> "Лабораторні"
            else -> item.type
        }
        val matchesType = selectedTypes.contains(itemTypePlural) || selectedTypes.contains(item.type)

        val matchesSubject = selectedSubject == null || item.subject == selectedSubject

        matchesTeacherOwnership && matchesFavorite && matchesSearch && matchesCourse && matchesSemester && matchesType && matchesSubject
    }

    val totalPages = ((filteredItems.size + CATALOG_PAGE_SIZE - 1) / CATALOG_PAGE_SIZE).coerceAtLeast(1)
    LaunchedEffect(query, selectedCourses, selectedSemesters, selectedTypes, selectedSubject, isFavoriteFilterActive) {
        currentPage = 1
    }
    LaunchedEffect(totalPages) {
        currentPage = currentPage.coerceIn(1, totalPages)
    }
    val pagedItems = filteredItems
        .drop((currentPage - 1) * CATALOG_PAGE_SIZE)
        .take(CATALOG_PAGE_SIZE)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FilterDrawerContent(
                selectedCourses = selectedCourses,
                onCourseToggle = { crs ->
                    selectedCourses = if (selectedCourses.contains(crs)) {
                        selectedCourses - crs
                    } else {
                        selectedCourses + crs
                    }
                },
                selectedSemesters = selectedSemesters,
                onSemesterToggle = { sem ->
                    selectedSemesters = if (selectedSemesters.contains(sem)) {
                        selectedSemesters - sem
                    } else {
                        selectedSemesters + sem
                    }
                },
                selectedTypes = selectedTypes,
                onTypeToggle = { type ->
                    selectedTypes = if (selectedTypes.contains(type)) {
                        selectedTypes - type
                    } else {
                        selectedTypes + type
                    }
                },
                selectedSubject = selectedSubject,
                onSubjectSelect = { selectedSubject = it },
                onResetFilters = {
                    selectedCourses = setOf("1 курс", "2 курс", "3 курс", "4 курс", "Магістратура")
                    selectedSemesters = (1..11).toSet()
                    selectedTypes = setOf("Лекції", "Методички", "Лабораторні", "Практичні", "Література", "Відео/Аудіо")
                    selectedSubject = null
                    isFavoriteFilterActive = false
                    searchQuery = ""
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ScreenBackground,
            topBar = {
                CatalogHeader(
                    user = user,
                    favoriteCount = favoriteCount,
                    isFavoriteFilterActive = isFavoriteFilterActive,
                    onToggleFavoriteFilter = { isFavoriteFilterActive = !isFavoriteFilterActive },
                    onToggleDrawer = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onToggleSearch = { isSearchVisible = !isSearchVisible },
                    onAvatarClick = onOpenProfile,
                    onLogoutClick = {
                        AppRepository.logout()
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                AnimatedVisibility(visible = isSearchVisible) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Пошук за назвою, викладачем, дисципліною...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Пошук") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистити")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }


                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = selectedSubject ?: "Навчальні матеріали",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Знайдено: ${filteredItems.size} ${if (filteredItems.size == 1) "документ" else "документів"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }


                if (user.role == UserRole.STUDENT) {
                    AnnouncementsPanel(subject = selectedSubject)
                    StudentStatsHeader(selectedSubject = selectedSubject)
                }


                val configuration = LocalConfiguration.current
                val isWideScreen = configuration.screenWidthDp >= 600

                if (isWideScreen) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(
                            items = pagedItems,
                            key = { item -> item.id }
                        ) { item ->
                            CatalogItemCard(
                                item = item,
                                onCardClick = { selectedDetailItemId = it.id },
                                onReadClick = { readingItemId = it.id },
                                onFavoriteToggle = { id -> AppRepository.toggleFavorite(id) },
                                onToggleCompleted = { id -> AppRepository.toggleCompleted(id) },
                                onDownloadClick = { itemToDownloadState = it },
                                onEditClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { editingItem = it } } else null,
                                onDeleteClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { itemToDeleteState = it } } else null,
                                showFavoriteAndDownload = !isTeacher
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(
                            items = pagedItems,
                            key = { item -> item.id }
                        ) { item ->
                            CatalogItemCard(
                                item = item,
                                onCardClick = { selectedDetailItemId = it.id },
                                onReadClick = { readingItemId = it.id },
                                onFavoriteToggle = { id -> AppRepository.toggleFavorite(id) },
                                onToggleCompleted = { id -> AppRepository.toggleCompleted(id) },
                                onDownloadClick = { itemToDownloadState = it },
                                onEditClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { editingItem = it } } else null,
                                onDeleteClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { itemToDeleteState = it } } else null,
                                showFavoriteAndDownload = !isTeacher
                            )
                        }
                    }
                }


                PaginationAndActionsBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPageChange = { currentPage = it.coerceIn(1, totalPages) },
                    onAddMaterialClick = if (user.role == UserRole.TEACHER || user.role == UserRole.ADMIN) { { isAddDialogOpen = true } } else null
                )
            }


            val selectedDetailItem = materials.find { it.id == selectedDetailItemId }
            selectedDetailItem?.let { item ->
                FileDetailsDialog(
                    item = item,
                    onDismissRequest = { selectedDetailItemId = null },
                    onFavoriteToggle = { id -> AppRepository.toggleFavorite(id) },
                    onDownloadClick = { itemToDownloadState = it },
                    onOpenReaderClick = {
                        selectedDetailItemId = null
                        readingItemId = item.id
                    },
                    showFavoriteAndDownload = !isTeacher
                )
            }


            val readingItem = materials.find { it.id == readingItemId }
            readingItem?.let { item ->
                ReadMaterialDialog(
                    item = item,
                    onDismissRequest = { readingItemId = null },
                    onFavoriteToggle = { id -> AppRepository.toggleFavorite(id) },
                    onToggleCompleted = { id -> AppRepository.toggleCompleted(id) },
                    onDownloadClick = { itemToDownloadState = it },
                    showFavoriteAndDownload = !isTeacher
                )
            }


            itemToDownloadState?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemToDownloadState = null },
                    title = { Text("Завантажити файл?") },
                    text = { Text("Ви дійсно бажаєте завантажити файл '${item.title}.${item.fileType.lowercase()}' (${item.fileSize}) на ваш пристрій для офлайн-перегляду?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (isDownloading) return@Button
                                isDownloading = true
                                scope.launch {
                                    val result = AppRepository.downloadMaterial(context, item.id)
                                    result
                                        .onSuccess {
                                            Toast.makeText(context, "Файл '${item.title}' збережено для офлайн-перегляду", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(context, error.localizedMessage ?: "Не вдалося завантажити файл", Toast.LENGTH_LONG).show()
                                        }
                                    isDownloading = false
                                    if (result.isSuccess) itemToDownloadState = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                        ) {
                            Text(if (isDownloading) "Завантаження…" else "Завантажити")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDownloadState = null }) {
                            Text("Скасувати")
                        }
                    }
                )
            }


            if (isAddDialogOpen) {
                AddItemDialog(
                    onDismissRequest = { isAddDialogOpen = false },
                    onSaveItem = { title, type, subject, teacher, fileType, fileSize, semester, course, tags, description, linkUrl, fileUri, complete ->
                        AppRepository.addMaterial(
                            title = title,
                            type = type,
                            subject = subject,
                            teacherName = teacher.ifBlank { user.fullName },
                            fileType = fileType,
                            fileSize = fileSize,
                            semester = semester,
                            course = course,
                            tags = tags,
                            description = description,
                            linkUrl = linkUrl,
                            fileUri = fileUri
                        ) { result ->
                            result.onSuccess {
                                searchQuery = ""
                                isFavoriteFilterActive = false
                                selectedSubject = null
                                selectedCourses = selectedCourses + course
                                selectedSemesters = selectedSemesters + semester
                                selectedTypes = selectedTypes + type
                                currentPage = 1
                                Toast.makeText(context, "Опубліковано матеріал з дисципліни '$subject'", Toast.LENGTH_LONG).show()
                            }.onFailure { error ->
                                Toast.makeText(context, error.localizedMessage ?: "Не вдалося опублікувати матеріал", Toast.LENGTH_LONG).show()
                            }
                            complete(result)
                        }
                    }
                )
            }


            editingItem?.let { item ->
                AddItemDialog(
                    initialItem = item,
                    onDismissRequest = { editingItem = null },
                    onSaveItem = { title, type, subject, teacher, fileType, fileSize, semester, course, tags, description, linkUrl, fileUri, complete ->
                        AppRepository.updateMaterial(
                            id = item.id,
                            title = title,
                            type = type,
                            subject = subject,
                            teacherName = teacher,
                            fileType = fileType,
                            fileSize = fileSize,
                            semester = semester,
                            course = course,
                            tags = tags,
                            description = description,
                            linkUrl = linkUrl,
                            fileUri = fileUri
                        ) { result ->
                            result.onSuccess { Toast.makeText(context, "Зміни збережено", Toast.LENGTH_SHORT).show() }
                                .onFailure { error -> Toast.makeText(context, error.localizedMessage ?: "Не вдалося зберегти зміни", Toast.LENGTH_LONG).show() }
                            complete(result)
                        }
                    }
                )
            }


            itemToDeleteState?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemToDeleteState = null },
                    title = { Text("Видалити матеріал?") },
                    text = { Text("Ви дійсно бажаєте видалити '${item.title}' з бази даних?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                AppRepository.deleteMaterial(item.id)
                                itemToDeleteState = null
                                Toast.makeText(context, "Матеріал видалено", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Видалити")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDeleteState = null }) {
                            Text("Скасувати")
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CatalogPreview() {
    StefanykHubTheme {
        MainAppEntry()
    }
}
