package com.island.dynamicisland.live

/** Scoring explanations are event details, not a rotating player-stat heading. */
internal fun isSportsStatsSection(title: String?, hasPlayers: Boolean): Boolean {
    if (title.isNullOrBlank()) return hasPlayers
    return Regex("""\bleaders\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) ||
        title.trim().lowercase() in setOf("passing", "rushing", "receiving", "batting", "pitching", "stats", "statistics")
}
