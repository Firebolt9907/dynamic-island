package com.island.dynamicisland.live

/** Lead with the task; a redundant app-name title can yield to its useful body. */
internal fun activityHeadline(update: LiveUpdate): String {
    val title = update.title.trim()
    if (title.isNotEmpty() && !title.equals(update.appName.trim(), ignoreCase = true)) return title
    return update.text.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
        ?: title.ifBlank { update.appName }
}

/** Clearance is inside the content's existing top inset, not added twice. */
internal fun activityCameraInset(cameraSafeTop: Float, contentTop: Float): Float =
    (cameraSafeTop - contentTop).coerceAtLeast(0f)

/** Conservative, explicit stages. Unknown/localized text uses the normal fallback. */
internal fun activityStage(update: LiveUpdate): String? {
    val fields = listOf(update.title, update.status, update.text.lineSequence().firstOrNull().orEmpty())
        .map { it.lowercase(java.util.Locale.ROOT) }
    val stages = listOf(
        "failed" to "failed|failure|error",
        "cancelled" to "cancelled|canceled",
        "paused" to "paused|on hold",
        "complete" to "completed|complete|finished|delivered",
        "arrived" to "has arrived|have arrived|is here|arrived",
        "ready" to "ready for pickup|ready to collect",
        "travelling" to "on the way|en route|out for delivery",
        "preparing" to "preparing|being prepared",
        "arriving" to "arriving|approaching",
        "recording" to "recording|resumed",
        "downloading" to "downloading",
        "uploading" to "uploading",
    )
    // Prefer the explicit title over secondary instructions. Negated/future
    // statements are not observations of a task transition.
    for (field in fields) {
        if (Regex("\\b(not|never|incomplete|will|when|once|until)\\b").containsMatchIn(field)) continue
        stages.firstOrNull { (_, words) -> Regex("\\b(?:$words)\\b").containsMatchIn(field) }
            ?.let { return it.first }
    }
    return null
}
