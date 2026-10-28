package com.island.dynamicisland.live

/** Compare notification meaning, leaving scores and delivery-stage wording intact. */
internal fun significantLiveUpdateKey(update: LiveUpdate): String {
    if (update.isNavigation) return navigationDirectionKey(update.title)
    update.sportsScore?.let { score ->
        // ESPN rotates leader panels and game context. Only actual scoreboard
        // changes should reopen the sports card, regardless of notification text.
        return listOf("sports", score.teamOne.orEmpty(), score.teamTwo.orEmpty(),
            score.scoreOne, score.scoreTwo).joinToString("\n")
    }
    fun normalize(value: String): String = value.lowercase(java.util.Locale.ROOT)
        // Clock ticks, arrival times, countdowns, distances and percentages are routine updates.
        .replace(Regex("""\b\d{1,3}:\d{2}(?::\d{2})?(?:[.,]\d+)?\s*(?:[ap]\.?m\.?)?"""), " <time> ")
        .replace(Regex("""\b\d+(?:[.,]\d+)?\s*(?:hours?|hrs?|minutes?|mins?|seconds?|secs?|feet|foot|ft|miles?|mi|meters?|metres?|m|kilometers?|kilometres?|km)\b"""), " <measure> ")
        .replace(Regex("""\b\d+(?:[.,]\d+)?\s*%"""), " <progress> ")
        .replace(Regex("""\s+"""), " ").trim()
    val stage = activityStage(update)
    if (stage != null) {
        val completion = if (stage in setOf("downloading", "uploading") &&
            update.progress?.let { it >= 1f } == true) "complete" else stage
        return "stage:$completion"
    }
    if (update.progress != null || update.indeterminate) {
        val progressState = if (update.progress?.let { it >= 1f } == true) "complete" else "active"
        return "progress:$progressState:${normalize(update.title)}"
    }
    // Supporting details (addresses, file sizes, device diagnostics) remain
    // visible, but changes to them alone should not interrupt the user.
    return listOf(update.title, update.status, update.text.lineSequence().firstOrNull().orEmpty())
        .joinToString("\n", transform = ::normalize)
}

/** Ignore re-posts and noisy fields; removal resets the baseline for the next activity. */
internal class SignificantLiveUpdateTracker {
    private val known = mutableMapOf<String, String>()

    fun changedActivity(updates: List<LiveUpdate>, suppressedKeys: Set<String> = emptySet()): String? {
        known.keys.retainAll(updates.map { it.key }.toSet())
        var changed: String? = null
        for (update in updates) {
            val signature = significantLiveUpdateKey(update)
            // Temporary Maps service states must not replace the last actual direction.
            if (signature.isBlank()) continue
            // Clock posts its stopwatch when leaving the app; keep it compact until held.
            if (update.stopwatch == null && known[update.key] != signature && update.key !in suppressedKeys) changed = update.key
            known[update.key] = signature
        }
        return changed
    }
}

/** Called only for a published auto-expansion event, never for routine refreshes. */
internal fun autoExpansionTextChanges(before: LiveUpdate, after: LiveUpdate): Set<String> {
    val baseline = significantLiveUpdateKey(before)
    val titleTriggered = significantLiveUpdateKey(before.copy(title = after.title)) != baseline
    val textTriggered = significantLiveUpdateKey(before.copy(text = after.text)) != baseline
    return buildSet {
        if ((titleTriggered || textTriggered) && activityHeadline(before) != activityHeadline(after)) add("headline")
        if (textTriggered && before.text != after.text) add("text")
    }
}
