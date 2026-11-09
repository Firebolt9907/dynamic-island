package com.island.dynamicisland.live

data class FootballPlay(val fromYards: Float, val toYards: Float, val team: Int) {
    val gain: Int get() = ((toYards - fromYards) * if (team == 1) 1 else -1).toInt()
}

/** Infer only movement within a continuous, identifiable possession. May include penalties. */
internal class FootballPlayHistory {
    private data class Entry(val game: List<String?>, val scores: Pair<String, String>,
                             val phase: String, val position: FootballFieldPosition, val play: FootballPlay?)
    private val entries = mutableMapOf<String, Entry>()

    fun remember(key: String, score: SportsScore): SportsScore {
        if (score.fieldContextStale) {
            val previous = entries[key]?.takeIf { it.game == listOf(score.teamOne, score.teamTwo) }
            return score.copy(lastFootballPlay = previous?.play)
        }
        val field = footballFieldPosition(score)
        if (field == null || field.attackingTeam == null) {
            entries.remove(key)
            return score.copy(lastFootballPlay = null)
        }
        val game = listOf(score.teamOne, score.teamTwo)
        val scores = score.scoreOne to score.scoreTwo
        val phase = score.status.orEmpty().replace(Regex("""\b\d{1,2}:\d{2}(?:[.,]\d+)?\b"""), "")
            .trim().lowercase()
        val previous = entries[key]?.takeIf {
            it.game == game && it.scores == scores && it.phase == phase &&
                it.position.attackingTeam == field.attackingTeam
        }
        val play = when {
            previous == null -> null
            previous.position.yards == field.yards -> previous.play
            else -> FootballPlay(previous.position.yards, field.yards, field.attackingTeam)
        }
        entries[key] = Entry(game, scores, phase, field, play)
        return score.copy(lastFootballPlay = play)
    }

    fun forget(key: String) { entries.remove(key) }
    fun clear() { entries.clear() }
}
