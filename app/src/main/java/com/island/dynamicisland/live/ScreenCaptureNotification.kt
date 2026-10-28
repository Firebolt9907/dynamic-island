package com.island.dynamicisland.live

enum class ScreenCaptureKind { RECORDING, SHARING }

/** Called only after ongoing, summary, media and auto-cancel eligibility checks. */
internal fun isScreenCaptureNotification(packageName: String, channelId: String?, texts: List<String>): Boolean {
    // System UI uses this channel for both active and saved recordings; the caller's
    // ongoing check is essential to keep saved recordings out of the island.
    if (packageName == "com.android.systemui" && channelId == "screen_record") return true
    return texts.any { screenCaptureStatus.matches(it.trim()) && !completedCapture.containsMatchIn(it) }
}

private val screenCaptureStatus = Regex(
    "(?:screen recording|recording (?:your |the )?screen|screen sharing|sharing (?:your |the )?screen|" +
        ".+ is (?:recording|sharing|capturing) (?:your |the )?screen)(?:[ .:–—-].*)?",
    RegexOption.IGNORE_CASE,
)

private val completedCapture = Regex("\\b(?:saved|ended|stopped|finished|complete|completed)\\b", RegexOption.IGNORE_CASE)
