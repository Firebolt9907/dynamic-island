package com.island.dynamicisland.live

/** A play/shot clock is not the period's game clock. Plain TextViews never imply running. */
internal fun isSportsGameClockField(name: String): Boolean {
    val id = name.lowercase()
    if (listOf("shot", "play_clock", "playclock", "timeout", "countdown_to", "pregame").any { it in id }) return false
    return id.contains("game_clock") || id.contains("game_timer") ||
        id.contains("period_clock") || id.contains("match_clock") ||
        id.contains("status_primary") || id == "game_status"
}
