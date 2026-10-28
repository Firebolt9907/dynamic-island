package com.island.dynamicisland.ui.island

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.LiveUpdate
import com.island.dynamicisland.live.ScreenCaptureKind
import com.island.dynamicisland.ui.theme.IslandFontFamily

private fun captureColor(live: LiveUpdate) =
    if (live.screenCapture == ScreenCaptureKind.RECORDING) Color(0xFFFF554B) else Color(0xFF34C759)

@Composable
internal fun CaptureSymbol(live: LiveUpdate, size: Dp) {
    if (live.screenCapture == ScreenCaptureKind.RECORDING) {
        Box(Modifier.size(size).background(captureColor(live), CircleShape))
    } else {
        Icon(Icons.Outlined.ScreenShare, "Screen sharing", Modifier.size(size), tint = captureColor(live))
    }
}

@Composable
internal fun ScreenCaptureCard(live: LiveUpdate, appear: () -> Float, interactive: Boolean,
    active: Boolean, compactHeight: Dp, onOpenSource: () -> Unit) {
    val recording = live.screenCapture == ScreenCaptureKind.RECORDING
    val color = captureColor(live)
    val stop = live.actions.firstOrNull { it.title.contains("stop", ignoreCase = true) }

    Row(Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).height(48.dp).clickable(enabled = interactive, onClick = onOpenSource)) {
            // The island owns the persistent symbol and timer in this reserved row.
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(if (recording) "Screen Recording" else "Screen Sharing", color = Color.White,
                fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = IslandFontFamily,
                modifier = Modifier.expandedContentEntrance(appear, 0, 54f, compactHeight.value))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(48.dp).expandedContentEntrance(appear, 1, 46f, compactHeight.value).semantics { contentDescription = if (stop != null) "Stop " + if (recording) "screen recording" else "screen sharing" else "Open screen capture controls" }.border(2.dp, Color.White, CircleShape)
            .clickable(enabled = interactive, onClick = {
                if (stop != null) runCatching { stop.intent.send() } else onOpenSource()
            }), contentAlignment = Alignment.Center) {
            Box(Modifier.size(19.dp).background(color, RoundedCornerShape(5.dp)))
        }
    }
}
