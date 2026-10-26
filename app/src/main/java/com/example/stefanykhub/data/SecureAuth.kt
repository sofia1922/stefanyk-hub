package com.example.stefanykhub.data

import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import kotlinx.coroutines.launch
import org.json.JSONObject

class RegistrationNotice(message: String) : Exception(message)

object SecureAuth {
    private suspend fun accept(response: JSONObject): User {
        if (response.optBoolean("mfaRequired")) {
            SecurityApi.pendingChallenge.value = response.getString("challenge")
            error("Введіть шестизначний код із застосунку автентифікації.")
        }
        val auth = AppRepository.securityAuth ?: error("Firebase не налаштовано")
        try {
            auth.signInWithCustomToken(response.getString("customToken")).securityAwait()
            val p = response.getJSONObject("profile")
            val user = User(
                id = p.getString("id"), fullName = p.getString("fullName"), email = p.getString("email"),
                role = UserRole.valueOf(p.getString("role")), course = p.optString("course", ""),
                specialty = p.optString("specialty", ""), group = p.optString("group", ""),
                department = p.optString("department", "")
            )
            SecurityApi.cancelChallenge()
            AppRepository.acceptSecureUser(user)
            return user
        } catch (error: Exception) { auth.signOut(); throw error }
    }

    fun register(fullName: String, email: String, password: String, role: UserRole,
                 course: String, specialty: String?, group: String?, department: String?,
                 captchaId: String, captchaAnswer: String, onComplete: (Result<User>) -> Unit) {
        SecurityApi.scope.launch {
            val result = runCatching {
                PasswordPolicy.error(password)?.let { error(it) }
                val body = JSONObject().put("fullName", fullName).put("email", email).put("password", password)
                    .put("role", role.name).put("course", course).put("specialty", specialty ?: "")
                    .put("group", group ?: "").put("department", department ?: "")
                    .put("captchaId", captchaId).put("captchaAnswer", captchaAnswer)
                val response = SecurityApi.post("/api/register", body)
                throw RegistrationNotice(response.getString("message"))
            }
            onComplete(result)
        }
    }

    fun login(email: String, password: String, onComplete: (Result<User>) -> Unit) {
        SecurityApi.scope.launch { onComplete(runCatching {
            SecurityApi.cancelChallenge()
            accept(SecurityApi.post("/api/login", JSONObject().put("email", email).put("password", password)))
        }) }
    }

    fun google(idToken: String, onComplete: (Result<Pair<User, Boolean>>) -> Unit) {
        SecurityApi.scope.launch { onComplete(runCatching {
            SecurityApi.cancelChallenge()
            accept(SecurityApi.post("/api/google", JSONObject().put("idToken", idToken))) to false
        }) }
    }

    fun verifyMfa(code: String, onComplete: (Result<User>) -> Unit) {
        SecurityApi.scope.launch { onComplete(runCatching {
            val challenge = SecurityApi.pendingChallenge.value ?: error("Почніть вхід знову")
            accept(SecurityApi.post("/api/mfa/verify", JSONObject().put("challenge", challenge).put("code", code)))
        }) }
    }

    fun reset(email: String, onComplete: (Result<Unit>) -> Unit) {
        SecurityApi.scope.launch { onComplete(runCatching {
            SecurityApi.post("/api/reset", JSONObject().put("email", email)); Unit
        }) }
    }
}
