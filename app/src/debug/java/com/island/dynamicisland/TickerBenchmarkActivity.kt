package com.island.dynamicisland

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.Stopwatch
import com.island.dynamicisland.ui.island.NumericTickerText
import com.island.dynamicisland.ui.theme.DynamicIslandTheme
import com.island.dynamicisland.ui.theme.IslandFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Shell-only diagnostic: identical three-readout workloads with/without digit motion. */
class TickerBenchmarkActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()
        // Diagnostics must not leave a timer running after another app takes over.
        if (!isChangingConfigurations) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val animated = intent.getBooleanExtra("animated", true)
        val precise = intent.getBooleanExtra("precise", false)
        val expanded = intent.getBooleanExtra("expanded", false)
        val idle = intent.getBooleanExtra("idle", false)
        val native = intent.getBooleanExtra("native", false)
        setContent {
            DynamicIslandTheme {
                LaunchedEffect(Unit) {
                    delay(intent.getLongExtra("durationMs", 15_000L))
                    finish()
                }
                val start = remember { SystemClock.elapsedRealtime() - 55_000L }
                val now by produceState(SystemClock.elapsedRealtime(), idle, precise) {
                    while (!idle && !native && isActive) {
                        delay(if (precise) 50L else 1000L - (SystemClock.elapsedRealtime() - start) % 1000L)
                        value = SystemClock.elapsedRealtime()
                    }
                }
                Row(Modifier.fillMaxSize().background(Color.Black),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(3) { index ->
                        val label = Stopwatch(start - index * 60_000L).format(now, precise)
                        val style = TextStyle(color = Color.White, fontSize = if (expanded) 40.sp else 13.sp,
                            fontFamily = IslandFontFamily, fontFeatureSettings = "tnum")
                        Box(Modifier.padding(4.dp)) {
                            if (native) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val views = remember(start, index) {
                                    android.widget.RemoteViews(context.packageName, R.layout.ticker_chronometer_demo).apply {
                                        setChronometer(R.id.ticker_chronometer, start - index * 60_000L, null, true)
                                    }
                                }
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { com.island.dynamicisland.ui.island.CustomNotificationHost(it) },
                                    modifier = Modifier.size(64.dp, 20.dp),
                                    update = {
                                        it.animateCompactTimers = true
                                        it.tickerEnabled = animated
                                        it.bind(views, com.island.dynamicisland.live.CustomActivityParts(
                                            R.id.ticker_chronometer, emptyList(), "0:55", start - index * 60_000L),
                                            timerOnly = true, compactTimer = true)
                                    })
                            } else if (animated) NumericTickerText(label, style = style, fractionalTime = precise, compact = !expanded)
                            else Text(label, style = style, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
