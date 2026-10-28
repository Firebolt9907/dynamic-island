package com.island.dynamicisland.ui.island

/** Artwork starts at the card inset; centered text just clears the measured lens bottom. */
internal object MusicHeaderGeometry {
    fun height(textHeight: Float, topInset: Float, cameraBottom: Float): Float =
        maxOf(72f, textHeight, textHeight + 2f * (cameraBottom + 2f - topInset))
}
