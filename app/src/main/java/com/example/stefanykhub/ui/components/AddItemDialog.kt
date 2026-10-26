package com.example.stefanykhub.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.SubjectCatalog
import com.example.stefanykhub.ui.theme.LabBadgeBg
import com.example.stefanykhub.ui.theme.LabBadgeText
import com.example.stefanykhub.ui.theme.PrimaryContainerIndigo
import com.example.stefanykhub.ui.theme.PrimaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    initialItem: CatalogItem? = null,
    onDismissRequest: () -> Unit,
    onSaveItem: (
        title: String,
        type: String,
        subject: String,
        teacher: String,
        fileType: String,
        fileSize: String,
        semester: Int,
        course: String,
        tags: String,
        description: String,
        linkUrl: String?,
        fileUri: Uri?
    ) -> Unit,
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var type by remember { mutableStateOf(initialItem?.type ?: "Лабораторні") }
    var subject by remember { mutableStateOf(initialItem?.subject ?: SubjectCatalog.ALL_SUBJECTS.first()) }
    var teacher by remember { mutableStateOf(initialItem?.teacher ?: "Пікуляк М.В.") }
    var semester by remember { mutableIntStateOf(initialItem?.semester ?: 7) }
    var course by remember { mutableStateOf(initialItem?.course ?: "4 курс") }
    var fileType by remember { mutableStateOf(initialItem?.fileType ?: "PDF") }
    var fileSize by remember { mutableStateOf(initialItem?.fileSize ?: "2.5 MB") }
    var tags by remember { mutableStateOf(initialItem?.tags ?: "#Android #UIUX") }
    var description by remember { mutableStateOf(initialItem?.description ?: "Опис навчального матеріалу...") }
    var linkUrl by remember { mutableStateOf(initialItem?.linkUrl ?: "") }

    // Стан вибраного файлу з пристрою
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // Launcher для вибору файлу з пристрою ПК / смартфона
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        selectedFileName = name
                        if (title.isBlank()) {
                            title = name.substringBeforeLast(".")
                        }
                        // Визначення формату файлу
                        val ext = name.substringAfterLast(".", "PDF").uppercase()
                        fileType = if (ext in listOf("PDF", "DOC", "DOCX", "PPTX", "ZIP", "MP4")) ext else ext
                    }
                    if (sizeIndex != -1) {
                        val bytes = it.getLong(sizeIndex)
                        val mb = bytes / (1024.0 * 1024.0)
                        fileSize = String.format(java.util.Locale.US, "%.1f MB", if (mb > 0) mb else 1.5)
                    }
                }
            }
        }
    }

    var isSubjectDropdownExpanded by remember { mutableStateOf(false) }
    var isSemesterDropdownExpanded by remember { mutableStateOf(false) }
    var isCourseDropdownExpanded by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var linkUrlError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = if (initialItem == null) "Публікація нового матеріалу" else "Редагування матеріалу")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Блок вибору файлу з пристрою (комп'ютера або телефона)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, PrimaryIndigo, RoundedCornerShape(14.dp))
                        .clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                    color = PrimaryContainerIndigo
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Завантажити файл",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(32.dp)
                        )

                        Text(
                            text = if (selectedFileName != null) "✓ Файл обрано: $selectedFileName" else "📁 Клацніть, щоб обрати файл з ПК або телефона",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryIndigo
                        )

                        if (selectedFileName != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LabBadgeBg
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LabBadgeText, modifier = Modifier.size(14.dp))
                                    Text("Готово до збереження в базу ($fileSize)", style = MaterialTheme.typography.bodySmall, color = LabBadgeText)
                                }
                            }
                        }
                    }
                }

                // Назва матеріалу
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (isError) isError = false
                    },
                    label = { Text("Назва теми / лекції / роботи") },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )

                // Дисципліна із 58 предметів
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Дисципліна (58 предметів)") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { isSubjectDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = isSubjectDropdownExpanded,
                        onDismissRequest = { isSubjectDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 280.dp)
                    ) {
                        SubjectCatalog.ALL_SUBJECTS.forEach { itemSubject ->
                            DropdownMenuItem(
                                text = { Text(itemSubject, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    subject = itemSubject
                                    isSubjectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Вибір курсу та семестру
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Курс (1-4 курс, Магістратура)
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = course,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Курс") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { isCourseDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = isCourseDropdownExpanded,
                            onDismissRequest = { isCourseDropdownExpanded = false }
                        ) {
                            SubjectCatalog.ALL_COURSES.forEach { crs ->
                                DropdownMenuItem(
                                    text = { Text(crs) },
                                    onClick = {
                                        course = crs
                                        isCourseDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Семестр (1..11)
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = "$semester сем",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Семестр") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { isSemesterDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = isSemesterDropdownExpanded,
                            onDismissRequest = { isSemesterDropdownExpanded = false }
                        ) {
                            SubjectCatalog.ALL_SEMESTERS.forEach { sem ->
                                DropdownMenuItem(
                                    text = { Text("$sem семестр") },
                                    onClick = {
                                        semester = sem
                                        isSemesterDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Введення викладача
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Викладач / Автор") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Вибір типу матеріалу
                Text(
                    text = "Тип матеріалу:",
                    style = MaterialTheme.typography.labelMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Лекції", "Методички", "Лабораторні", "Практичні", "Література", "Відео/Аудіо").chunked(2).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chunk.forEach { itemType ->
                                FilterChip(
                                    selected = (type == itemType),
                                    onClick = { type = itemType },
                                    label = { Text(itemType, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Теги та Опис
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Теги (напр. #Compose #Android)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Короткий опис файлу") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = {
                        linkUrl = it
                        linkUrlError = null
                    },
                    label = { Text("Посилання на зовнішній ресурс (опціонально)") },
                    isError = linkUrlError != null,
                    supportingText = { linkUrlError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError || titleError != null) {
                    Text(
                        text = titleError ?: "Заповніть назву роботи!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    titleError = null
                    linkUrlError = null
                    isError = false

                    if (title.isBlank()) {
                        titleError = "Назва теми / роботи є обов'язковим полем!"
                        isError = true
                        return@Button
                    }

                    if (linkUrl.isNotBlank() && !linkUrl.startsWith("http://") && !linkUrl.startsWith("https://")) {
                        linkUrlError = "Посилання повинно починатися з http:// або https://"
                        return@Button
                    }

                    onSaveItem(
                        title.trim(),
                        type,
                        subject,
                        teacher.trim(),
                        fileType,
                        fileSize,
                        semester,
                        course,
                        tags.trim(),
                        description.trim(),
                        linkUrl.ifBlank { null },
                        selectedFileUri
                    )
                    onDismissRequest()
                }
            ) {
                Text("Опублікувати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Скасувати")
            }
        }
    )
}
