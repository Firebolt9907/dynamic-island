package com.island.dynamicisland.ui.island

/** Hold the visible height during a swipe; the size animator receives the latest target on release. */
internal class DeferredContentResize {
    var visibleHeight: Int = 0
    private var heldHeight: Int? = null

    fun height(measuredHeight: Int, deferred: Boolean): Int {
        if (!deferred) {
            heldHeight = null
            return measuredHeight
        }
        return heldHeight ?: visibleHeight.takeIf { it > 0 }?.also { heldHeight = it }
            ?: measuredHeight.also { heldHeight = it }
    }
}
