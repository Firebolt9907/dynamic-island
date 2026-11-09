package com.island.dynamicisland.ui.island

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredContentResizeTest {
    @Test fun lyricGrowthAndGapsWaitUntilSwipeRelease() {
        val resize = DeferredContentResize()
        resize.visibleHeight = 200
        assertEquals(200, resize.height(236, true))
        assertEquals(200, resize.height(190, true))
        assertEquals(236, resize.height(236, false))
        resize.visibleHeight = 218 // Capture an in-flight resize at its visible height.
        assertEquals(218, resize.height(236, true))
        assertEquals(218, resize.height(200, true))
        assertEquals(200, resize.height(200, false))
    }
}
