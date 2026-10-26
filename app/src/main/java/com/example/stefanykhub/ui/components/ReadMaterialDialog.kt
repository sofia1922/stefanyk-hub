package com.example.stefanykhub.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadMaterialDialog(
    item: CatalogItem,
    onDismissRequest: () -> Unit,
    onFavoriteToggle: (Int) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onDownloadClick: (CatalogItem) -> Unit,
    showFavoriteAndDownload: Boolean = true
) {
    LaunchedEffect(item.id) { AppRepository.recordMaterialOpened(item.id) }
    val context = LocalContext.current
    val downloaded = AppRepository.isMaterialDownloaded(item.id)
    var selectedReaderTab by remember { mutableIntStateOf(0) } // 0 = Повний документ (PDF/Word), 1 = Конспект
    var fontSizeMultiplier by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                AnnouncementsPanel(subject = item.subject, materialId = item.id)
                // Верхня панель ридера
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ScreenBackground,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (downloaded) "Офлайн-читання" else "Читання матеріалу",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (selectedReaderTab == 1) {
                                    // Кнопки змінення шрифту тільки для конспекту
                                    IconButton(
                                        onClick = { if (fontSizeMultiplier > 0.8f) fontSizeMultiplier -= 0.1f },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                                    }

                                    IconButton(
                                        onClick = { if (fontSizeMultiplier < 1.4f) fontSizeMultiplier += 0.1f },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryIndigo)
                                    }
                                }

                                // Сердечко Обране (для студентів)
                                if (showFavoriteAndDownload) {
                                    IconButton(
                                        onClick = { onFavoriteToggle(item.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Обране",
                                            tint = if (item.isFavorite) FavoriteRed else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Кнопка закриття
                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Закрити", tint = TextPrimary)
                                }
                            }
                        }

                        // Перемикач вкладок "Оригінал документа (PDF/Word)" vs "Конспект"
                        MaterialReactionsBar(
                            materialId = item.id,
                            compact = true,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        TabRow(
                            selectedTabIndex = selectedReaderTab,
                            containerColor = Color.White,
                            contentColor = PrimaryIndigo
                        ) {
                            Tab(
                                selected = selectedReaderTab == 0,
                                onClick = { selectedReaderTab = 0 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("📄 Повний документ (${item.fileType})", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedReaderTab == 1,
                                onClick = { selectedReaderTab = 1 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Subject, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("📝 Текстовий конспект", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                    }
                }

                // Вміст залежно від обраної вкладки
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (selectedReaderTab == 0) {
                        // Вкладка 0: Вбудований рендерер оригінального файла
                        NativeFileContentRenderer(item = item)
                    } else {
                        // Вкладка 1: Розширений розбір та конспект матеріалу
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Картка заголовка
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = PrimaryContainerIndigo
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PrimaryIndigo
                                        ) {
                                            Text(
                                                text = item.type,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        Text(
                                            text = "${item.course} • ${item.semester} семестр",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }

                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = (18 * fontSizeMultiplier).sp),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    Text(
                                        text = "🎓 Дисципліна: ${item.subject}\n👨‍🏫 Викладач / Автор: ${item.teacher}\n📁 Формат: ${item.fileType} (${item.fileSize})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (13 * fontSizeMultiplier).sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Розділ 1: Анотація
                            Text(
                                text = "📌 1. Розширений зміст та анотація теми",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = (15 * fontSizeMultiplier).sp),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )

                            Text(
                                text = if (item.description.isNotBlank() && item.description != "Опис навчального матеріалу...") {
                                    "Вказівки викладача (${item.teacher}): ${item.description}\n\nНавчальний документ '${item.title}' опрацьовується для студентів ${item.course} (${item.semester} семестр) у рамках курсу '${item.subject}'."
                                } else {
                                    "Навчальний матеріал з дисципліни '${item.subject}' підготовлено викладачем ${item.teacher} для розбору теми '${item.title}'. Робота містить теоретичний блок, методичні рекомендації та практичні завдання для студентів ${item.course}."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (14 * fontSizeMultiplier).sp,
                                    lineHeight = (22 * fontSizeMultiplier).sp
                                ),
                                color = TextPrimary
                            )

                            HorizontalDivider()

                            // Розділ 2: Теоретичні концепції
                            Text(
                                text = "📚 2. Ключові теоретичні положення модуля",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = (15 * fontSizeMultiplier).sp),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )

                            Text(
                                text = """
                                    Під час вивчення теми "${item.title}" розглядаються такі ключові аспекти:
                                    1. Теоретична база та стандарти дисципліни "${item.subject}".
                                    2. Архітектурні підходи та методологія розв'язання практичних задач.
                                    3. Алгоритми та вимоги до виконання робіт типу "${item.type}".
                                    4. Критерії захисту та перевірки якості знань викладачем ${item.teacher}.
                                """.trimIndent(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (14 * fontSizeMultiplier).sp,
                                    lineHeight = (22 * fontSizeMultiplier).sp
                                ),
                                color = TextPrimary
                            )

                            // Блок з прикладом коду
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "💻 Методичний приклад коду / інструкція:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryIndigo
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = """
                                            // ПНУ • Дисципліна: ${item.subject}
                                            // Тема: ${item.title}
                                            // Автор: ${item.teacher}
                                            
                                            fun main() {
                                                println("Здача роботи '${item.title}'")
                                                println("Курс: ${item.course}, Семестр: ${item.semester}")
                                            }
                                        """.trimIndent(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = (12 * fontSizeMultiplier).sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }

                            // Додаткові джерела в інтернеті (Якщо викладач УВІВ посилання!)
                            val isWebUrl = !item.linkUrl.isNullOrBlank() && (item.linkUrl.startsWith("http://") || item.linkUrl.startsWith("https://"))
                            if (isWebUrl) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = PrimaryContainerIndigo
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "🌐 Додаткове джерело в інтернеті від викладача:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryIndigo
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.linkUrl.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PrimaryIndigo
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.linkUrl))
                                                    context.startActivity(intent)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Перейти за посиланням", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            // Теги
                            if (item.tags.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ScreenBackground
                                ) {
                                    Text(
                                        text = "🏷️ Теги теми: ${item.tags}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryIndigo,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Нижня панель дій ридера
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ScreenBackground,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка завантаження (для студентів)
                        if (showFavoriteAndDownload) {
                            OutlinedButton(
                                onClick = { onDownloadClick(item) },
                                enabled = !downloaded,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (downloaded) "Збережено офлайн" else "Завантажити", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Кнопка опрацьовано
                        if (AppRepository.currentUser.value?.role == UserRole.STUDENT) {
                        Button(
                            onClick = { onToggleCompleted(item.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isCompleted) LabBadgeBg else PrimaryIndigo
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (item.isCompleted) LabBadgeText else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (item.isCompleted) "Опрацьовано" else "Опрацювати",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.isCompleted) LabBadgeText else Color.White
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeFileContentRenderer(item: CatalogItem, modifier: Modifier = Modifier) {
    MaterialDocumentReader(item = item, modifier = modifier)
}

@Composable
fun DocumentWebViewer(
    item: CatalogItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var analyticsRecorded by remember(item.id) { mutableStateOf(false) }
    var pageFailed by remember(item.id) { mutableStateOf(false) }

    val rawUrl = AppRepository.getDownloadedFileUri(item.id)?.toString() ?: item.linkUrl.orEmpty()
    val isWebUrl = rawUrl.startsWith("http://") || rawUrl.startsWith("https://")

    // Формування чистого документа
    val documentHtml = remember(item) {
        val descText = item.description.ifBlank {
            "У даному матеріалі розглядаються основні методы, алгоритми та теоретичні положення з дисципліни '${item.subject}'."
        }.escapeHtml()
        val title = item.title.escapeHtml()
        val subject = item.subject.escapeHtml()
        val teacher = item.teacher.escapeHtml()
        val course = item.course.escapeHtml()
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
        <style>
          body { background-color: #525659; margin: 0; padding: 12px; font-family: 'Times New Roman', Times, serif; color: #000000; }
          .page { background: #ffffff; max-width: 800px; margin: 0 auto 16px auto; padding: 40px 48px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); box-sizing: border-box; font-size: 15px; line-height: 1.6; }
          h1 { font-size: 22px; font-weight: bold; text-align: center; margin: 0 0 24px 0; color: #000000; }
          h2 { font-size: 16px; font-weight: bold; margin-top: 20px; margin-bottom: 8px; color: #000000; }
          p { text-indent: 24px; margin: 8px 0; text-align: justify; }
          ol, ul { margin: 8px 0 8px 24px; padding: 0; }
          li { margin-bottom: 6px; }
          .code-box { background: #f8fafc; border: 1px solid #cbd5e1; padding: 12px; font-family: 'Courier New', monospace; font-size: 13px; margin: 12px 0; white-space: pre-wrap; }
        </style>
        </head>
        <body>
        <div class="page">
          <h1>$title</h1>

          <h2>1. Опис матеріалу та методичних вказівок.</h2>
          <p><b>1.1 Основні положення.</b> ${descText}</p>
          <p>Під час опрацювання теми з дисципліни <i>"$subject"</i> під керівництвом викладача <b>$teacher</b> розглядаються алгоритми та теоретичний модуль для курсу <i>$course</i> (${item.semester} семестр).</p>

          <h2>2. Алгоритм та послідовність виконання.</h2>
          <p>Формування теоретичних знань та практичних навичок згідно з навчальним плануванням:</p>
          <ol>
            <li>Ознайомлення з методичними вимогами та структурою теми "$title";</li>
            <li>Аналіз вхідних даних, алгоритмів та програмних засобів;</li>
            <li>Реалізація практичних завдань та перевірка результатів.</li>
          </ol>

          <div class="code-box">// Завдання з дисципліни: $subject
// Автор: $teacher
fun processAssignment() {
    println("Тема: $title")
}</div>

          <h2>3. Висновки та контроль.</h2>
          <p>Після завершення вивчення матеріалу студент повинен підготувати відповіді на контрольні питання та надати звіт на перевірку викладачу.</p>
        </div>
        </body>
        </html>
        """.trimIndent()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryContainerIndigo,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📄 ${item.title} (${item.fileType})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (isWebUrl) {
                    IconButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Відкрити зовні",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ScreenBackground, RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = isWebUrl
                        settings.domStorageEnabled = isWebUrl
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                if (request?.isForMainFrame == true) pageFailed = true
                            }
                            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                                if (request?.isForMainFrame == true) pageFailed = true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                if (item.fileType.equals("URL", true) && isWebUrl && !pageFailed && !analyticsRecorded) {
                                    AppRepository.recordMaterialActivity(item.id, "VIEW")
                                    analyticsRecorded = true
                                }
                            }
                        }

                        if (isWebUrl) {
                            val path = Uri.parse(rawUrl).path.orEmpty().lowercase()
                            val viewerUrl = if (path.endsWith(".pdf") || path.endsWith(".docx") || path.endsWith(".doc")) {
                                Uri.parse("https://docs.google.com/gview")
                                    .buildUpon()
                                    .appendQueryParameter("embedded", "true")
                                    .appendQueryParameter("url", rawUrl)
                                    .build()
                                    .toString()
                            } else {
                                rawUrl
                            }
                            loadUrl(viewerUrl)
                        } else {
                            loadDataWithBaseURL("https://pnu.edu.ua", documentHtml, "text/html", "UTF-8", null)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryIndigo)
                        Text(
                            text = "Відкриття документа ${item.fileType}...",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
