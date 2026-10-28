package com.island.dynamicisland.live

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap

data class LiveProgressSegment(val fraction: Float, val color: Int)
data class LiveProgressPoint(val fraction: Float, val color: Int)
data class LiveProgressStyle(
    val segments: List<LiveProgressSegment>,
    val points: List<LiveProgressPoint>,
    val styledByProgress: Boolean,
    val tracker: Bitmap?,
    val startIcon: Bitmap?,
    val endIcon: Bitmap?,
)

@RequiresApi(36)
internal fun readLiveProgressStyle(context: Context, style: Notification.ProgressStyle): LiveProgressStyle {
    val max = style.progressMax.coerceAtLeast(1).toFloat()
    fun bitmap(icon: Icon?) = runCatching { icon?.loadDrawable(context)?.toBitmap(64, 64) }.getOrNull()
    return LiveProgressStyle(
        style.progressSegments.map { LiveProgressSegment(it.length / max, it.color) },
        style.progressPoints.filter { it.position > 0 && it.position < max }
            .map { LiveProgressPoint(it.position / max, it.color) },
        style.isStyledByProgress, bitmap(style.progressTrackerIcon),
        bitmap(style.progressStartIcon), bitmap(style.progressEndIcon),
    )
}
