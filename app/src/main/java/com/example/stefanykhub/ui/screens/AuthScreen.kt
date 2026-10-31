package com.example.stefanykhub.ui.screens

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.stefanykhub.data.AppRepository
import com.example.stefanykhub.data.PasswordPolicy
import com.example.stefanykhub.data.SecurityApi
import com.example.stefanykhub.data.SecureAuth
import com.example.stefanykhub.data.RegistrationNotice
import com.example.stefanykhub.ui.components.RegistrationCaptcha
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: (User, isNewUser: Boolean) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }


    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaId by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var captchaGeneration by remember { mutableIntStateOf(0) }
    var secondFactorCode by remember { mutableStateOf("") }
    val mfaChallenge = SecurityApi.pendingChallenge.value

    LaunchedEffect(email, password, isRegisterMode) {
        SecurityApi.cancelChallenge()
        secondFactorCode = ""
    }


    var specialty by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var courseMenuExpanded by remember { mutableStateOf(false) }


    var department by remember { mutableStateOf("") }


    var errorMessage by remember { mutableStateOf<String?>(null) }
    var registrationNotice by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    registrationNotice?.let { message ->
        AlertDialog(onDismissRequest = { registrationNotice = null }, title = { Text("Акаунт створено") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { registrationNotice = null }) { Text("Зрозуміло") } })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ScreenBackground
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(52.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Stefanyk.Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = if (isRegisterMode) "Реєстрація акаунту" else "Вхід у систему",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))


                    DebugDemoControls(enabled = !isSubmitting) { demoEmail, demoPassword ->
                        isRegisterMode = false
                        email = demoEmail
                        password = demoPassword
                        errorMessage = null
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TabRow(
                        selectedTabIndex = if (isRegisterMode) 1 else 0,
                        containerColor = PrimaryContainerIndigo
                    ) {
                        Tab(
                            selected = !isRegisterMode,
                            onClick = {
                                isRegisterMode = false
                                errorMessage = null
                            },
                            text = { Text("Вхід", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = isRegisterMode,
                            onClick = {
                                isRegisterMode = true
                                errorMessage = null
                            },
                            text = { Text("Реєстрація", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))


                    var fullNameError by remember { mutableStateOf<String?>(null) }
                    var emailError by remember { mutableStateOf<String?>(null) }
                    var passwordError by remember { mutableStateOf<String?>(null) }
                    var specialtyError by remember { mutableStateOf<String?>(null) }
                    var courseError by remember { mutableStateOf<String?>(null) }
                    var groupError by remember { mutableStateOf<String?>(null) }
                    var departmentError by remember { mutableStateOf<String?>(null) }

                    if (isRegisterMode) {

                        Text(
                            text = "Виберіть ваш статус:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedRole == UserRole.STUDENT,
                                    onClick = {
                                        selectedRole = UserRole.STUDENT
                                        errorMessage = null
                                    },
                                    label = { Text("🎓 Студент", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = selectedRole == UserRole.TEACHER,
                                    onClick = {
                                        selectedRole = UserRole.TEACHER
                                        errorMessage = null
                                    },
                                    label = { Text("👨‍🏫 Викладач", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))


                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                fullNameError = null
                                errorMessage = null
                            },
                            label = { Text("ПІБ (Прізвище, Ім'я)") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            isError = fullNameError != null,
                            supportingText = { fullNameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }


                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                            errorMessage = null
                        },
                        label = { Text("Електронна пошта (@pnu.edu.ua)") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        isError = emailError != null,
                        supportingText = { emailError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))


                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            errorMessage = null
                        },
                        label = { Text("Пароль") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = { passwordError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isRegisterMode) {
                        TextButton(
                            onClick = {
                                if (!AppRepository.isUniversityEmail(email)) {
                                    errorMessage = "Спочатку введіть університетську пошту для відновлення пароля."
                                } else {
                                    isSubmitting = true
                                    AppRepository.sendPasswordReset(email) { result ->
                                        isSubmitting = false
                                        errorMessage = result.fold(
                                            onSuccess = { "Лист для відновлення пароля надіслано на $email." },
                                            onFailure = { it.localizedMessage ?: "Не вдалося надіслати лист." }
                                        )
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Забули пароль?")
                        }
                    }

                    if (isRegisterMode) {
                        Spacer(modifier = Modifier.height(6.dp))

                        if (selectedRole == UserRole.STUDENT) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { courseMenuExpanded = true },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (course.isBlank()) "Оберіть курс навчання" else course)
                                }
                                DropdownMenu(
                                    expanded = courseMenuExpanded,
                                    onDismissRequest = { courseMenuExpanded = false }
                                ) {
                                    listOf("1 курс", "2 курс", "3 курс", "4 курс", "Магістратура").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                course = option
                                                courseError = null
                                                courseMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            courseError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Start))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = specialty,
                                onValueChange = {
                                    specialty = it
                                    specialtyError = null
                                    errorMessage = null
                                },
                                label = { Text("Спеціальність (напр. 121 ІПЗ)") },
                                isError = specialtyError != null,
                                supportingText = { specialtyError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = group,
                                onValueChange = {
                                    group = it
                                    groupError = null
                                    errorMessage = null
                                },
                                label = { Text("Академічна група (напр. ІПЗ-41)") },
                                isError = groupError != null,
                                supportingText = { groupError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = department,
                                onValueChange = {
                                    department = it
                                    departmentError = null
                                    errorMessage = null
                                },
                                label = { Text("Кафедра (напр. Кафедра ІПЗ)") },
                                isError = departmentError != null,
                                supportingText = { departmentError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }


                    errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "⚠️ $err",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRegisterMode) {
                        Text(PasswordPolicy.strength(password), style = MaterialTheme.typography.bodySmall)
                        Text("8–128 символів, великі й малі літери, цифра та спеціальний символ.", style = MaterialTheme.typography.bodySmall)
                        RegistrationCaptcha(captchaGeneration, !isSubmitting) { id, answer ->
                            captchaId = id
                            captchaAnswer = answer
                        }
                    }
                    if (mfaChallenge != null && !isRegisterMode) {
                        OutlinedTextField(
                            value = secondFactorCode,
                            onValueChange = { secondFactorCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("Код із автентифікатора") },
                            supportingText = { Text("Введіть 6 цифр із Google Authenticator або сумісного застосунку") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = { SecurityApi.cancelChallenge(); secondFactorCode = "" }) { Text("Почати вхід знову") }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            fullNameError = null
                            emailError = null
                            passwordError = null
                            specialtyError = null
                            courseError = null
                            groupError = null
                            departmentError = null

                            var hasError = false

                            if (mfaChallenge == null && email.isBlank()) {
                                emailError = "Введіть електронну пошту!"
                                hasError = true
                            } else if (mfaChallenge == null && !AppRepository.isUniversityEmail(email)) {
                                emailError = "Пошта повинна закінчуватись на @pnu.edu.ua або @stefanyk.edu.ua"
                                hasError = true
                            }

                            if (mfaChallenge == null && password.isBlank()) {
                                passwordError = "Введіть пароль!"
                                hasError = true
                            } else if (isRegisterMode && PasswordPolicy.error(password) != null) {
                                passwordError = PasswordPolicy.error(password)
                                hasError = true
                            }

                            if (isRegisterMode) {
                                if (fullName.isBlank()) {
                                    fullNameError = "Введіть ваші ПІБ!"
                                    hasError = true
                                }

                                if (selectedRole == UserRole.STUDENT) {
                                    if (course.isBlank()) {
                                        courseError = "Оберіть курс навчання!"
                                        hasError = true
                                    }
                                    if (specialty.isBlank()) {
                                        specialtyError = "Вкажіть спеціальність!"
                                        hasError = true
                                    }
                                    if (group.isBlank()) {
                                        groupError = "Вкажіть академічну групу!"
                                        hasError = true
                                    }
                                } else if (selectedRole == UserRole.TEACHER) {
                                    if (department.isBlank()) {
                                        departmentError = "Вкажіть вашу кафедру!"
                                        hasError = true
                                    }
                                }

                                if (hasError) return@Button
                                if (captchaId.isBlank() || captchaAnswer.isBlank()) {
                                    errorMessage = "Введіть цифри із зображення CAPTCHA"
                                    return@Button
                                }

                                isSubmitting = true
                                AppRepository.registerUser(
                                    fullName = fullName,
                                    email = email,
                                    password = password,
                                    captchaId = captchaId,
                                    captchaAnswer = captchaAnswer,
                                    role = selectedRole,
                                    course = if (selectedRole == UserRole.STUDENT) course else "",
                                    specialty = if (selectedRole == UserRole.STUDENT) specialty else null,
                                    group = if (selectedRole == UserRole.STUDENT) group else null,
                                    department = if (selectedRole != UserRole.STUDENT) department.ifBlank { "Адміністрація" } else null,
                                    onComplete = { result ->
                                        isSubmitting = false
                                        captchaGeneration++
                                        result.onSuccess { user -> onAuthSuccess(user, true) }
                                            .onFailure { error ->
                                                if (error is RegistrationNotice) {
                                                    registrationNotice = error.message
                                                    isRegisterMode = false
                                                    password = ""
                                                } else errorMessage = error.localizedMessage ?: "Не вдалося створити акаунт"
                                            }
                                    }
                                )
                            } else {
                                if (hasError) return@Button

                                isSubmitting = true
                                val complete: (Result<User>) -> Unit = { result ->
                                    isSubmitting = false
                                    result.onSuccess { user -> onAuthSuccess(user, false) }
                                        .onFailure { error -> errorMessage = error.localizedMessage ?: "Невірна електронна пошта або пароль" }
                                }
                                if (mfaChallenge != null) SecureAuth.verifyMfa(secondFactorCode, complete)
                                else AppRepository.loginUser(email, password, complete)
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Text(
                            text = if (isSubmitting) "Зачекайте…" else if (isRegisterMode) "Зареєструватися" else if (mfaChallenge != null) "Підтвердити код" else "Увійти",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isRegisterMode && mfaChallenge == null) {
                        TextButton(enabled = !isSubmitting, onClick = {
                            if (!AppRepository.isUniversityEmail(email)) {
                                errorMessage = "Спочатку введіть університетську пошту"
                            } else {
                                isSubmitting = true
                                scope.launch {
                                    runCatching { SecurityApi.post("/api/resend", org.json.JSONObject().put("email", email)) }
                                        .onSuccess { errorMessage = it.optString("message") }
                                        .onFailure { errorMessage = it.localizedMessage }
                                    isSubmitting = false
                                }
                            }
                        }) { Text("Надіслати підтвердження пошти повторно") }
                    }
                    if (!isRegisterMode && !AppRepository.isDemoMode.value) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (isSubmitting) return@OutlinedButton
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        val clientIdResource = context.resources.getIdentifier(
                                            "default_web_client_id",
                                            "string",
                                            context.packageName
                                        )
                                        if (clientIdResource == 0) {
                                            error("У Firebase немає OAuth Web Client ID. Додайте оновлений google-services.json.")
                                        }
                                        val serverClientId = context.getString(clientIdResource)
                                        val credentialManager = CredentialManager.create(context)
                                        fun request(filterAuthorized: Boolean) = GetCredentialRequest.Builder()
                                            .addCredentialOption(
                                                GetGoogleIdOption.Builder()
                                                    .setServerClientId(serverClientId)
                                                    .setFilterByAuthorizedAccounts(filterAuthorized)
                                                    .setAutoSelectEnabled(false)
                                                    .build()
                                            )
                                            .build()
                                        val response = try {
                                            credentialManager.getCredential(context, request(true))
                                        } catch (_: NoCredentialException) {
                                            credentialManager.getCredential(context, request(false))
                                        }
                                        val credential = response.credential as? CustomCredential
                                            ?: error("Google не повернув облікові дані")
                                        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            error("Отримано невідомий тип облікових даних Google")
                                        }
                                        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                        AppRepository.loginWithGoogleIdToken(idToken) { result ->
                                            isSubmitting = false
                                            result.onSuccess { (user, isNewUser) -> onAuthSuccess(user, isNewUser) }
                                                .onFailure { error -> errorMessage = error.localizedMessage ?: "Не вдалося увійти через Google" }
                                        }
                                    } catch (error: Exception) {
                                        isSubmitting = false
                                        errorMessage = error.localizedMessage ?: "Не вдалося увійти через Google"
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Увійти через Google", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
