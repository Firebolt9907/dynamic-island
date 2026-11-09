package com.island.dynamicisland.ui.island

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.island.dynamicisland.ui.theme.IslandFontFamily
import android.text.Editable
import android.text.TextWatcher
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView

/** Retain the source timer itself: never call start/stop or reconstruct a running flag. */
internal class SportsGameClockHost(context: Context) : FrameLayout(context) {
    var onTimeChanged: ((String) -> Unit)? = null
    private var source: RemoteViews? = null
    private var timerId: Int? = null

    fun bind(content: RemoteViews, id: Int) {
        if (source === content && timerId == id) return
        removeAllViews()
        source = null
        timerId = null
        val timer = runCatching {
            val root = content.apply(context, this)
            root.findViewById<Chronometer>(id)?.also {
                (it.parent as? ViewGroup)?.removeView(it)
            }
        }.getOrNull() ?: return
        timer.alpha = 0f
        timer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        timer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTimeChanged?.invoke(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        onTimeChanged?.invoke(timer.text.toString())
        timer.setTextColor(Color.WHITE)
        timer.textSize = 15f
        timer.typeface = resources.getFont(com.island.dynamicisland.R.font.inter_semibold)
        timer.gravity = Gravity.CENTER
        timer.includeFontPadding = false
        timer.setSingleLine(true)
        timer.setPadding(0, 0, 0, 0)
        timer.fontFeatureSettings = "tnum"
        addView(timer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        source = content
        timerId = id
    }
}

@Composable
internal fun SportsGameClockView(content: RemoteViews, timerId: Int, initialText: String, active: Boolean) {
    var value by remember(timerId) { mutableStateOf(initialText) }
    Box(Modifier.fillMaxWidth().height(21.dp)) {
        AndroidView(factory = { SportsGameClockHost(it) }, modifier = Modifier.fillMaxSize(),
            update = {
                // The original timer owns time and running state; the ticker observes its text.
                it.onTimeChanged = { text -> value = text }
                it.visibility = if (active) View.VISIBLE else View.INVISIBLE
                it.bind(content, timerId)
            }, onRelease = { it.onTimeChanged = null; it.removeAllViews() })
        NumericTickerText(value, Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.White,
            fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = IslandFontFamily, textAlign = TextAlign.Center, enabled = active)
    }
}
