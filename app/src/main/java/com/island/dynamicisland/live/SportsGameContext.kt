package com.island.dynamicisland.live

data class FootballFieldPosition(
    val sourceText: String,
    /** Team one's goal line is 0; team two's is 100. */
    val yards: Float,
    val firstDownYards: Float?,
    val attackingTeam: Int?,
)

/** Require a down-and-distance before interpreting a trailing number as a yard line. */
internal fun footballFieldPosition(score: SportsScore): FootballFieldPosition? {
    if (isSportsFinalStatus(score.gameStatus)) return null
    score.retainedFootballField?.let { return it }
    val down = Regex("""\b[1-4](?:st|nd|rd|th)\s*(?:&|and)\s*(\d{1,2}|goal)\b""", RegexOption.IGNORE_CASE)
        .find(score.gameStatus) ?: return null
    val lines = score.gameStatus.lines().map { it.trim() }
    val field = lines.firstNotNullOfOrNull { line ->
        Regex("""^(.+?)\s+(\d{1,2})$""").matchEntire(line)?.let { match ->
            val side = when {
                match.groupValues[1].equals(score.teamOne, true) -> 1
                match.groupValues[1].equals(score.teamTwo, true) -> 2
                else -> return@let null
            }
            val yard = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..50 } ?: return@let null
            line to if (side == 1) yard.toFloat() else 100f - yard
        } ?: if (line.matches(Regex("""(?i)^(?:midfield\s*)?50$"""))) line to 50f else null
    } ?: return null
    val attack = score.possession?.takeIf { it.label == "Possession" }?.team
    val distance = down.groupValues[1].toIntOrNull()
    val target = when (attack) {
        1 -> if (distance == null) 100f else (field.second + distance).coerceAtMost(100f)
        2 -> if (distance == null) 0f else (field.second - distance).coerceAtLeast(0f)
        else -> null
    }
    return FootballFieldPosition(field.first, field.second, target, attack)
}

data class SportsSetScore(val team: Int?, val text: String)

internal fun isSportsSetScoreField(name: String): Boolean =
    Regex("""(?:^|_)set(?:s|_\d+)?_(?:score|scores)(?:_|$)|(?:^|_)score_set(?:_|$)""", RegexOption.IGNORE_CASE)
        .containsMatchIn(name)

internal fun baseballOuts(status: String): Int? =
    Regex("""\b([0-3])\s+outs?\b""", RegexOption.IGNORE_CASE)
        .find(status)?.groupValues?.get(1)?.toIntOrNull()
