package com.island.dynamicisland.live

import kotlin.math.pow
import kotlin.math.roundToInt

/** Favor opaque, saturated logo pixels so white outlines do not erase team colors. */
internal fun dominantTeamColor(pixels: IntArray): Int {
    val weights = DoubleArray(36)
    val red = DoubleArray(36)
    val green = DoubleArray(36)
    val blue = DoubleArray(36)
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) / 255.0
        val r = (pixel ushr 16 and 255).toDouble()
        val g = (pixel ushr 8 and 255).toDouble()
        val b = (pixel and 255).toDouble()
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        if (alpha < .5 || max < 30 || delta / max < .2) continue
        val hue = when (max) {
            r -> ((g - b) / delta + 6) % 6
            g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        } * 60
        val bucket = (hue / 10).toInt().coerceIn(0, 35)
        val weight = alpha * delta / 255
        weights[bucket] += weight
        red[bucket] += r * weight
        green[bucket] += g * weight
        blue[bucket] += b * weight
    }
    val best = weights.indices.maxBy { weights[it] }
    if (weights[best] == 0.0) return 0xFFE4E4E7.toInt()
    return (255 shl 24) or ((red[best] / weights[best]).roundToInt() shl 16) or
        ((green[best] / weights[best]).roundToInt() shl 8) or (blue[best] / weights[best]).roundToInt()
}

/** Lift dark team colors toward white until small scores are readable on black. */
internal fun readableTeamColor(source: Int): Int {
    fun channel(shift: Int, lift: Double) = ((source ushr shift and 255) * (1 - lift) + 255 * lift).toInt()
    fun linear(value: Int): Double = (value / 255.0).let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
    for (step in 0..20) {
        val lift = step / 20.0
        val r = channel(16, lift)
        val g = channel(8, lift)
        val b = channel(0, lift)
        val luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
        if ((luminance + .05) / .05 >= 7) return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    return -1
}
