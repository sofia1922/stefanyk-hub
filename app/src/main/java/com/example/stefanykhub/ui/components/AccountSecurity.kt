package com.example.stefanykhub.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.SecurityApi
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RegistrationCaptcha(generation: Int, enabled: Boolean, onChange: (String, String) -> Unit) {
    var image by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var id by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(generation, refresh, AppRepository.isDemoMode.value) {
        image = null; id = ""; answer = ""; error = null; onChange("", "")
        runCatching { SecurityApi.post("/api/captcha") }
            .onSuccess {
                val bytes = Base64.decode(it.getString("image"), Base64.DEFAULT)
                image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                id = it.getString("id")
            }.onFailure { error = it.localizedMessage }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Перевірка CAPTCHA", style = MaterialTheme.typography.titleSmall)
        image?.let { Image(it.asImageBitmap(), contentDescription = "Шість цифр CAPTCHA", modifier = Modifier.fillMaxWidth().height(80.dp)) }
        if (image == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(answer, { answer = it.filter(Char::isDigit).take(6); onChange(id, answer) },
            enabled = enabled && id.isNotBlank(), label = { Text("Цифри із зображення") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = enabled, onClick = { refresh++ }) { Text("Інше зображення") }
    }
}

@Composable
fun AccountSecurity(user: User, onSessionEnded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var setup by remember { mutableStateOf<JSONObject?>(null) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var events by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var confirmAccount by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun refresh() {
        status = SecurityApi.post("/api/security/status", authenticated = true)
        if (user.role == UserRole.ADMIN) {
            val a = SecurityApi.post("/api/admin/accounts", authenticated = true).getJSONArray("accounts")
            accounts = List(a.length()) { a.getJSONObject(it) }
            val e = SecurityApi.post("/api/admin/audit", authenticated = true).getJSONArray("events")
            events = List(e.length()) { e.getJSONObject(it) }
        }
    }
    fun execute(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch { try { block() } catch (e: Exception) { message = e.localizedMessage } finally { busy = false } }
    }
    OutlinedButton(onClick = { visible = true; execute { refresh() } }, modifier = Modifier.fillMaxWidth()) { Text("Безпека облікового запису") }
    if (visible) AlertDialog(
        onDismissRequest = { if (!busy) { visible = false; setup = null; code = "" } },
        title = { Text("Безпека облікового запису") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(user.email)
                status?.let { s ->
                    Text(if (s.optBoolean("emailVerified")) "Пошта підтверджена • акаунт активовано" else "Пошта не підтверджена")
                    Text(if (s.optBoolean("mfaEnabled")) "Двофакторна автентифікація: увімкнена" else "Двофакторна автентифікація: вимкнена")
                    if (!s.optBoolean("mfaEnabled") && setup == null) {
                        Button(enabled = !busy, onClick = { execute { setup = SecurityApi.post("/api/mfa/setup", authenticated = true) } }) { Text("Налаштувати 2FA") }
                    }
                    setup?.let { value ->
                        Text("У Google Authenticator або іншому сумісному застосунку додайте ключ вручну: тип — за часом (TOTP), 6 цифр, 30 секунд.")
                        SelectionContainer { Text(value.getString("secret"), style = MaterialTheme.typography.titleMedium) }
                        Text("Збережіть ключ у безпечному місці. Без нього та автентифікатора вхід із 2FA буде недоступний.")
                    }
                    if (setup != null || s.optBoolean("mfaEnabled")) {
                        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код автентифікатора") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && code.length == 6, onClick = {
                            execute {
                                val enabling = setup != null
                                val body = JSONObject().put("code", code)
                                if (enabling) body.put("setup", setup!!.getString("setup"))
                                SecurityApi.post(if (enabling) "/api/mfa/enable" else "/api/mfa/disable", body, true)
                                visible = false; setup = null; code = ""; onSessionEnded()
                            }
                        }) { Text(if (setup != null) "Увімкнути 2FA та ввійти знову" else "Вимкнути 2FA та ввійти знову") }
                    }
                }
                if (user.role == UserRole.ADMIN) {
                    HorizontalDivider()
                    Text("Облікові записи", style = MaterialTheme.typography.titleMedium)
                    accounts.forEach { account ->
                        Column {
                            Text(account.optString("email"))
                            Text("Спроб після останнього успішного входу: ${account.optInt("failedAttempts")}", style = MaterialTheme.typography.bodySmall)
                            if (account.optLong("lockedUntil") > System.currentTimeMillis()) Text("Вхід тимчасово обмежено")
                            if (account.optString("uid") != user.id) {
                                TextButton(enabled = !busy, onClick = { confirmAccount = account }) {
                                    Text(if (account.optBoolean("blocked")) "Розблокувати" else if (account.optInt("failedAttempts") >= 5) "Зняти тимчасове блокування" else "Заблокувати")
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Журнал входів і подій", style = MaterialTheme.typography.titleMedium)
                    val format = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
                    if (events.isEmpty()) Text("Подій ще немає")
                    events.forEach { event ->
                        Text("${format.format(Date(event.optLong("time")))} • ${event.optString("email")}\n${event.optString("event")} • ${if (event.optBoolean("success")) "успішно" else "відмова"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { execute { refresh() } }) { Text("Оновити") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { visible = false; setup = null; code = "" }) { Text("Закрити") } }
    )
    confirmAccount?.let { account ->
        val block = !account.optBoolean("blocked") && account.optInt("failedAttempts") < 5
        AlertDialog(onDismissRequest = { confirmAccount = null }, title = { Text(if (block) "Заблокувати акаунт?" else "Розблокувати акаунт?") },
            text = { Text(account.optString("email")) },
            confirmButton = { TextButton(onClick = {
                confirmAccount = null
                execute { SecurityApi.post("/api/admin/block", JSONObject().put("uid", account.getString("uid")).put("blocked", block), true); refresh() }
            }) { Text("Підтвердити") } }, dismissButton = { TextButton(onClick = { confirmAccount = null }) { Text("Скасувати") } })
    }
}
