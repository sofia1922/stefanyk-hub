package com.example.stefanykhub.model

data class MaterialActivity(val studentId: String, val kind: String, val group: String, val time: Long)
data class AnalyticsSettings(val group: String = "", val groupSize: Int = 0, val deadline: Long = 0)
data class MaterialAnalytics(val events: List<MaterialActivity>) {
    val views get() = events.count { it.kind == "VIEW" }
    val downloads get() = events.count { it.kind == "DOWNLOAD" }
    val readers get() = events.filter { it.kind == "VIEW" }.map { it.studentId }.distinct().size
    fun groupDownloads(group: String) = events.filter {
        it.kind == "DOWNLOAD" && it.group.trim().equals(group.trim(), ignoreCase = true)
    }.map { it.studentId }.distinct().size
    fun percent(settings: AnalyticsSettings): Int? {
        val count = groupDownloads(settings.group)
        return if (settings.group.isBlank() || settings.groupSize <= 0 || count > settings.groupSize) null
        else (count * 100L / settings.groupSize).toInt()
    }
    fun beforeDeadline(deadline: Long) = events.count {
        it.kind == "DOWNLOAD" && it.time >= deadline - 2 * 3_600_000L && it.time < deadline
    }

    fun hours(end: Long): List<Int> = List(24) { index ->
        val start = end - (24 - index) * 3_600_000L
        events.count { it.kind == "DOWNLOAD" && it.time >= start && it.time < start + 3_600_000L }
    }
}
