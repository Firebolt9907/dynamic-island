package com.island.dynamicisland.live

internal data class LiveUpdateText(val body: String, val detail: String)

/** Expanded text replaces compact text; summary/status fields aren't extra paragraphs. */
internal fun liveUpdateText(title: String, body: String, bigText: String,
                            lines: List<String>, subText: String): LiveUpdateText {
    fun normalized(text: String) = text.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
    val expanded = bigText.trim().ifBlank {
        lines.map(String::trim).filter(String::isNotBlank).distinct().joinToString("\n")
    }.ifBlank { body.trim() }
    val main = expanded.takeUnless { normalized(it) == normalized(title) }.orEmpty()
    val detail = subText.trim().takeUnless {
        it.isBlank() || normalized(main).contains(normalized(it)) || normalized(title).contains(normalized(it))
    }.orEmpty()
    return LiveUpdateText(main, detail)
}
