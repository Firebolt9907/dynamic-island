package com.island.dynamicisland.live

/** Short critical status (shortCriticalText), progress percentage, duration, or sports score. */
internal fun compactLiveStatus(status: String, progress: Float?, text: String, details: String): String {
    fun compact(value: String) = value.trim().replace(Regex("\\s+"), " ")
    val critical = compact(status)
    if (critical.isNotEmpty() && critical.length <= 12) return critical
    if (progress != null) return "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
    // Only extract an explicit duration, rather than guessing an ETA from arbitrary numbers.
    val duration = Regex("(?<![\\p{L}\\p{N}.,])(\\d+(?:[.,]\\d+)*)\\s*(minutes?|mins?|hours?|hrs?|seconds?|secs?)\\b", RegexOption.IGNORE_CASE)
        .find(listOf(critical, text, details).joinToString("\n"))
    if (duration != null) {
        val unit = when (duration.groupValues[2].lowercase(java.util.Locale.ROOT).first()) {
            'h' -> "hr"
            's' -> "sec"
            else -> "min"
        }
        return "${duration.groupValues[1]} $unit"
    }
    val score = Regex("""(?<!\S)(\d{1,3}\s*[-–]\s*\d{1,3})(?!\S)""")
        .find(listOf(text, details).joinToString("\n"))
    if (score != null) {
        val s = compact(score.groupValues[1])
        if (s.length <= 12) return s
    }
    return ""
}

/** Reject non-direction text so routing and location-access updates do not expand the island. */
private val nonDirectionTerms = listOf("rerouting", "waiting for location", "searching for gps", "gps signal lost")

/** Retain grouped numbers without altering whole navigation miles or metres. */
internal fun nextTurnDistance(vararg sources: String): String? {
    val nonDirectionRegex = Regex("""\b(?:${nonDirectionTerms.joinToString("|")})\b""", RegexOption.IGNORE_CASE)
    fun find(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
        .find(sources.filter { it.isNotBlank() && !it.contains(nonDirectionRegex) }.joinToString("\n"))
    val standard = find("""(?<![\p{L}\p{N}.,])(\d+(?:[.,]\d+)*)\s*(feet|foot|ft|miles?|mi|meters?|metres?|m|kilometers?|kilometres?|km)\b""")
    if (standard != null) {
        val unit = when (standard.groupValues[2].lowercase(java.util.Locale.ROOT).first()) {
            'f' -> "ft"
            'm' -> if (standard.groupValues[2].startsWith("mi", ignoreCase = true)) "mi" else "m"
            else -> "km"
        }
        return "${standard.groupValues[1]} $unit"
    }
    return null
}

/** Normalize distance ticks away so changing meters/feet does not count as a new direction. */
internal fun navigationDirectionKey(title: String): String = title.lowercase(java.util.Locale.ROOT)
    .replace(Regex("""\b\d+(?:[.,]\d+)*\s*(?:feet|foot|ft|miles?|mi|meters?|metres?|m|kilometers?|kilometres?|km)\b"""), "")
    .replace(Regex("""\s+"""), " ").trim()
    .takeUnless { value -> nonDirectionTerms.any { value.contains(it) } }.orEmpty()
