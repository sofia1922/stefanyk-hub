package com.example.stefanykhub.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Xml
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.CatalogItem
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipInputStream

private sealed interface DocumentState {
    data object Loading : DocumentState
    data class Pdf(val session: PdfSession) : DocumentState
    data class Text(val text: String) : DocumentState
    data class Error(val message: String) : DocumentState
}

/** Only visible pages are rendered; opening a large PDF doesn't allocate every page. */
private class PdfSession(context: android.content.Context, uri: Uri) : AutoCloseable {
    private val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Не вдалося відкрити документ.")
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) {
        descriptor.close()
        throw error
    }
    val pageCount = renderer.pageCount
    private var closed = false
    @Synchronized fun page(index: Int): Bitmap {
        check(!closed) { "Документ уже закрито." }
        return renderer.openPage(index).use { page ->
            val width = (page.width * 2).coerceIn(1, 1600)
            val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 4096)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            catch (error: Exception) { bitmap.recycle(); throw error }
            bitmap
        }
    }
    @Synchronized override fun close() {
        if (!closed) { closed = true; renderer.close() }
    }
}

@Composable
fun MaterialDocumentReader(item: CatalogItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AppRepository.analyticsWriteError.value?.let {
        LaunchedEffect(it) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    var attempt by remember(item.id) { mutableIntStateOf(0) }
    var localUri by remember(item.id, item.linkUrl) { mutableStateOf<Uri?>(null) }
    val downloaded = AppRepository.isMaterialDownloaded(item.id)
    var viewRecorded by remember(item.id) { mutableStateOf(false) }
    if (item.fileType.equals("URL", true)) {
        DocumentWebViewer(item, modifier)
        return
    }
    val state by produceState<DocumentState>(DocumentState.Loading, item.id, item.linkUrl, downloaded, attempt) {
        value = DocumentState.Loading
        var session: PdfSession? = null
        try {
            val uri = AppRepository.prepareMaterialForReading(context, item).getOrThrow()
            localUri = uri
            when (item.fileType.uppercase()) {
                "PDF" -> {
                    withContext(Dispatchers.IO + NonCancellable) { session = PdfSession(context, uri) }
                    value = DocumentState.Pdf(checkNotNull(session))
                    if (!viewRecorded && checkNotNull(session).pageCount > 0) {
                        AppRepository.recordMaterialActivity(item.id, "VIEW"); viewRecorded = true
                    }
                    awaitCancellation()
                }
                "TXT", "TEXT", "DOCX" -> {
                    val text = withContext(Dispatchers.IO) { readDocumentText(context, uri, item.fileType) }
                    value = DocumentState.Text(text)
                    if (!viewRecorded) { AppRepository.recordMaterialActivity(item.id, "VIEW"); viewRecorded = true }
                }
                else -> value = DocumentState.Error("Формат ${item.fileType} можна відкрити в іншій програмі на пристрої.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            value = DocumentState.Error(error.localizedMessage ?: "Не вдалося відкрити документ.")
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { session?.close() }
        }
    }
    when (val document = state) {
        DocumentState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Відкриваємо документ…")
            }
        }
        is DocumentState.Pdf -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(document.session.pageCount, key = { it }) { index ->
                PdfPage(document.session, index)
            }
        }
        is DocumentState.Text -> Text(
            document.text,
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        )
        is DocumentState.Error -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(document.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = { attempt++ }) { Text("Спробувати ще") }
            localUri?.let { uri ->
                OutlinedButton(onClick = {
                    runCatching {
                        val shareUri = if (uri.scheme == "file") FileProvider.getUriForFile(
                            context, "${context.packageName}.files", File(checkNotNull(uri.path))
                        ) else uri
                        val mime = android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(item.fileType.lowercase()) ?: "application/octet-stream"
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(shareUri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { Toast.makeText(context, "На пристрої немає програми для відкриття цього формату.", Toast.LENGTH_LONG).show() }
                }) { Text("Відкрити іншою програмою") }
            }
        }
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int) {
    val bitmap by produceState<Result<Bitmap>?>(null, session, index) {
        value = withContext(Dispatchers.IO) { runCatching { session.page(index) } }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Сторінка ${index + 1} / ${session.pageCount}", style = MaterialTheme.typography.labelSmall)
        val result = bitmap
        when {
            result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            result.isFailure -> Text("Не вдалося відобразити сторінку ${index + 1}.")
            else -> Image(
                result.getOrThrow().asImageBitmap(), contentDescription = "Сторінка ${index + 1}",
                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
            )
        }
    }
}

private fun readDocumentText(context: android.content.Context, uri: Uri, type: String): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("Не вдалося відкрити файл.")
    val maxBytes = 8 * 1024 * 1024
    val bytes = input.use { stream ->
        if (type.equals("DOCX", true)) {
            ZipInputStream(stream).use zipUse@{ zip ->
                while (true) {
                    val entry = zip.nextEntry ?: error("У DOCX не знайдено текст документа.")
                    if (entry.name == "word/document.xml") {
                        return@zipUse readBoundedText(zip, maxBytes)
                    }
                }
                @Suppress("UNREACHABLE_CODE") byteArrayOf()
            }
        } else {
            readBoundedText(stream, maxBytes)
        }
    }
    if (!type.equals("DOCX", true)) return bytes.toString(Charsets.UTF_8).ifBlank { "Документ порожній." }
    val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
    return buildString {
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = true
                    "tab" -> append('\t')
                    "br" -> append('\n')
                }
                XmlPullParser.TEXT -> if (inText) append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> append('\n')
                }
            }
            parser.next()
        }
    }.ifBlank { "У документі немає тексту." }
}

private fun readBoundedText(input: java.io.InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        check(output.size() + read <= limit) { "Текст документа надто великий." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
