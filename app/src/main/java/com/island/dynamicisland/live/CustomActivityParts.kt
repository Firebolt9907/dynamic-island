package com.island.dynamicisland.live

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews

data class CustomActivityParts(val timerId: Int, val controlIds: List<Int>, val timerText: String, val timerBase: Long)

/** Inspect only the public, inflated hierarchy. Keep the original timer and listeners. */
internal fun readCustomActivityParts(context: Context, content: RemoteViews): CustomActivityParts? = runCatching {
    val root = content.apply(context, FrameLayout(context))
    var timerId: Int? = null
    var timerText = ""
    var timerBase = 0L
    val controls = mutableListOf<Int>()
    fun visit(view: View, insideControl: Boolean = false) {
        if (view.visibility != View.VISIBLE) return
        if (view is Chronometer && !view.isCountDown && view.id != View.NO_ID) {
            timerId = view.id
            timerText = view.text.toString()
            timerBase = view.base
        }
        val control = view.hasOnClickListeners() && view.id != View.NO_ID
        if (control && !insideControl) controls += view.id
        if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i), insideControl || control)
    }
    visit(root)
    timerId?.let { CustomActivityParts(it, controls.take(3), timerText, timerBase) }
}.getOrNull()
