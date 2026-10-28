package com.island.dynamicisland.ui.island

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.island.dynamicisland.live.CustomActivityParts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.sp
import android.text.Editable
import android.text.TextWatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.island.dynamicisland.power.LocalPowerSaveMode
import com.island.dynamicisland.ui.theme.IslandFontFamily
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/** Host the public RemoteViews, preserving the source app's timer and click intents. */
internal class CustomNotificationHost(context: Context) : FrameLayout(context) {
    var animateCompactTimers = true
    var interactive = false
    var tickerEnabled by mutableStateOf(true)
    private var applied: RemoteViews? = null
    private var appliedAccent: Int? = null
    private var appliedCompactMotion: Boolean? = null

    fun bind(content: RemoteViews, parts: CustomActivityParts? = null, timerOnly: Boolean = false,
             compactTimer: Boolean = false, controlsOnly: Boolean = false, accentColor: Int = Color.LTGRAY) {
        if (applied === content && appliedAccent == accentColor && appliedCompactMotion == animateCompactTimers) return
        // Reinflate on notification updates so removed controls/listeners cannot linger.
        removeAllViews()
        applied = null
        try {
            val child = content.apply(context, this)
            if (parts == null) addView(child) else {
                val timer = child.findViewById<TextView>(parts.timerId)
                val controls = if (timerOnly) emptyList() else parts.controlIds.mapNotNull { child.findViewById<View>(it) }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                }
                fun detach(view: View) { (view.parent as? ViewGroup)?.removeView(view) }
                detach(timer)
                timer.setTextColor(accentColor)
                timer.textSize = if (compactTimer) 13f else 26f
                timer.typeface = resources.getFont(com.island.dynamicisland.R.font.inter_semibold)
                timer.letterSpacing = 0f
                timer.fontFeatureSettings = "tnum"
                timer.setSingleLine(true)
                timer.setPadding(0, 0, 0, 0)
                timer.minWidth = 0
                timer.minimumWidth = 0
                timer.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                timer.includeFontPadding = false
                timer.gravity = Gravity.CENTER_VERTICAL or Gravity.END
                if (!controlsOnly) {
                    if (compactTimer && !animateCompactTimers) {
                        row.addView(timer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                    } else {
                        // Retain the source Chronometer and its own running/paused state. Observe
                        // its real text changes rather than adding a second timer or guessing its base.
                        val value = mutableStateOf(timer.text.toString())
                        timer.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                value.value = s?.toString().orEmpty()
                            }
                            override fun afterTextChanged(s: Editable?) = Unit
                        })
                        timer.alpha = 0f
                        timer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        val timerFrame = FrameLayout(context)
                        timerFrame.addView(timer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        timerFrame.addView(ComposeView(context).apply {
                            setContent {
                                NumericTickerText(value.value,
                                    modifier = Modifier.fillMaxSize(),
                                    color = androidx.compose.ui.graphics.Color(accentColor),
                                    fontSize = (if (compactTimer) 13 else 26).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = IslandFontFamily,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                    lineHeight = (if (compactTimer) 17 else 32).sp,
                                    textAlign = TextAlign.End,
                                    enabled = tickerEnabled, compact = compactTimer)
                            }
                        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        row.addView(timerFrame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                    }
                }
                val density = resources.displayMetrics.density
                controls.forEach { control ->
                    detach(control)
                    val padding = (12 * density).toInt()
                    control.setPadding(padding, padding, padding, padding)
                    control.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 31)) }
                    if (control is ImageView) {
                        control.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
                        control.scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    row.addView(control, LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
                        marginStart = (10 * density).toInt()
                    })
                }
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
            appliedAccent = accentColor
            appliedCompactMotion = animateCompactTimers
            applied = content
        } catch (error: RuntimeException) {
            android.util.Log.w("IslandLive", "Unable to inflate custom notification", error)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (interactive) super.dispatchTouchEvent(event) else false
}

@Composable
internal fun CustomNotificationContent(content: RemoteViews, activityKey: String,
                                       interactive: Boolean, active: Boolean, modifier: Modifier = Modifier,
                                       parts: CustomActivityParts? = null, timerOnly: Boolean = false,
                                       compactTimer: Boolean = false, controlsOnly: Boolean = false,
                                       accentColor: Int = LocalLiveAccent.current.toArgb()) {
    val compactMotion = !compactTimer || com.island.dynamicisland.power.LocalCompactTickerAllowed.current
    val animate = active && !LocalPowerSaveMode.current
    key(activityKey, timerOnly, compactTimer, controlsOnly) {
        AndroidView(
            factory = { CustomNotificationHost(it) },
            modifier = modifier,
            update = {
                it.interactive = interactive
                it.tickerEnabled = animate
                it.animateCompactTimers = compactMotion
                // INVISIBLE retains measurement but stops hidden Chronometer ticks.
                it.visibility = if (active) android.view.View.VISIBLE else android.view.View.INVISIBLE
                it.bind(content, parts, timerOnly, compactTimer, controlsOnly, accentColor)
            },
            onRelease = { it.removeAllViews() },
        )
    }
}
