package com.example.stefanykhub.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.example.stefanykhub.model.CatalogItem
import com.example.stefanykhub.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.example.stefanykhub.model.User
import com.example.stefanykhub.model.UserRole
import com.example.stefanykhub.model.StudyProgress
import com.example.stefanykhub.model.StudyStreak
import com.example.stefanykhub.model.MaterialReaction
import com.example.stefanykhub.model.ReactionSummary
import com.example.stefanykhub.model.ReactionVote
import com.example.stefanykhub.model.nextReaction
import com.example.stefanykhub.model.summarizeReactions
import java.time.LocalDate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

// Модель для візуального бейджа досягнень
data class AchievementBadge(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean
)

object AppRepository {
    fun sendPresenceHeartbeat() {
        val user = currentUser.value ?: return
        if (firebaseAuth?.currentUser?.isEmailVerified != true) return
        firestore?.collection("presence")?.document(user.id)?.set(mapOf(
            "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "sentAt" to com.google.firebase.Timestamp.now()))
    }

    fun observeUniversityPulse(
        presence: (List<com.example.stefanykhub.model.PulsePresence>?, String?) -> Unit,
        downloads: (List<com.example.stefanykhub.model.PulseDownload>?, String?) -> Unit,
        storage: (com.example.stefanykhub.model.StorageReport?, String?) -> Unit
    ): () -> Unit {
        val user = currentUser.value?.takeIf { it.role == UserRole.ADMIN } ?: return {}
        val db = firestore ?: run { presence(null, "Firebase не підключено"); downloads(null, "Firebase не підключено"); storage(null, "Firebase не підключено"); return {} }
        val weekAgo = com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() - 7 * 86_400_000L))
        val listeners = listOf(
            db.collection("presence").whereGreaterThanOrEqualTo("lastSeen", weekAgo).addSnapshotListener { snapshot, error ->
                if (currentUser.value?.id != user.id) return@addSnapshotListener
                presence(snapshot?.documents?.mapNotNull { doc -> doc.getTimestamp("lastSeen")?.toDate()?.time?.let {
                    com.example.stefanykhub.model.PulsePresence(doc.id, it)
                } }, error?.localizedMessage)
            },
            db.collectionGroup("activity").addSnapshotListener { snapshot, error ->
                if (currentUser.value?.id != user.id) return@addSnapshotListener
                downloads(snapshot?.documents?.mapNotNull { doc ->
                    if (doc.getString("kind") != "DOWNLOAD" || doc.getTimestamp("time") == null) return@mapNotNull null
                    val material = doc.reference.parent.parent ?: return@mapNotNull null
                    if (material.parent.id != "materials") return@mapNotNull null
                    material.id.toIntOrNull()?.let { com.example.stefanykhub.model.PulseDownload(it) }
                }, error?.localizedMessage)
            },
            db.collection("system_metrics").document("storage").addSnapshotListener { doc, error ->
                if (currentUser.value?.id != user.id) return@addSnapshotListener
                val bytes = doc?.getLong("bytes")
                val updated = doc?.getTimestamp("updatedAt")?.toDate()?.time
                storage(if (bytes != null && updated != null) com.example.stefanykhub.model.StorageReport(bytes,
                    doc.getLong("files") ?: 0, updated, doc.getLong("budgetBytes")) else null, error?.localizedMessage)
            }
        )
        return { listeners.forEach { it.remove() } }
    }
    fun observeSystemAlerts(manage: Boolean, callback: (List<com.example.stefanykhub.model.SystemAlert>, String?) -> Unit): () -> Unit {
        val uid = currentUser.value?.id ?: return {}
        val db = firestore ?: run { callback(emptyList(), "Firebase не підключено"); return {} }
        var query: com.google.firebase.firestore.Query = db.collection("system_alerts")
        if (!manage) query = query.whereEqualTo("active", true)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (currentUser.value?.id != uid) return@addSnapshotListener
            if (error != null) callback(emptyList(), error.localizedMessage)
            else callback(snapshot?.documents.orEmpty().mapNotNull { doc ->
                val level = runCatching { com.example.stefanykhub.model.AlertLevel.valueOf(doc.getString("level").orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                com.example.stefanykhub.model.SystemAlert(doc.id, doc.getString("text").orEmpty(), level,
                    doc.getBoolean("active") == true, doc.getTimestamp("createdAt")?.toDate()?.time ?: 0)
            }.sortedWith(compareByDescending<com.example.stefanykhub.model.SystemAlert> { it.active }
                .thenByDescending { it.level.ordinal }.thenByDescending { it.createdAt }.thenBy { it.id }), null)
        }
        return { listener.remove() }
    }

    fun publishSystemAlert(text: String, level: com.example.stefanykhub.model.AlertLevel, done: (String?) -> Unit) {
        val user = currentUser.value ?: return done("Увійдіть у систему")
        if (user.role != UserRole.ADMIN) return done("Доступ лише для адміністратора")
        if (text.trim().isEmpty() || text.trim().length > 1000) return done("Введіть від 1 до 1000 символів")
        val db = firestore ?: return done("Firebase не підключено")
        db.collection("system_alerts").add(mapOf("text" to text.trim(), "level" to level.name,
            "active" to true, "authorId" to user.id, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "Помилка публікації") }
    }

    fun setSystemAlertActive(id: String, active: Boolean, done: (String?) -> Unit) {
        if (currentUser.value?.role != UserRole.ADMIN) return done("Доступ лише для адміністратора")
        val db = firestore ?: return done("Firebase не підключено")
        db.collection("system_alerts").document(id).update("active", active)
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "Помилка оновлення") }
    }
    fun observeAnnouncements(subject: String?, manage: Boolean, materialId: Int? = null, callback: (List<com.example.stefanykhub.model.Announcement>, String?) -> Unit): () -> Unit {
        val user = currentUser.value ?: return {}
        val db = firestore ?: run { callback(emptyList(), "Firebase не підключено"); return {} }
        var query: com.google.firebase.firestore.Query = db.collection("announcements")
        if (manage && user.role != UserRole.ADMIN) query = query.whereEqualTo("authorId", user.id)
        else if (!manage) {
            if (subject == null) return {}
            query = query.whereEqualTo("subject", subject)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (currentUser.value?.id != user.id) return@addSnapshotListener
            if (error != null) callback(emptyList(), error.localizedMessage)
            else callback(snapshot?.documents.orEmpty().map { doc ->
                com.example.stefanykhub.model.Announcement(doc.id, doc.getString("authorId").orEmpty(),
                    doc.getString("authorName").orEmpty(), doc.getString("subject").orEmpty(),
                    doc.getString("text").orEmpty(), doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                    doc.getString("scope") ?: "SUBJECT", doc.getString("materialId"), doc.getString("materialTitle"))
            }.filter { manage || if (materialId == null) it.scope == "SUBJECT"
                else it.scope == "MATERIAL" && it.materialId == materialId.toString()
            }.sortedWith(compareByDescending<com.example.stefanykhub.model.Announcement> { it.createdAt }.thenBy { it.id }), null)
        }
        return { listener.remove() }
    }

    fun publishAnnouncement(materialId: Int, text: String, scope: String = "SUBJECT", done: (String?) -> Unit) {
        val user = currentUser.value ?: return done("Увійдіть у систему")
        val material = materials.firstOrNull { it.id == materialId } ?: return done("Матеріал не знайдено")
        if (user.role != UserRole.ADMIN && !(user.role == UserRole.TEACHER && material.teacherId == user.id)) return done("Немає прав на публікацію")
        val message = text.trim()
        if (scope !in listOf("SUBJECT", "MATERIAL")) return done("Оберіть область оголошення")
        if (message.isBlank() || message.length > 500) return done("Оголошення має містити від 1 до 500 символів")
        val db = firestore ?: return done("Firebase не підключено")
        db.collection("announcements").add(mapOf("authorId" to user.id, "authorName" to user.fullName,
            "materialId" to material.id.toString(), "subject" to material.subject, "text" to message,
            "scope" to scope, "materialTitle" to material.title,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "Не вдалося опублікувати") }
    }

    fun deleteAnnouncement(id: String, done: (String?) -> Unit) {
        val db = firestore ?: return done("Firebase не підключено")
        db.collection("announcements").document(id).delete().addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.localizedMessage ?: "Не вдалося видалити") }
    }
    /** Immutable server-timestamped events. Failures are visible; opening a file remains usable offline. */
    val analyticsWriteError = mutableStateOf<String?>(null)

    fun recordMaterialActivity(itemId: Int, kind: String) {
        val user = currentUser.value ?: return
        if (user.role != UserRole.STUDENT || kind !in listOf("VIEW", "DOWNLOAD")) return
        firestore?.collection("materials")?.document(itemId.toString())?.collection("activity")?.add(
            mapOf("studentId" to user.id, "kind" to kind, "group" to user.group.orEmpty(),
                "time" to com.google.firebase.firestore.FieldValue.serverTimestamp())
        )?.addOnSuccessListener { analyticsWriteError.value = null }
            ?.addOnFailureListener { analyticsWriteError.value = "Не вдалося синхронізувати активність: ${it.localizedMessage}" }
    }

    fun observeMaterialAnalytics(itemId: Int, callback: (com.example.stefanykhub.model.MaterialAnalytics?, String?) -> Unit): () -> Unit {
        val registration = firestore?.collection("materials")?.document(itemId.toString())?.collection("activity")
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) callback(null, error.localizedMessage)
                else callback(com.example.stefanykhub.model.MaterialAnalytics(snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val time = doc.getTimestamp("time")?.toDate()?.time ?: return@mapNotNull null
                    com.example.stefanykhub.model.MaterialActivity(doc.getString("studentId").orEmpty(),
                        doc.getString("kind").orEmpty(), doc.getString("group").orEmpty(), time)
                }), null)
            }
        if (registration == null) callback(null, "Firebase не підключено")
        return { registration?.remove() }
    }

    fun observeAnalyticsSettings(itemId: Int, callback: (com.example.stefanykhub.model.AnalyticsSettings?, String?) -> Unit): () -> Unit {
        val registration = firestore?.collection("materials")?.document(itemId.toString())?.collection("analytics")?.document("settings")
            ?.addSnapshotListener { doc, error ->
                if (error != null) callback(null, error.localizedMessage)
                else callback(com.example.stefanykhub.model.AnalyticsSettings(doc?.getString("group").orEmpty(),
                    doc?.getLong("groupSize")?.toInt() ?: 0, doc?.getLong("deadline") ?: 0), null)
            }
        return { registration?.remove() }
    }

    fun saveAnalyticsSettings(itemId: Int, settings: com.example.stefanykhub.model.AnalyticsSettings, done: (String?) -> Unit) {
        val db = firestore ?: return done("Firebase не підключено")
        db.collection("materials").document(itemId.toString()).collection("analytics").document("settings")
            .set(mapOf("group" to settings.group.trim(), "groupSize" to settings.groupSize, "deadline" to settings.deadline))
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "Помилка збереження") }
    }
    private var prefs: SharedPreferences? = null
    private val downloadMutex = Mutex()
    private val readerMutex = Mutex()

    // Навчальний стрік (Study Streak) - починається з 0 і зростає при активності!
    var studyStreakDays = mutableIntStateOf(0)
    var lastActiveDate = mutableStateOf("")
    val bestStudyStreakDays = mutableIntStateOf(0)
    private var progressLoadVersion = 0L

    // База дозволених пошт викладачів
    val allowedTeacherEmails = mutableStateListOf<String>()

    // Список навчальних матеріалів
    val materials = mutableStateListOf<CatalogItem>()

    // Завантажені матеріали студента
    val downloadedItemIds = mutableStateListOf<Int>()

    // Ініціалізація інстансів Firebase
    val isDemoMode = mutableStateOf(false)
    private var demoFirebaseApp: FirebaseApp? = null
    private val activeFirebaseApp: FirebaseApp? get() =
        if (isDemoMode.value && BuildConfig.DEBUG) demoFirebaseApp else runCatching { FirebaseApp.getInstance() }.getOrNull()
    private val firebaseAuth: FirebaseAuth? get() = activeFirebaseApp?.let(FirebaseAuth::getInstance)
    private val firestore: FirebaseFirestore? get() = activeFirebaseApp?.let(FirebaseFirestore::getInstance)
    private val firebaseStorage: FirebaseStorage? get() = activeFirebaseApp?.let(FirebaseStorage::getInstance)

    private fun configureDemoFirebase(context: Context) {
        isDemoMode.value = BuildConfig.DEBUG && context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE)
            .getBoolean("local_firebase", false)
        if (!isDemoMode.value || demoFirebaseApp != null) return
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-stefanykhub")
            .setApplicationId("1:1234567890:android:0000000000000000")
            .setApiKey("demo-api-key")
            .setStorageBucket("demo-stefanykhub.firebasestorage.app")
            .build()
        val app = FirebaseApp.initializeApp(context.applicationContext, options, "local-demo")
        FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
        FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
        FirebaseStorage.getInstance(app).useEmulator("10.0.2.2", 9199)
        demoFirebaseApp = app
    }

    fun setDemoMode(context: Context, enabled: Boolean) {
        if (!BuildConfig.DEBUG || isDemoMode.value == enabled) return
        logout()
        prefs = null
        authInitialized.value = false
        materials.clear()
        downloadedItemIds.clear()
        allowedTeacherEmails.clear()
        context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("local_firebase", enabled).apply()
        initStorage(context.applicationContext)
    }
    private var allowedTeachersListener: ListenerRegistration? = null
    private var materialsListener: ListenerRegistration? = null
    val materialReactions = mutableStateMapOf<Int, ReactionSummary>()
    val reactionErrors = mutableStateMapOf<Int, String>()
    private class ReactionWatch(var observers: Int = 1, var listener: ListenerRegistration? = null)
    private val reactionWatches = mutableMapOf<String, ReactionWatch>()

    /** One shared listener per visible material; disposing the last view releases it. */
    fun observeReactions(materialId: Int): () -> Unit {
        val uid = currentUser.value?.id ?: return {}
        val key = "$uid:$materialId"
        val existing = reactionWatches[key]
        val watch = if (existing != null) {
            existing.observers++
            existing
        } else {
            val created = ReactionWatch()
            reactionWatches[key] = created
            val database = firestore
            if (database == null) reactionErrors[materialId] = "Firebase не налаштовано."
            created.listener = database?.collection("materials")?.document(materialId.toString())
                ?.collection("reactions")?.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (currentUser.value?.id != uid || reactionWatches[key] !== created) return@addSnapshotListener
                    if (error != null) {
                        reactionErrors[materialId] = "Не вдалося завантажити реакції. Перевірте підключення та правила Firestore."
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val votes = snapshot.documents.mapNotNull { doc ->
                            val type = doc.getString("reaction")?.let { value ->
                                MaterialReaction.entries.firstOrNull { it.name == value }
                            } ?: return@mapNotNull null
                            ReactionVote(doc.id, type)
                        }
                        materialReactions[materialId] = summarizeReactions(votes, uid)
                            .copy(pending = snapshot.metadata.hasPendingWrites())
                    }
                }
            created
        }
        return {
            if (reactionWatches[key] === watch) {
                watch.observers--
                if (watch.observers == 0) {
                    watch.listener?.remove()
                    reactionWatches.remove(key)
                    materialReactions.remove(materialId)
                    reactionErrors.remove(materialId)
                }
            }
        }
    }

    fun toggleReaction(materialId: Int, reaction: MaterialReaction) {
        val student = currentUser.value?.takeIf { it.role == UserRole.STUDENT } ?: return
        val database = firestore ?: return
        val current = materialReactions[materialId]?.mine
        val next = nextReaction(current, reaction)
        reactionErrors.remove(materialId)
        val doc = database.collection("materials").document(materialId.toString())
            .collection("reactions").document(student.id)
        // Firestore applies pending writes to the live snapshot immediately, including offline.
        val task = if (next == null) doc.delete() else doc.set(mapOf("reaction" to next.name))
        task.addOnSuccessListener {
            if (currentUser.value?.id == student.id) reactionErrors.remove(materialId)
        }.addOnFailureListener {
            if (currentUser.value?.id == student.id) {
                reactionErrors[materialId] = "Реакцію не збережено. Спробуйте ще раз; якщо помилка повторюється, перевірте правила доступу."
            }
        }
        checkAndUpdateStreak()
    }

    // Поточний авторизований користувач
    var currentUser = mutableStateOf<User?>(null)
    val authInitialized = mutableStateOf(false)

    // Ініціалізація логіки локального збереження
    fun initStorage(context: Context) {
        if (prefs == null) {
            configureDemoFirebase(context)
            val storageName = if (isDemoMode.value) "stefanyk_hub_storage_demo" else "stefanyk_hub_storage"
            prefs = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
            // Remove credentials written by older demo builds.
            prefs?.edit()?.remove("registered_users")?.apply()
            loadFromDisk()
            restoreSignedInUser()
        }
    }

    private fun restoreSignedInUser() {
        val authUser = firebaseAuth?.currentUser
        val database = firestore
        if (authUser == null || database == null) {
            authInitialized.value = true
            return
        }
        database.collection("users").document(authUser.uid).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.toUser(authUser.uid)
                if (user == null) {
                    firebaseAuth?.signOut()
                    currentUser.value = null
                } else {
                    currentUser.value = user
                    loadFavorites(user.id)
                    listenToMaterials()
                    if (user.role == UserRole.ADMIN) listenToAllowedTeachers()
                }
                authInitialized.value = true
            }
            .addOnFailureListener {
                currentUser.value = null
                firebaseAuth?.signOut()
                authInitialized.value = true
            }
    }

    private fun listenToAllowedTeachers() {
        val database = firestore ?: return
        allowedTeachersListener?.remove()
        allowedTeachersListener = database.collection("allowed_teachers")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                allowedTeacherEmails.clear()
                allowedTeacherEmails.addAll(snapshot.documents.mapNotNull { document ->
                    (document.getString("email") ?: document.id).trim().lowercase().takeIf(String::isNotBlank)
                })
                prefs?.edit()?.putStringSet("allowed_teachers", allowedTeacherEmails.toSet())?.apply()
            }
    }

    private fun listenToMaterials() {
        val database = firestore ?: return
        materialsListener?.remove()
        materialsListener = database.collection("materials")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val remoteMaterials = snapshot.documents.mapNotNull(::documentToCatalogItem)
                    .sortedByDescending { it.id }
                materials.clear()
                materials.addAll(remoteMaterials)
                currentUser.value?.id?.let(::loadFavorites)
                saveToDisk()
            }
    }

    private fun documentToCatalogItem(document: com.google.firebase.firestore.DocumentSnapshot): CatalogItem? {
        val id = document.getLong("id")?.toInt() ?: document.id.toIntOrNull() ?: return null
        val title = document.getString("title") ?: return null
        return CatalogItem(
            id = id,
            title = title,
            type = document.getString("type") ?: "Матеріал",
            subject = document.getString("subject") ?: "Без дисципліни",
            teacher = document.getString("teacher") ?: "Невідомий автор",
            teacherId = document.getString("teacherId") ?: "",
            date = document.getString("date") ?: "",
            fileType = document.getString("fileType") ?: "PDF",
            fileSize = document.getString("fileSize") ?: "",
            semester = (document.getLong("semester") ?: 1L).toInt(),
            course = document.getString("course") ?: "1 курс",
            tags = document.getString("tags") ?: "",
            description = document.getString("description") ?: "",
            linkUrl = document.getString("linkUrl")?.takeIf(String::isNotBlank),
            isFavorite = false,
            isCompleted = false,
            downloadCount = (document.getLong("downloadCount") ?: 0L).toInt()
        )
    }

    private fun catalogItemToMap(item: CatalogItem): Map<String, Any> = mapOf(
        "id" to item.id,
        "title" to item.title,
        "type" to item.type,
        "subject" to item.subject,
        "teacher" to item.teacher,
        "teacherId" to item.teacherId,
        "date" to item.date,
        "fileType" to item.fileType,
        "fileSize" to item.fileSize,
        "semester" to item.semester,
        "course" to item.course,
        "tags" to item.tags,
        "description" to item.description,
        "linkUrl" to (item.linkUrl ?: ""),
        "downloadCount" to item.downloadCount
    )

    private fun userToMap(user: User): Map<String, Any> = mapOf(
        "id" to user.id,
        "fullName" to user.fullName,
        "email" to user.email,
        "role" to user.role.name,
        "course" to user.course,
        "specialty" to (user.specialty ?: ""),
        "group" to (user.group ?: ""),
        "department" to (user.department ?: "")
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(uid: String): User? {
        val fullName = getString("fullName")?.takeIf(String::isNotBlank) ?: return null
        val email = getString("email")?.takeIf(String::isNotBlank) ?: return null
        val role = getString("role")?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: return null
        return User(
            id = uid,
            fullName = fullName,
            email = email,
            role = role,
            course = getString("course") ?: "1 курс",
            specialty = getString("specialty")?.takeIf(String::isNotBlank),
            group = getString("group")?.takeIf(String::isNotBlank),
            department = getString("department")?.takeIf(String::isNotBlank)
        )
    }

    private fun loadFromDisk() {
        val sp = prefs ?: return

        downloadedItemIds.clear()
        downloadedItemIds.addAll(sp.getStringSet("downloaded_item_ids", emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull).filter { getDownloadedFileUri(it) != null })

        val savedEmails = sp.getStringSet("allowed_teachers", null)
        if (!savedEmails.isNullOrEmpty()) {
            allowedTeacherEmails.clear()
            allowedTeacherEmails.addAll(savedEmails)
        }

        val materialsJsonStr = sp.getString("materials_catalog", null)
        if (!materialsJsonStr.isNullOrBlank()) {
            runCatching {
                val jsonArr = JSONArray(materialsJsonStr)
                materials.clear()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    materials.add(
                        CatalogItem(
                            id = obj.getInt("id"),
                            title = obj.getString("title"),
                            type = obj.getString("type"),
                            subject = obj.getString("subject"),
                            teacher = obj.getString("teacher"),
                            teacherId = obj.optString("teacherId", ""),
                            date = obj.optString("date", "Сьогодні"),
                            fileType = obj.optString("fileType", "PDF"),
                            fileSize = obj.optString("fileSize", "2.0 MB"),
                            semester = obj.optInt("semester", 7),
                            course = obj.optString("course", "4 курс"),
                            tags = obj.optString("tags", ""),
                            description = obj.optString("description", ""),
                            linkUrl = obj.optString("linkUrl", null),
                            isFavorite = false,
                            isCompleted = false,
                            downloadCount = obj.optInt("downloadCount", 0)
                        )
                    )
                }
            }
        }
    }

    private fun saveToDisk() {
        val sp = prefs ?: return
        val editor = sp.edit()


        editor.putStringSet("allowed_teachers", allowedTeacherEmails.toSet())

        val materialsJsonArr = JSONArray()
        materials.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("subject", item.subject)
                put("teacher", item.teacher)
                put("teacherId", item.teacherId)
                put("date", item.date)
                put("fileType", item.fileType)
                put("fileSize", item.fileSize)
                put("semester", item.semester)
                put("course", item.course)
                put("tags", item.tags)
                put("description", item.description)
                put("linkUrl", item.linkUrl ?: "")
                put("downloadCount", item.downloadCount)
            }
            materialsJsonArr.put(obj)
        }
        editor.putString("materials_catalog", materialsJsonArr.toString())

        editor.apply()
    }

    // Перевірка та оновлення стріку активності (наростає від 0 при реальній активності)
    fun checkAndUpdateStreak() {
        val user = currentUser.value?.takeIf { it.role == UserRole.STUDENT } ?: return
        val updated = storedStudyStreak(user.id).recordActivity(LocalDate.now())
        prefs?.edit()
            ?.putInt("streak_days_${user.id}", updated.days)
            ?.putString("streak_date_${user.id}", updated.lastActiveDate)
            ?.putInt("streak_best_${user.id}", updated.bestDays)
            ?.apply()
        refreshStudyStreak()
    }

    private fun storedStudyStreak(userId: String) = StudyStreak(
        days = prefs?.getInt("streak_days_$userId", 0) ?: 0,
        lastActiveDate = prefs?.getString("streak_date_$userId", "").orEmpty(),
        bestDays = prefs?.getInt("streak_best_$userId", 0) ?: 0
    )

    fun refreshStudyStreak() {
        val user = currentUser.value
        val streak = if (user?.role == UserRole.STUDENT) storedStudyStreak(user.id) else StudyStreak()
        studyStreakDays.intValue = streak.visibleDays(LocalDate.now())
        lastActiveDate.value = streak.lastActiveDate
        bestStudyStreakDays.intValue = streak.bestDays
    }

    fun recordMaterialOpened(itemId: Int) {
        if (materials.any { it.id == itemId }) checkAndUpdateStreak()
    }

    // Перевірка, чи належить пошта домену ПНУ
    fun isUniversityEmail(email: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val isValidEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()
        return isValidEmail && (cleanEmail.endsWith("@pnu.edu.ua") || cleanEmail.endsWith("@stefanyk.edu.ua"))
    }

    // Перевірка пошти викладача
    fun isTeacherEmailAllowed(email: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        return isUniversityEmail(cleanEmail) && allowedTeacherEmails.contains(cleanEmail)
    }

    // Додавання пошти викладача
    fun addTeacherEmail(email: String, onComplete: (Result<Unit>) -> Unit = {}) {
        val cleanEmail = email.trim().lowercase()
        val database = firestore
        if (!isUniversityEmail(cleanEmail)) {
            onComplete(Result.failure(IllegalArgumentException("Вкажіть коректну університетську пошту.")))
            return
        }
        if (database == null) {
            onComplete(Result.failure(IllegalStateException("Firebase не налаштовано.")))
            return
        }
        database.collection("allowed_teachers").document(cleanEmail).set(mapOf("email" to cleanEmail))
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    // Видалення пошти викладача
    fun removeTeacherEmail(email: String, onComplete: (Result<Unit>) -> Unit = {}) {
        val cleanEmail = email.trim().lowercase()
        val database = firestore
        if (database == null) {
            onComplete(Result.failure(IllegalStateException("Firebase не налаштовано.")))
            return
        }
        database.collection("allowed_teachers").document(cleanEmail).delete()
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }


    internal val securityAuth: FirebaseAuth? get() = firebaseAuth

    internal fun acceptSecureUser(user: User) {
        currentUser.value = user
        loadFavorites(user.id)
        listenToMaterials()
        if (user.role == UserRole.ADMIN) listenToAllowedTeachers()
    }

    fun registerUser(fullName: String, email: String, password: String, role: UserRole,
        course: String = "1", specialty: String? = null, group: String? = null,
        department: String? = null, captchaId: String = "", captchaAnswer: String = "",
        onComplete: (Result<User>) -> Unit
    ) = SecureAuth.register(fullName, email, password, role, course, specialty, group, department,
        captchaId, captchaAnswer, onComplete)

    fun loginUser(email: String, passwordInput: String, onComplete: (Result<User>) -> Unit) =
        SecureAuth.login(email, passwordInput, onComplete)

    fun loginWithGoogleIdToken(idToken: String, onComplete: (Result<Pair<User, Boolean>>) -> Unit) =
        SecureAuth.google(idToken, onComplete)

    fun sendPasswordReset(email: String, onComplete: (Result<Unit>) -> Unit) =
        SecureAuth.reset(email, onComplete)

    // Вихід з акаунту
    fun logout() {
        SecurityApi.cancelChallenge()
        reactionWatches.values.forEach { it.listener?.remove() }
        reactionWatches.clear()
        materialReactions.clear()
        reactionErrors.clear()
        firebaseAuth?.signOut()
        materialsListener?.remove()
        materialsListener = null
        allowedTeachersListener?.remove()
        allowedTeachersListener = null
        currentUser.value = null
        progressLoadVersion++
        refreshStudyStreak()
        materials.indices.forEach { index ->
            materials[index] = materials[index].copy(isCompleted = false, isFavorite = false)
        }
    }

    // Перемикання "Обране"
    fun toggleFavorite(itemId: Int) {
        val userId = currentUser.value?.id ?: return
        val key = "favorites_$userId"
        val ids = prefs?.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val id = itemId.toString()
        val isFavorite = if (ids.remove(id)) false else {
            ids.add(id)
            true
        }
        prefs?.edit()?.putStringSet(key, ids)?.apply()
        val index = materials.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = materials[index]
            val updatedItem = item.copy(isFavorite = isFavorite)
            materials[index] = updatedItem
            checkAndUpdateStreak()
            saveToDisk()
        }
        val favoriteDoc = firestore?.collection("users")?.document(userId)
            ?.collection("favorites")?.document(id)
        if (isFavorite) favoriteDoc?.set(mapOf("materialId" to itemId)) else favoriteDoc?.delete()
    }

    private fun loadFavorites(userId: String) {
        refreshStudyStreak()
        val loadVersion = ++progressLoadVersion
        fun applyIds(ids: Set<String>) {
            if (currentUser.value?.id != userId) return
            prefs?.edit()?.putStringSet("favorites_$userId", ids)?.apply()
            materials.indices.forEach { index ->
                val item = materials[index]
                val favorite = item.id.toString() in ids
                if (item.isFavorite != favorite) materials[index] = item.copy(isFavorite = favorite)
            }
        }

        val localIds = prefs?.getStringSet("favorites_$userId", emptySet()).orEmpty()
        applyIds(localIds)
        firestore?.collection("users")?.document(userId)?.collection("favorites")?.get()
            ?.addOnSuccessListener { snapshot ->
                applyIds(snapshot.documents.mapNotNull { it.id.toIntOrNull() }.map(Int::toString).toSet())
            }

        fun applyCompletedIds(ids: Set<String>) {
            if (currentUser.value?.id != userId) return
            prefs?.edit()?.putStringSet("completed_$userId", ids)?.apply()
            materials.indices.forEach { index ->
                val item = materials[index]
                val completed = currentUser.value?.role == UserRole.STUDENT && item.id.toString() in ids
                if (item.isCompleted != completed) materials[index] = item.copy(isCompleted = completed)
            }
        }

        applyCompletedIds(prefs?.getStringSet("completed_$userId", emptySet()).orEmpty())
        firestore?.collection("users")?.document(userId)?.collection("completed")?.get()
            ?.addOnSuccessListener { snapshot ->
                if (loadVersion != progressLoadVersion) return@addOnSuccessListener
                applyCompletedIds(snapshot.documents.mapNotNull { it.id.toIntOrNull() }.map(Int::toString).toSet())
            }
    }

    // Completed state belongs to the signed-in student, not to the shared material record.
    fun toggleCompleted(itemId: Int) {
        val userId = currentUser.value?.takeIf { it.role == UserRole.STUDENT }?.id ?: return
        if (materials.none { it.id == itemId }) return
        progressLoadVersion++
        val key = "completed_$userId"
        val ids = prefs?.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val id = itemId.toString()
        val isCompleted = if (ids.remove(id)) false else {
            ids.add(id)
            true
        }
        prefs?.edit()?.putStringSet(key, ids)?.apply()
        val index = materials.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = materials[index]
            val updatedItem = item.copy(isCompleted = isCompleted)
            materials[index] = updatedItem
            checkAndUpdateStreak()
            saveToDisk()
        }
        val completedDoc = firestore?.collection("users")?.document(userId)
            ?.collection("completed")?.document(id)
        if (isCompleted) completedDoc?.set(mapOf("materialId" to itemId)) else completedDoc?.delete()
    }

    /** Downloads a remote file or copies a selected local document into app-private storage. */
    suspend fun downloadMaterial(context: Context, itemId: Int): Result<Uri> {
        return downloadMutex.withLock {
            getDownloadedFileUri(itemId)?.let { return@withLock Result.success(it) }
            downloadMaterialOnce(context, itemId)
        }
    }

    private suspend fun downloadMaterialOnce(context: Context, itemId: Int): Result<Uri> {
        val item = materials.firstOrNull { it.id == itemId }
            ?: return Result.failure(IllegalArgumentException("Матеріал не знайдено"))
        val source = item.linkUrl?.takeIf(String::isNotBlank)
            ?: return Result.failure(IllegalStateException("Для матеріалу не вказано файл або посилання"))

        val localUri = try {
            withContext(Dispatchers.IO) {
                val directory = java.io.File(context.applicationContext.filesDir, if (isDemoMode.value) "demo_materials" else "materials")
                if (!directory.exists() && !directory.mkdirs()) error("Не вдалося створити каталог для файлів")
                val extension = item.fileType.lowercase().filter(Char::isLetterOrDigit).ifBlank { "bin" }
                val destination = java.io.File(directory, "${item.id}.$extension")
                val temporary = java.io.File(directory, "${item.id}.$extension.part")
                val input = if (source.startsWith("http://") || source.startsWith("https://")) {
                    val connection = java.net.URL(source).openConnection().apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                    connection.getInputStream()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(source))
                        ?: error("Не вдалося відкрити файл")
                }
                try {
                    input.use { stream ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var totalBytes = 0L
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                if (totalBytes > 50L * 1024L * 1024L) error("Файл перевищує ліміт 50 МБ")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (destination.exists() && !destination.delete()) error("Не вдалося оновити збережений файл")
                    if (!temporary.renameTo(destination)) error("Не вдалося зберегти завантажений файл")
                } catch (error: Exception) {
                    temporary.delete()
                    throw error
                }
                Uri.fromFile(destination)
            }
        } catch (error: Exception) {
            return Result.failure(error)
        }

        withContext(Dispatchers.Main) {
            if (!downloadedItemIds.contains(itemId)) downloadedItemIds.add(itemId)
            prefs?.edit()
                ?.putString("downloaded_file_$itemId", localUri.toString())
                ?.putStringSet("downloaded_item_ids", downloadedItemIds.map(Int::toString).toSet())
                ?.apply()

            val index = materials.indexOfFirst { it.id == itemId }
            if (index != -1) {
                val updated = materials[index].copy(downloadCount = materials[index].downloadCount + 1)
                materials[index] = updated
            }
            checkAndUpdateStreak()
            saveToDisk()
        }
        withContext(Dispatchers.Main) { recordMaterialActivity(itemId, "DOWNLOAD") }
        return Result.success(localUri)
    }

    fun getDownloadedFileUri(itemId: Int): Uri? {
        val uri = prefs?.getString("downloaded_file_$itemId", null)?.let(Uri::parse) ?: return null
        return uri.takeIf { it.scheme == "file" && it.path?.let { path ->
            val file = java.io.File(path)
            file.isFile && file.length() > 0
        } == true }
    }

    fun isMaterialDownloaded(itemId: Int): Boolean =
        itemId in downloadedItemIds && getDownloadedFileUri(itemId) != null

    /** A read cache is separate from the student's explicitly downloaded/offline list. */
    suspend fun prepareMaterialForReading(context: Context, item: CatalogItem): Result<Uri> = readerMutex.withLock {
        getDownloadedFileUri(item.id)?.let { return@withLock Result.success(it) }
        val source = item.linkUrl?.takeIf(String::isNotBlank)
            ?: return@withLock Result.failure(IllegalStateException("Для матеріалу не вказано файл або посилання."))
        val uri = Uri.parse(source)
        if (uri.scheme == "content" || uri.scheme == "file") return@withLock Result.success(uri)
        if (uri.scheme != "http" && uri.scheme != "https") {
            return@withLock Result.failure(IllegalArgumentException("Непідтримуване посилання на документ."))
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = java.io.File(context.cacheDir, if (isDemoMode.value) "demo_reader" else "reader")
                if (!folder.exists() && !folder.mkdirs()) error("Не вдалося створити кеш читання.")
                val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val destination = java.io.File(folder, "${item.id}-$hash")
                if (destination.isFile && destination.length() > 0) return@runCatching Uri.fromFile(destination)
                val temporary = java.io.File(folder, "${destination.name}.part")
                val connection = java.net.URL(source).openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    if (connection.responseCode !in 200..299) error("Не вдалося отримати документ (HTTP ${connection.responseCode}).")
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var size = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                size += read
                                if (size > 50L * 1024 * 1024) error("Документ перевищує ліміт 50 МБ.")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (temporary.length() == 0L) error("Отримано порожній документ.")
                    if (!temporary.renameTo(destination)) error("Не вдалося зберегти документ для читання.")
                    Uri.fromFile(destination)
                } finally {
                    connection.disconnect()
                    temporary.delete()
                }
            }
        }
    }

    // CREATE: збереження матеріалу в локальному кеші та Firestore.
    fun addMaterial(
        title: String,
        type: String,
        subject: String,
        teacherName: String,
        fileType: String,
        fileSize: String,
        semester: Int,
        course: String = "1 курс",
        tags: String = "",
        description: String = "",
        linkUrl: String? = null,
        fileUri: Uri? = null,
        onComplete: (Result<CatalogItem>) -> Unit = {}
    ) {
        val author = currentUser.value
        val database = firestore
        if (author == null || database == null) {
            onComplete(Result.failure(IllegalStateException("Потрібен вхід у Firebase для публікації матеріалу")))
            return
        }
        val newId = (materials.maxOfOrNull { it.id } ?: 0) + 1
        val newItem = CatalogItem(
            id = newId,
            title = title,
            type = type,
            subject = subject,
            teacher = teacherName,
            teacherId = currentUser.value?.id ?: "",
            date = "Сьогодні",
            fileType = fileType,
            fileSize = fileSize,
            semester = semester,
            course = course,
            tags = tags,
            description = description,
            linkUrl = fileUri?.toString() ?: linkUrl,
            isFavorite = false,
            isCompleted = false,
            downloadCount = 0
        )
        materials.add(0, newItem)
        saveToDisk()
        fun fail(error: Exception) {
            materials.removeAll { it.id == newId }
            saveToDisk()
            onComplete(Result.failure(error))
        }
        fun publish(item: CatalogItem, afterFailure: () -> Unit = {}) {
            database.collection("materials").document(newId.toString())
                .set(catalogItemToMap(item))
                .addOnSuccessListener {
                    val index = materials.indexOfFirst { it.id == newId }
                    if (index != -1) materials[index] = item
                    saveToDisk()
                    onComplete(Result.success(item))
                }
                .addOnFailureListener {
                    afterFailure()
                    fail(it)
                }
        }

        if (fileUri == null) {
            publish(newItem)
        } else {
            val storage = firebaseStorage
            if (storage == null) {
                fail(IllegalStateException("Firebase Storage не налаштовано"))
                return
            }
            val extension = fileType.lowercase().filter(Char::isLetterOrDigit).ifBlank { "bin" }
            val reference = storage.reference.child("materials/${author.id}/$newId.$extension")
            reference.putFile(fileUri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Не вдалося завантажити файл")
                    reference.downloadUrl
                }
                .addOnSuccessListener { downloadUrl ->
                    publish(newItem.copy(linkUrl = downloadUrl.toString())) {
                        reference.delete()
                    }
                }
                .addOnFailureListener { fail(it) }
        }
    }

    // UPDATE: оновлення локального кешу та Firestore.
    fun updateMaterial(
        id: Int,
        title: String,
        type: String,
        subject: String,
        teacherName: String,
        fileType: String,
        fileSize: String,
        semester: Int,
        course: String,
        tags: String,
        description: String,
        linkUrl: String?,
        fileUri: Uri? = null,
        onComplete: (Result<CatalogItem>) -> Unit = {}
    ) {
        val database = firestore
        val author = currentUser.value
        if (database == null || author == null) {
            onComplete(Result.failure(IllegalStateException("Потрібен вхід у Firebase для редагування матеріалу")))
            return
        }
        val index = materials.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = materials[index]
            val updated = existing.copy(
                title = title,
                type = type,
                subject = subject,
                teacher = teacherName,
                fileType = fileType,
                fileSize = fileSize,
                semester = semester,
                course = course,
                tags = tags,
                description = description,
                linkUrl = fileUri?.toString() ?: linkUrl
            )
            materials[index] = updated
            saveToDisk()
            fun rollback(error: Exception) {
                val currentIndex = materials.indexOfFirst { it.id == id }
                if (currentIndex != -1) materials[currentIndex] = existing
                saveToDisk()
                onComplete(Result.failure(error))
            }
            fun save(item: CatalogItem, afterSaved: () -> Unit = {}, afterFailure: () -> Unit = {}) {
                database.collection("materials").document(id.toString())
                    .set(catalogItemToMap(item))
                    .addOnSuccessListener {
                        val currentIndex = materials.indexOfFirst { it.id == id }
                        if (currentIndex != -1) materials[currentIndex] = item
                        saveToDisk()
                        onComplete(Result.success(item))
                    }
                    .addOnSuccessListener { afterSaved() }
                    .addOnFailureListener {
                        afterFailure()
                        rollback(it)
                    }
            }

            if (fileUri == null) {
                save(updated)
            } else {
                val storage = firebaseStorage
                if (storage == null) {
                    rollback(IllegalStateException("Firebase Storage не налаштовано"))
                    return
                }
                val oldExtension = existing.fileType.lowercase().filter(Char::isLetterOrDigit).ifBlank { "bin" }
                val extension = fileType.lowercase().filter(Char::isLetterOrDigit).ifBlank { "bin" }
                val reference = storage.reference.child("materials/${existing.teacherId}/$id.$extension")
                reference.putFile(fileUri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Не вдалося завантажити файл")
                        reference.downloadUrl
                    }
                    .addOnSuccessListener { downloadUrl ->
                        save(
                            updated.copy(linkUrl = downloadUrl.toString()),
                            afterSaved = {
                                if (oldExtension != extension) {
                                    storage.reference.child("materials/${existing.teacherId}/$id.$oldExtension").delete()
                                }
                            },
                            afterFailure = { reference.delete() }
                        )
                    }
                    .addOnFailureListener { rollback(it) }
            }
        } else {
            onComplete(Result.failure(IllegalArgumentException("Матеріал не знайдено")))
        }
    }

    // DELETE: видалення з кешу та Firestore.
    fun deleteMaterial(itemId: Int) {
        val deletedMaterial = materials.firstOrNull { it.id == itemId }
        materials.removeAll { it.id == itemId }
        downloadedItemIds.removeAll { it == itemId }
        getDownloadedFileUri(itemId)?.path?.let { path -> java.io.File(path).delete() }
        prefs?.edit()
            ?.remove("downloaded_file_$itemId")
            ?.putStringSet("downloaded_item_ids", downloadedItemIds.map(Int::toString).toSet())
            ?.apply()
        saveToDisk()

        firestore?.collection("materials")?.document(itemId.toString())?.delete()
        if (deletedMaterial?.linkUrl?.contains("firebasestorage.googleapis.com") == true) {
            val extension = deletedMaterial.fileType.lowercase().filter(Char::isLetterOrDigit).ifBlank { "bin" }
            firebaseStorage?.reference?.child("materials/${deletedMaterial.teacherId}/$itemId.$extension")?.delete()
        }
    }

    // Розрахунок статистики та досягнень (Badges)
    fun getCompletedCount(): Int = materials.count { it.isCompleted }

    fun getOverallProgressRatio(): Float {
        return getStudyProgress().ratio
    }

    fun getStudyProgress(subject: String? = null): StudyProgress {
        val relevant = if (subject == null) materials.toList() else materials.filter { it.subject == subject }
        return StudyProgress(relevant.count { it.isCompleted }, relevant.size)
    }

    fun getSubjectProgressRatio(subjectName: String): Pair<Int, Int> {
        val progress = getStudyProgress(subjectName)
        return progress.completed to progress.total
    }

    fun getAchievementBadges(): List<AchievementBadge> {
        val completedCount = getCompletedCount()
        val streak = bestStudyStreakDays.intValue

        return listOf(
            AchievementBadge(
                id = "first_step",
                icon = "🎯",
                title = "Перший крок",
                description = "Опрацьовано 1-й навчальний матеріал",
                isUnlocked = completedCount >= 1
            ),
            AchievementBadge(
                id = "erudite",
                icon = "📚",
                title = "Ерудит",
                description = "Опрацьовано 5 навчальних робіт",
                isUnlocked = completedCount >= 5
            ),
            AchievementBadge(
                id = "fire_streak",
                icon = "🔥",
                title = "Вогняний старт",
                description = "Взаємодія з матеріалами 3 дні поспіль. Враховується особистий рекорд.",
                isUnlocked = streak >= 3
            )
        )
    }
}
