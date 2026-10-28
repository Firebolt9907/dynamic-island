package com.island.dynamicisland.live

/** Cheap flags-only rejection before loading notification styles, icons, or RemoteViews. */
internal fun canBeLiveActivityNotification(
    ongoing: Boolean,
    groupSummary: Boolean,
    mediaSession: Boolean,
    sportsFinal: Boolean = false,
): Boolean = (ongoing || sportsFinal) && !groupSummary && !mediaSession

/** Pausing a recognized stopwatch or displaying a completed game score keeps its activity alive until removed. */
internal fun isLiveActivityNotification(
    ongoing: Boolean,
    groupSummary: Boolean,
    mediaSession: Boolean,
    autoCancel: Boolean,
    pausedStopwatch: Boolean = false,
    sportsFinal: Boolean = false,
): Boolean = canBeLiveActivityNotification(ongoing, groupSummary, mediaSession, sportsFinal) &&
    (!autoCancel || pausedStopwatch || sportsFinal)
