package com.island.dynamicisland.ui.island

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.sin

/**
 * Continuous-curvature ("squircle") corners for the island bubbles.
 *
 * Uses Figma's corner-smoothing construction (the one that matches iOS): each corner is a circular
 * arc of the nominal radius, eased into both edges by cubic Béziers that start with zero curvature.
 * Unlike a low-exponent superellipse there is no curvature spike where the corner meets a straight
 * edge, so no faint point is visible. When the shape is too short for the smoothing budget, the
 * smoothing gives way first, so a full pill is still an exact stadium.
 */
internal object Squircle {
    /** iOS-like smoothing; 0 is a plain rounded rectangle. */
    const val SMOOTHING = 0.6f

    internal data class Corner(
        val radius: Float, val p: Float, val a: Float, val b: Float, val c: Float, val d: Float,
        val arcLength: Float, val arcDegrees: Float,
    )

    /** Geometry for one corner along an edge budget of half the shorter side. */
    internal fun corner(radius: Float, budget: Float, smoothing: Float = SMOOTHING): Corner {
        val r = radius.coerceIn(0f, budget.coerceAtLeast(0f))
        if (r <= 0f) return Corner(0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f)
        val s = smoothing.coerceIn(0f, (budget / r - 1f).coerceAtLeast(0f))
        val p = minOf((1f + s) * r, budget)
        val arcDegrees = 90f * (1f - s)
        val arcLength = sin(rad(arcDegrees / 2f)) * r * SQRT2
        val alpha = (90f - arcDegrees) / 2f
        val p3ToP4 = r * tan(rad(alpha / 2f))
        val beta = 45f * s
        val c = p3ToP4 * cos(rad(beta))
        val d = c * tan(rad(beta))
        val b = ((p - arcLength - c - d) / 3f).coerceAtLeast(0f)
        return Corner(r, p, 2f * b, b, c, d, arcLength, arcDegrees)
    }

    fun path(rect: Rect, radius: Float, path: Path = Path(), smoothing: Float = SMOOTHING): Path {
        path.reset()
        val k = corner(radius, min(rect.width, rect.height) / 2f, smoothing)
        if (k.radius <= 0f) {
            path.addRect(rect)
            return path
        }
        // Clockwise from the top edge. Each corner is drawn in a local frame: u runs along the
        // incoming edge, v along the outgoing one; every frame is a proper rotation.
        path.moveTo(rect.left + k.p, rect.top)
        corner(path, k, rect.right, rect.top, 1f, 0f, 0f, 1f)
        corner(path, k, rect.right, rect.bottom, 0f, 1f, -1f, 0f)
        corner(path, k, rect.left, rect.bottom, -1f, 0f, 0f, -1f)
        corner(path, k, rect.left, rect.top, 0f, -1f, 1f, 0f)
        path.close()
        return path
    }

    private fun corner(path: Path, k: Corner, cornerX: Float, cornerY: Float,
                       inX: Float, inY: Float, outX: Float, outY: Float) {
        // Local origin is where the corner leaves the incoming straight edge.
        val ox = cornerX - inX * k.p
        val oy = cornerY - inY * k.p
        fun x(u: Float, v: Float) = ox + inX * u + outX * v
        fun y(u: Float, v: Float) = oy + inY * u + outY * v
        path.lineTo(x(0f, 0f), y(0f, 0f))
        val (a, b, c, d) = listOf(k.a, k.b, k.c, k.d)
        // Ease in: zero curvature at the edge, tangent to the arc at its start.
        val su = a + b + c
        path.cubicTo(x(a, 0f), y(a, 0f), x(a + b, 0f), y(a + b, 0f), x(su, d), y(su, d))
        // Circular arc as a single cubic (≤ 90°), centered on the corner's diagonal.
        val cu = k.p - k.radius
        val cv = k.radius
        val eu = su + k.arcLength
        val ev = d + k.arcLength
        val h = 4f / 3f * tan(rad(k.arcDegrees / 4f))
        // Clockwise tangent of radius vector (ru, rv) is (-rv, ru).
        val c1u = su - (d - cv) * h
        val c1v = d + (su - cu) * h
        val c2u = eu + (ev - cv) * h
        val c2v = ev - (eu - cu) * h
        path.cubicTo(x(c1u, c1v), y(c1u, c1v), x(c2u, c2v), y(c2u, c2v), x(eu, ev), y(eu, ev))
        // Ease out onto the outgoing edge, mirroring the ease in.
        path.cubicTo(x(k.p, ev + c), y(k.p, ev + c),
            x(k.p, ev + c + b), y(k.p, ev + c + b),
            x(k.p, k.p), y(k.p, k.p))
    }

    private const val SQRT2 = 1.4142135f
    private fun rad(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)
}

/** Squircle with a fixed corner radius; [percent] = 50 gives a squircle-ended pill. */
internal class SquircleShape private constructor(
    private val corner: Dp?,
    private val percent: Int,
    private val smoothing: Float = Squircle.SMOOTHING,
) : Shape {
    constructor(corner: Dp, smoothing: Float = Squircle.SMOOTHING) : this(corner, 0, smoothing)
    constructor(percent: Int) : this(null, percent)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = corner?.let { with(density) { it.toPx() } } ?: (min(size.width, size.height) * percent / 100f)
        return Outline.Generic(Squircle.path(Rect(Offset.Zero, size), radius, smoothing = smoothing))
    }

    override fun equals(other: Any?) = other is SquircleShape && other.corner == corner &&
        other.percent == percent && other.smoothing == smoothing
    override fun hashCode() = (31 * (corner?.hashCode() ?: 0) + percent) * 31 + smoothing.hashCode()
}

internal fun DrawScope.drawSquircle(color: Color, topLeft: Offset, size: Size, radius: Float,
                                    style: DrawStyle = Fill, smoothing: Float = Squircle.SMOOTHING) {
    drawPath(Squircle.path(Rect(topLeft, size), radius, smoothing = smoothing), color, style = style)
}

/**
 * Corner smoothing of the expanded island. Following the display's own (circular) corners
 * needs none: an inset circle of radius R - inset is exactly concentric with the screen's.
 * Controls inside the card use the same value so they stay concentric with it too.
 */
internal val LocalIslandCornerSmoothing = androidx.compose.runtime.staticCompositionLocalOf { Squircle.SMOOTHING }
