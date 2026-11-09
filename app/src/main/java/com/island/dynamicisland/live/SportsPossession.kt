package com.island.dynamicisland.live

data class SportsPossession(val team: Int, val label: String)

/** Generic team indicators include winner arrows; only explicit turn semantics qualify. */
internal fun sportsPossessionLabel(field: String, description: String): String? {
    val value = "$field $description".lowercase()
    if (Regex("winner|victory|favorite").containsMatchIn(value)) return null
    return when {
        value.contains("possession") || value.contains("has the ball") -> "Possession"
        Regex("serving|server|(?:^|[_\\s])serve(?:$|[_\\s])").containsMatchIn(value) -> "Serving"
        value.contains("batting") || value.contains("at bat") -> "Batting"
        else -> null
    }
}

internal fun resolveSportsPossession(candidates: List<SportsPossession>): SportsPossession? =
    candidates.filter { it.team in 1..2 }.distinct().let { turns ->
        turns.firstOrNull()?.takeIf { turns.map { it.team }.distinct().size == 1 }
    }

/** Compare silhouettes, allowing source tint/opacity and small antialiasing differences. */
internal fun sportsTurnIconMatches(expected: List<Int>, actual: List<Int>): Boolean {
    if (expected.size != actual.size || expected.isEmpty()) return false
    val expectedPeak = expected.maxOrNull() ?: 0
    val actualPeak = actual.maxOrNull() ?: 0
    if (expectedPeak == 0 || actualPeak == 0) return false
    var intersection = 0
    var union = 0
    expected.indices.forEach { index ->
        val a = expected[index] >= expectedPeak * .5f
        val b = actual[index] >= actualPeak * .5f
        if (a || b) union++
        if (a && b) intersection++
    }
    return union >= 8 && intersection.toFloat() / union >= .92f
}
