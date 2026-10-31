package com.example.stefanykhub

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.model.User
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MaterialPublicationTest {
    @Test fun pdfWithGenericProviderMimeIsPublishedAndVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            AppRepository.initStorage(context)
            AppRepository.setDemoMode(context, true)
        }
        val signedIn = CountDownLatch(1)
        var login: Result<User>? = null
        instrumentation.runOnMainSync {
            AppRepository.loginUser("teacher.demo@pnu.edu.ua", "DemoPass123!") { login = it; signedIn.countDown() }
        }
        assertTrue("Teacher login timed out", signedIn.await(30, TimeUnit.SECONDS))
        login!!.getOrThrow()
        val app = FirebaseApp.getInstance("local-demo")
        val db = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)
        Tasks.await(db.collection("materials").get(), 20, TimeUnit.SECONDS)
        val folder = File(context.cacheDir, "demo_reader").apply { mkdirs() }
        val file = File(folder, "publication-regression.bin")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
            page.canvas.drawText("Publication regression PDF", 30f, 60f, Paint().apply { textSize = 18f })
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        assertEquals("application/octet-stream", context.contentResolver.getType(uri))
        var published: CatalogItem? = null
        val title = "Publication regression ${System.currentTimeMillis()}"
        try {
            val done = CountDownLatch(1)
            var outcome: Result<CatalogItem>? = null
            instrumentation.runOnMainSync {
                AppRepository.addMaterial(title, "Лабораторні", "Бази даних", "Олександр Тестовий",
                    "PDF", "1 KB", 3, "2 курс", fileUri = uri) { outcome = it; done.countDown() }

                AppRepository.materials.removeAll { it.title == title }
            }
            assertTrue("Publication timed out", done.await(40, TimeUnit.SECONDS))
            published = outcome!!.getOrThrow()
            val item = published!!
            val saved = Tasks.await(db.collection("materials").document(item.id.toString()).get(), 20, TimeUnit.SECONDS)
            assertEquals(title, saved.getString("title"))
            assertEquals("demo-teacher", saved.getString("teacherId"))
            assertTrue(item.linkUrl!!.startsWith("http"))
            val reference = storage.reference.child("materials/demo-teacher/${item.id}.pdf")
            assertEquals("application/pdf", Tasks.await(reference.metadata, 20, TimeUnit.SECONDS).contentType)
            val bytes = Tasks.await(reference.getBytes(1024 * 1024), 20, TimeUnit.SECONDS)
            assertTrue(String(bytes.take(5).toByteArray()).startsWith("%PDF-"))
            val connection = java.net.URL(item.linkUrl).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                assertEquals(200, connection.responseCode)
                connection.inputStream.use { assertEquals("%PDF-", String(it.readNBytes(5))) }
            } finally { connection.disconnect() }
            instrumentation.runOnMainSync {
                assertTrue("Published material is missing from the live list", AppRepository.materials.any { it.id == item.id })
            }
        } finally {
            val created = Tasks.await(db.collection("materials").whereEqualTo("title", title).get(), 20, TimeUnit.SECONDS)
            for (doc in created.documents) {
                val id = doc.getLong("id")!!
                Tasks.await(doc.reference.delete(), 20, TimeUnit.SECONDS)
                runCatching { Tasks.await(storage.reference.child("materials/demo-teacher/$id.pdf").delete(), 20, TimeUnit.SECONDS) }
            }
            file.delete()
        }
    }
}
