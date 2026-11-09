package com.island.dynamicisland.ui.island

/** A central activity plus at most two detached activities, left then right. */
internal data class IslandActivities(val primary: String?, val secondaries: List<String>) {
    /** Membership regardless of role; a change here is an arrival or a departure. */
    val keys: Set<String> get() = listOfNotNull(primary).toSet() + secondaries
    companion object {
        const val MUSIC = "music"
        fun select(liveKeys: List<String>, hasMusic: Boolean): IslandActivities {
            val keys = liveKeys.distinct()
            val primary = keys.firstOrNull() ?: MUSIC.takeIf { hasMusic }
            return IslandActivities(primary, (keys.drop(1) + listOfNotNull(MUSIC.takeIf { hasMusic && primary != MUSIC })).take(2))
        }
    }
}

data class ActivityTouchBounds(val key: String, val left: Int, val top: Int, val width: Int, val height: Int)

internal data class ActivityBubblePosition(val left: Float, val topOffset: Float)

/** Three activities occupy one row: second on the left, third on the right. */
internal fun activityBubblePosition(index: Int, primaryLeft: Float, primaryWidth: Float, size: Float): ActivityBubblePosition {
    require(index in 0..1)
    return ActivityBubblePosition(if (index == 0) primaryLeft - size - 6f else primaryLeft + primaryWidth + 6f, 0f)
}

/** Missing foreground/session information never suppresses music. */
internal fun isMusicSourceOpen(foregroundPackage: String?, musicPackage: String?): Boolean =
    !musicPackage.isNullOrBlank() && foregroundPackage == musicPackage

/** Suppression is temporary: the repository keeps the activity until its source ends it. */
internal fun isLiveActivitySourceOpen(foregroundPackage: String?, activity: com.island.dynamicisland.live.LiveUpdate): Boolean =
    activity.packageName.isNotBlank() && foregroundPackage == activity.packageName
