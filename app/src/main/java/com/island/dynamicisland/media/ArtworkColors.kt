package com.island.dynamicisland.media

import android.graphics.Bitmap

/** Sample once when artwork arrives; UI drawing never reads bitmap pixels. */
internal object ArtworkColors {
    const val FALLBACK: Int = -15198181 // #18181B

    fun fromBitmap(bitmap: Bitmap): Int = runCatching {
        val sample = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            fromPixels(pixels)
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }.getOrDefault(FALLBACK)

    fun fromPixels(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FALLBACK
        var r = 0L; var g = 0L; var b = 0L
        for (pixel in pixels) {
            val alpha = pixel ushr 24
            // Transparent artwork is displayed against the artwork container's dark background.
            r += (((pixel ushr 16) and 255) * alpha + 28 * (255 - alpha)) / 255
            g += (((pixel ushr 8) and 255) * alpha + 28 * (255 - alpha)) / 255
            b += ((pixel and 255) * alpha + 31 * (255 - alpha)) / 255
        }
        val count = pixels.size
        return (255 shl 24) or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }
}
