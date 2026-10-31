package com.example.stefanykhub

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stefanykhub.data.AppRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
class Lab11FunctionalTest {
    @get:Rule(order = 0) val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 1) val evidence = object : TestWatcher() {
        override fun succeeded(description: Description) = screenshot(description.methodName + "_PASS")
        override fun failed(error: Throwable, description: Description) = screenshot(description.methodName + "_FAIL")
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "lab11")
        directory.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val context = instrumentation.targetContext
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StefanykLab11")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun waitText(text: String, substring: Boolean = false) {
        ui.waitUntil(25_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        val node = ui.onNodeWithText(text)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick()
    }
    private fun field(label: String) = ui.onNode(hasSetTextAction() and hasText(label))

    @Before fun openSignIn() {
        ui.waitUntil(25_000) { AppRepository.authInitialized.value }
        ui.runOnIdle {
            AppRepository.setDemoMode(ui.activity, true)
            AppRepository.logout()
        }
        waitText("Вхід у систему")
    }

    private fun login() {
        click("Студент")
        click("Увійти")
        waitText("Навчальні матеріали")
        waitText("Знайдено: 2 документів")
    }

    private fun search(query: String) {
        ui.onNodeWithContentDescription("Пошук").performClick()
        ui.onNode(hasSetTextAction() and hasContentDescription("Пошук")).performTextInput(query)
        ui.waitForIdle()
    }

    @Test fun TC001_valid_login() {
        login()
        ui.onNodeWithText("Олена Тестова").assertIsDisplayed()
    }

    @Test fun TC002_search_existing_material() {
        login(); search("SQL")
        ui.onNodeWithText("Знайдено: 1 документ").assertIsDisplayed()
        ui.onNodeWithText("Бази даних: таблиці та SQL").assertExists()
    }

    @Test fun TC003_course_filter() {
        login()
        ui.onNodeWithContentDescription("Меню фільтрів").performClick()
        click("2 курс")
        androidx.test.espresso.Espresso.pressBack()
        waitText("Знайдено: 0 документів")
        ui.onNodeWithContentDescription("Меню фільтрів").performClick()
        click("Скинути")
        androidx.test.espresso.Espresso.pressBack()
        waitText("Знайдено: 2 документів")
    }

    @Test fun TC004_open_english_pdf() {
        login(); search("Англійська")
        ui.onNodeWithText("Англійська для ІТ: спілкування в команді").performScrollTo().performClick()
        ui.onNodeWithText("📖 Читати матеріал").performClick()
        waitText("Сторінка 1 / 4")
        ui.waitUntil(25_000) { ui.onAllNodesWithContentDescription("Сторінка 1").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun TC005_progress_report() {
        login()
        ui.onNodeWithText("Загальний прогрес").assertIsDisplayed()
        ui.onNodeWithText("100% (2/2)").assertIsDisplayed()
    }

    @Test fun TC006_logout() {
        login()
        ui.onNodeWithContentDescription("Вийти з акаунту").performClick()
        waitText("Вхід у систему")
        ui.onNodeWithText("Навчальні матеріали").assertDoesNotExist()
    }

    @Test fun TC007_incorrect_password() {
        click("Студент")
        field("Пароль").performTextReplacement("WrongPassword42!")
        click("Увійти")
        waitText("Не вдалося підтвердити облікові дані.", substring = true)
        ui.onNodeWithText("Навчальні матеріали").assertDoesNotExist()
    }

    @Test fun TC008_empty_email() {
        field("Пароль").performTextInput("DemoPass123!")
        click("Увійти")
        ui.onNodeWithText("Введіть електронну пошту!").assertExists()
    }

    @Test fun TC009_empty_password() {
        field("Електронна пошта (@pnu.edu.ua)").performTextInput("student.demo@pnu.edu.ua")
        click("Увійти")
        ui.onNodeWithText("Введіть пароль!").assertExists()
    }

    @Test fun TC010_non_university_email() {
        field("Електронна пошта (@pnu.edu.ua)").performTextInput("student@gmail.com")
        field("Пароль").performTextInput("DemoPass123!")
        click("Увійти")
        ui.onNodeWithText("Пошта повинна закінчуватись на @pnu.edu.ua або @stefanyk.edu.ua").assertExists()
    }

    @Test fun TC011_weak_registration_password() {
        click("Реєстрація")
        field("Пароль").performScrollTo().performTextInput("abc")
        click("Зареєструватися")
        ui.onNodeWithText("Пароль має містити від 8 до 128 символів").assertExists()
        ui.onNodeWithText("Пароль має містити від 8 до 128 символів").performScrollTo()
        ui.onNodeWithText("Акаунт створено").assertDoesNotExist()
    }

    @Test fun TC012_search_no_results() {
        login(); search("zzzz-no-such-material-98765")
        ui.onNodeWithText("Знайдено: 0 документів").assertIsDisplayed()
        ui.onNodeWithText("Бази даних: таблиці та SQL").assertDoesNotExist()
        ui.onNodeWithText("Англійська для ІТ: спілкування в команді").assertDoesNotExist()
    }
}
