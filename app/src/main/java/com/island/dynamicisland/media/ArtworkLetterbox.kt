package com.island.dynamicisland.media

import android.graphics.Bitmap

/**
 * Video apps (YouTube, etc.) often publish 16:9 thumbnails padded into a square with black or
 * transparent bars. Rounded clipping then lands on the invisible bars, so the picture reads as
 * a sharp rectangle. Trimming the bars lets the real image fill the rounded artwork.
 */
internal object ArtworkLetterbox {
    /** Content bounds, as left/top/right(exclusive)/bottom(exclusive). */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun trim(bitmap: Bitmap): Bitmap = runCatching {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val b = contentBounds(pixels, w, h)
        if (b == null) bitmap
        else Bitmap.createBitmap(source, b.left, b.top, b.right - b.left, b.bottom - b.top)
    }.getOrDefault(bitmap)

    /** Returns null when there are no bars worth trimming. */
    fun contentBounds(pixels: IntArray, width: Int, height: Int): Bounds? {
        if (width < 8 || height < 8 || pixels.size < width * height) return null
        fun rowIsBar(y: Int) = barFraction(width) { pixels[y * width + it] }
        fun colIsBar(x: Int, top: Int, bottom: Int) = barFraction(bottom - top) { pixels[(top + it) * width + x] }

        var top = 0
        while (top < height && rowIsBar(top)) top++
        if (top == height) return null // Entirely dark artwork; leave it alone.
        var bottom = height
        while (bottom > top && rowIsBar(bottom - 1)) bottom--
        var left = 0
        while (left < width && colIsBar(left, top, bottom)) left++
        var right = width
        while (right > left && colIsBar(right - 1, top, bottom)) right--

        val trimmedW = right - left
        val trimmedH = bottom - top
        // Ignore thin dark edges that are part of the cover, and refuse to trim away most of it.
        val meaningful = (width - trimmedW) >= width * MIN_TRIM || (height - trimmedH) >= height * MIN_TRIM
        if (!meaningful || trimmedW < width * MIN_CONTENT || trimmedH < height * MIN_CONTENT) return null
        return Bounds(left, top, right, bottom)
    }

    private inline fun barFraction(count: Int, pixel: (Int) -> Int): Boolean {
        if (count <= 0) return false
        var bar = 0
        for (i in 0 until count) if (isBarPixel(pixel(i))) bar++
        // Allow a little JPEG noise along the bar edge.
        return bar >= count * BAR_PIXEL_RATIO
    }

    private fun isBarPixel(p: Int): Boolean {
        val alpha = p ushr 24
        if (alpha < 24) return true
        val r = (p ushr 16) and 255
        val g = (p ushr 8) and 255
        val b = p and 255
        return maxOf(r, g, b) <= BAR_MAX_CHANNEL
    }

    private const val BAR_MAX_CHANNEL = 20
    private const val BAR_PIXEL_RATIO = .98f
    private const val MIN_TRIM = .06f
    private const val MIN_CONTENT = .3f
}
