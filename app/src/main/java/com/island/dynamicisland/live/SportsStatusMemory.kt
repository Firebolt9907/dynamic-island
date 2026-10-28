package com.island.dynamicisland.live

/** Short-lived, notification-local memory for context omitted by rotating sports panels. */
internal class SportsStatusMemory(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val game: List<String?>, val phase: String, val scores: Pair<String, String>,
                             val context: String, val observedAt: Long)
    private val entries = mutableMapOf<String, Entry>()

    fun remember(key: String, score: SportsScore): SportsScore {
        val now = nowMs()
        val game = listOf(score.teamOne, score.teamTwo).map { it?.trim()?.lowercase(java.util.Locale.ROOT) }
        // Clock ticks do not mark a new phase, but quarters, innings, sets,
        // intermissions, and final/delayed states do.
        val phase = score.status.orEmpty().replace(Regex("""\b\d{1,3}:\d{2}(?::\d{2})?\b"""), "")
            .replace(Regex("""[\s–—-]+"""), " ").trim().lowercase(java.util.Locale.ROOT)
        val scores = score.scoreOne to score.scoreTwo
        val previous = entries[key]?.takeIf {
            it.game == game && it.phase == phase && it.scores == scores && now - it.observedAt in 0..120_000
        }
        val terminal = Regex("""\b(final|halftime|half time|intermission|postponed|cancelled|canceled|suspended|delayed|pregame|scheduled)\b""")
            .containsMatchIn(phase)
        val context = score.secondaryStatus?.takeIf { it.isNotBlank() }
        if (terminal) {
            entries.remove(key)
            return score
        }
        if (context != null) {
            entries[key] = Entry(game, phase, scores, context, now)
            return score
        }
        if (previous == null) entries.remove(key)
        // Reusing a panel must not extend its original observation timestamp.
        return if (previous == null) score else score.copy(secondaryStatus = previous.context)
    }

    fun forget(key: String) { entries.remove(key) }
    fun clear() { entries.clear() }
}
