package com.island.dynamicisland.live

import android.app.Notification
import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap

/** Resolve once while reading the notification, never in the rendering path. */
internal fun liveActivityAccent(context: Context, packageName: String, notificationColor: Int): Int {
    val source = if (notificationColor != Notification.COLOR_DEFAULT) notificationColor else runCatching {
        val icon = context.packageManager.getApplicationIcon(packageName).toBitmap(32, 32)
        // Vote by hue so white icon backgrounds don't wash out the brand color.
        val weights = FloatArray(36)
        val reds = FloatArray(36)
        val greens = FloatArray(36)
        val blues = FloatArray(36)
        val hsv = FloatArray(3)
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            val pixel = icon.getPixel(x, y)
            Color.colorToHSV(pixel, hsv)
            if (Color.alpha(pixel) < 128 || hsv[1] < .2f || hsv[2] < .15f) continue
            val bucket = (hsv[0] / 10).toInt().coerceIn(0, 35)
            val weight = hsv[1] * hsv[2] * Color.alpha(pixel) / 255f
            weights[bucket] += weight
            reds[bucket] += Color.red(pixel) * weight
            greens[bucket] += Color.green(pixel) * weight
            blues[bucket] += Color.blue(pixel) * weight
        }
        val best = weights.indices.maxBy { weights[it] }
        if (weights[best] == 0f) Color.LTGRAY else Color.rgb(
            (reds[best] / weights[best]).toInt(),
            (greens[best] / weights[best]).toInt(),
            (blues[best] / weights[best]).toInt(),
        )
    }.getOrDefault(Color.LTGRAY)
    val opaque = ColorUtils.setAlphaComponent(source, 255)
    // Lift toward white only as far as needed for readable small text on black.
    var accent = opaque
    var lift = 0f
    while (ColorUtils.calculateContrast(accent, Color.BLACK) < 7.0 && lift < 1f) {
        lift += .05f
        accent = ColorUtils.blendARGB(opaque, Color.WHITE, lift.coerceAtMost(1f))
    }
    return accent
}
