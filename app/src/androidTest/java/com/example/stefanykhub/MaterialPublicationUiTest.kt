package com.example.stefanykhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stefanykhub.data.AppRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MaterialPublicationUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun publishingClearsSearchAndShowsNewMaterial() {
        ui.waitUntil(20_000) { AppRepository.authInitialized.value }
        val ready = CountDownLatch(1)
        var error: Throwable? = null
        ui.runOnIdle {
            AppRepository.setDemoMode(ui.activity, true)
            AppRepository.loginUser("teacher.demo@pnu.edu.ua", "DemoPass123!") {
                error = it.exceptionOrNull(); ready.countDown()
            }
        }
        check(ready.await(25, TimeUnit.SECONDS))
        error?.let { throw it }
        ui.waitUntil(20_000) { ui.onAllNodesWithText("Навчальні матеріали").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Пошук").performClick()
        ui.onNode(hasSetTextAction() and hasContentDescription("Пошук")).performTextInput("publication-no-match")
        ui.onNodeWithText("Знайдено: 0 документів").assertExists()
        ui.onNodeWithText("Додати матеріал").performClick()
        ui.onNodeWithText("Опублікувати").performClick()
        ui.onNodeWithText("Публікація нового матеріалу").assertExists()
        val title = "UI publication regression ${System.currentTimeMillis()}"
        val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance("local-demo"))
        try {
            ui.onNode(hasSetTextAction() and hasText("Назва теми / лекції / роботи"))
                .performScrollTo().performTextInput(title)
            ui.onNodeWithText("Опублікувати").performClick()
            ui.waitUntil(25_000) { ui.onAllNodesWithText("Публікація нового матеріалу").fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithText(title).assertExists()
            ui.onNodeWithText("Знайдено: 3 документів").assertExists()
        } finally {
            val rows = Tasks.await(db.collection("materials").whereEqualTo("title", title).get(), 20, TimeUnit.SECONDS)
            for (doc in rows.documents) Tasks.await(doc.reference.delete(), 20, TimeUnit.SECONDS)
        }
    }
}
