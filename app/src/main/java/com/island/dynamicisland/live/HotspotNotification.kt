package com.island.dynamicisland.live

/** Used after the normal ongoing/summary/media/auto-cancel checks. */
internal fun isHotspotNotification(packageName: String, texts: List<String>): Boolean {
    if (packageName !in hotspotSystemPackages) return false
    // A tethering channel can also contain USB/Bluetooth and stopped/error notices.
    // Require an explicit active hotspot heading rather than promoting the whole channel.
    return texts.any { activeHotspot.matches(it.trim()) && !inactiveHotspot.containsMatchIn(it) }
}

private val hotspotSystemPackages = setOf(
    "android", "com.android.systemui", "com.android.settings",
    "com.android.networkstack.tethering", "com.google.android.networkstack.tethering",
)
private val activeHotspot = Regex(
    "(?:(?:(?:mobile|portable(?: wi[‑-]?fi)?|wi[‑-]?fi) )?hotspot(?: (?:is )?(?:active|on|enabled))?|" +
        "tethering or hotspot active)(?:\\s*[.:–—-].*)?",
    RegexOption.IGNORE_CASE,
)
private val inactiveHotspot = Regex(
    "\\b(?:off|disabled|stopped|ended|failed|error|unavailable|not)\\b", RegexOption.IGNORE_CASE,
)

/** Use only a source-authored client count. Never infer clients from unrelated numbers. */
internal fun hotspotCompactStatus(texts: List<String>): String {
    for (text in texts) {
        if (Regex("\\bno (?:connected )?devices?(?: connected)?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "0 devices"
        val count = Regex("\\b(\\d{1,3}) (?:connected )?(?:devices?|clients?)(?: connected)?\\b", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: continue
        return "$count ${if (count == 1) "device" else "devices"}"
    }
    return "On"
}
