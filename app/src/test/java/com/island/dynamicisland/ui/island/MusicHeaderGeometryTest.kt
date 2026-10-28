package com.island.dynamicisland.ui.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicHeaderGeometryTest {
    @Test fun centeredTextClearsCameraWithoutMovingArtworkDown() {
        for (cameraBottom in listOf(-10f, 18f, 30f, 48f, 64f)) {
            for (textHeight in listOf(39f, 54f, 90f)) {
                val height = MusicHeaderGeometry.height(textHeight, 10f, cameraBottom)
                val art = MusicElementMorph.art(37f, 10f, 1f, height, height)
                assertEquals(10f, art.top, 0.001f)
                assertTrue(10f + (height - textHeight) / 2f >= cameraBottom + 2f)
                assertTrue(height >= 72f && height >= textHeight)
            }
        }
    }
}
