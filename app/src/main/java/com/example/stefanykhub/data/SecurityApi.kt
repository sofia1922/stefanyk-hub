package com.example.stefanykhub.data

import com.example.stefanykhub.BuildConfig
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import androidx.compose.runtime.mutableStateOf

object PasswordPolicy {
    fun error(password: String): String? = when {
        password.length !in 8..128 -> "Пароль має містити від 8 до 128 символів"
        !password.any(Char::isUpperCase) -> "Додайте велику літеру"
        !password.any(Char::isLowerCase) -> "Додайте малу літеру"
        !password.any { it in '0'..'9' } -> "Додайте цифру"
        !password.any { !it.isLetterOrDigit() && !it.isWhitespace() } -> "Додайте спеціальний символ, наприклад ! або #"
        password.lowercase() in setOf("password1!", "password123!", "qwerty123!", "admin123!", "12345678aa!") -> "Цей пароль надто поширений"
        else -> null
    }
    fun strength(password: String): String = when {
        password.isEmpty() -> ""
        error(password) != null -> "Слабкий пароль: ${error(password)}"
        password.length < 12 -> "Пароль відповідає вимогам. Для більшої стійкості використайте 12+ символів."
        else -> "Сильний пароль"
    }
}

internal suspend fun <T> Task<T>.securityAwait(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.resumeWithException(CancellationException()) }
}

object SecurityApi {
    val pendingChallenge = mutableStateOf<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val base: String get() = if (AppRepository.isDemoMode.value && BuildConfig.DEBUG)
        "http://10.0.2.2:8787" else BuildConfig.SECURITY_API_URL.trimEnd('/')

    suspend fun post(path: String, body: JSONObject = JSONObject(), authenticated: Boolean = false): JSONObject {
        val endpoint = base
        check(endpoint.isNotBlank()) { "Сервер безпеки ще не налаштовано. Вкажіть SECURITY_API_URL або ввімкніть локальне тестування." }
        check(endpoint.startsWith("https://") || (BuildConfig.DEBUG && AppRepository.isDemoMode.value && endpoint == "http://10.0.2.2:8787")) { "Сервер має використовувати HTTPS." }
        val token = if (authenticated) {
            val user = AppRepository.securityAuth?.currentUser ?: error("Увійдіть у систему")
            user.getIdToken(false).securityAwait().token ?: error("Сесію завершено")
        } else null
        return withContext(Dispatchers.IO) {
            val connection = URL("$endpoint$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = JSONObject(stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}")
                if (status !in 200..299) error(response.optString("error", "Помилка сервера ($status)"))
                response
            } catch (error: java.io.IOException) {
                throw IllegalStateException("Сервер безпеки недоступний. Перевірте з’єднання та запуск сервера.", error)
            } finally { connection.disconnect() }
        }
    }

    fun cancelChallenge() { pendingChallenge.value = null }
}
