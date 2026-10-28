package com.island.dynamicisland.live

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.time.Duration
import java.time.Instant

internal data class NotificationClock(
    val stopwatch: Stopwatch? = null,
    val countdown: Countdown? = null,
    val label: String = "",
)

internal fun readNotificationClock(context: Context, notification: Notification): NotificationClock? {
    // Resolve only public API 37 methods, keeping compatibility with our compile SDK.
    if (Build.VERSION.SDK_INT >= 37) {
        val metric = runCatching {
            val style = Notification.Builder.recoverBuilder(context, notification).style ?: return@runCatching null
            if (style.javaClass.name != "android.app.Notification\$MetricStyle") return@runCatching null
            val metrics = style.javaClass.getMethod("getMetrics").invoke(style) as? List<*> ?: return@runCatching null
            val critical = style.javaClass.getMethod("getCriticalMetric").invoke(style)
            (listOfNotNull(critical) + metrics.filterNotNull()).distinct().firstNotNullOfOrNull { entry ->
                // One unsupported metric must not hide another usable clock.
                runCatching {
                    val value = entry.javaClass.getMethod("getValue").invoke(entry) ?: return@runCatching null
                    if (value.javaClass.name != "android.app.Notification\$Metric\$TimeDifference") return@runCatching null
                    val stopwatch = value.javaClass.getMethod("isStopwatch").invoke(value) == true
                    val timer = value.javaClass.getMethod("isTimer").invoke(value) == true
                    if (!stopwatch && !timer) return@runCatching null
                    val paused = value.javaClass.getMethod("getPausedDuration").invoke(value) as? Duration
                    val zeroElapsed = value.javaClass.getMethod("getZeroElapsedRealtime").invoke(value) as? Long
                    val zeroTime = value.javaClass.getMethod("getZeroTime").invoke(value) as? Instant
                    val base = zeroElapsed ?: zeroTime?.let {
                        SystemClock.elapsedRealtime() + (it.toEpochMilli() - System.currentTimeMillis())
                    }
                    val label = entry.javaClass.getMethod("getLabel").invoke(entry)?.toString().orEmpty()
                    when {
                        stopwatch && paused != null -> NotificationClock(stopwatch = Stopwatch(null, paused.toMillis()), label = label)
                        stopwatch && base != null -> NotificationClock(stopwatch = Stopwatch(base), label = label)
                        timer && paused != null -> NotificationClock(countdown = Countdown(0L, paused.toMillis()), label = label)
                        timer && base != null -> NotificationClock(countdown = Countdown(base), label = label)
                        else -> null
                    }
                }.getOrNull()
            }
        }.getOrNull()
        if (metric != null) return metric
    }
    val extras = notification.extras
    if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && notification.`when` > 0L) {
        val base = SystemClock.elapsedRealtime() + (notification.`when` - System.currentTimeMillis())
        return if (extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            NotificationClock(countdown = Countdown(base))
        else NotificationClock(stopwatch = Stopwatch(base))
    }
    return null
}
