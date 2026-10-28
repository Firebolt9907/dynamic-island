package com.island.dynamicisland.ui.island

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Independent reversible vertical travel, softness, and opacity for each content row. */
internal fun Modifier.expandedContentEntrance(appear: () -> Float, order: Int,
    centerY: Float, compactHeight: Float): Modifier = graphicsLayer {
    val expansion = appear()
    alpha = ExpandedContentMotion.fraction(expansion, order)
    translationY = ExpandedContentMotion.translationYDp(expansion, centerY,
        size.height / density, compactHeight).dp.toPx()
    val radius = ExpandedContentMotion.blurRadiusDp(expansion).dp.toPx()
    renderEffect = if (alpha > 0f && radius > .01f) BlurEffect(radius, radius, TileMode.Decal) else null
    clip = false
}
