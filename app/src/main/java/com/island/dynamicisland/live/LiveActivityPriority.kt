package com.island.dynamicisland.live

/** Rank attention needs; routine notification refreshes must not reorder equal peers. */
internal enum class LiveActivityPriority {
    NAVIGATION, ONGOING, RUNNING_STOPWATCH, PAUSED_STOPWATCH
}

internal fun liveActivityPriority(update: LiveUpdate): LiveActivityPriority = when {
    // Match the Maps fallback used for foreground suppression: some service updates
    // omit the navigation category while a route remains active.
    update.isNavigation || update.packageName == "com.google.android.apps.maps" -> LiveActivityPriority.NAVIGATION
    update.stopwatch == null -> LiveActivityPriority.ONGOING
    update.stopwatch.startedAtElapsedMs != null -> LiveActivityPriority.RUNNING_STOPWATCH
    else -> LiveActivityPriority.PAUSED_STOPWATCH
}
