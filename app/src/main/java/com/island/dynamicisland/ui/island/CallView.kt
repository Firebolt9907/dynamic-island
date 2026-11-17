package com.island.dynamicisland.ui.island

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import com.island.dynamicisland.live.LiveAction
import com.island.dynamicisland.power.LocalInteractionMotionAllowed
import com.island.dynamicisland.power.LocalPowerSaveMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.LiveUpdate
import com.island.dynamicisland.live.CallControlKind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Pause

@Composable
internal fun CallCard(live: LiveUpdate, appear: () -> Float, interactive: Boolean, active: Boolean,
                      topPadding: Dp, cameraSafeTop: Dp, compactHeight: Dp, onOpenSource: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        .padding(top = maxOf(topPadding, cameraSafeTop - 20.dp), bottom = 12.dp)) {
        LiveActivityReveal(appear, 0, compactHeight, topPadding) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF454052))
                    .then(if (interactive) Modifier.clickable(role = Role.Button,
                        onClickLabel = "Open call", onClick = onOpenSource) else Modifier), contentAlignment = Alignment.Center) {
                    live.callAvatar?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } ?: Text(live.title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) },
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)
                    .then(if (interactive) Modifier.clickable(role = Role.Button,
                        onClickLabel = "Open call in ${live.appName}", onClick = onOpenSource) else Modifier)) {
                    // Center the measured text stack with the avatar, including
                    // headers that gain a timer or wrap the contact name.
                    // Text changes (status, contact name, app) crossfade instead of snapping.
                    ContentUpdateText(live.appName, Modifier.widthIn(max = 96.dp),
                        style = TextStyle(color = Color(0xFF8E8E93), fontSize = 11.sp, lineHeight = 14.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, contentAlignment = Alignment.CenterStart)
                    Spacer(Modifier.height(4.dp))
                    if (!live.title.equals(live.appName, ignoreCase = true)) {
                        ContentUpdateText(live.title,
                            style = TextStyle(color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 2, overflow = TextOverflow.Ellipsis, contentAlignment = Alignment.CenterStart)
                    }
                    live.stopwatch?.let { clock ->
                        NumericTickerText(stopwatchTime(clock, active = active), enabled = active, color = Color(0xFF8E8E93),
                            fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
                Box(Modifier.size(48.dp).clip(CircleShape).then(if (interactive) Modifier.clickable(
                    role = Role.Button, onClickLabel = "Open call in ${live.appName}", onClick = onOpenSource) else Modifier),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Info, "Open call in ${live.appName}", Modifier.size(24.dp), tint = Color(0xFF8E8E93))
                }
            }
        }
        if (live.actions.isNotEmpty()) LiveActivityReveal(appear, 1, compactHeight, topPadding) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top) {
                // Keep the destructive control at the far right, preserving source order otherwise.
                live.actions.sortedBy { it.isHangUp }.forEach { action ->
                    key(action.callControl?.kind ?: if (action.isHangUp) "hangup" else action.title) {
                        CallActionControl(action, interactive, Modifier.weight(1f), onOpenSource)
                    }
                }
            }
        }
    }
}


@Composable
private fun CallActionControl(action: LiveAction, interactive: Boolean, modifier: Modifier,
                              onOpenSource: () -> Unit) {
    val control = action.callControl
    val fallbackIcon = when (control?.kind) {
        CallControlKind.MUTE -> Icons.Default.MicOff
        CallControlKind.DEAFEN -> Icons.Default.HeadsetOff
        CallControlKind.SPEAKER -> Icons.Default.VolumeUp
        CallControlKind.VIDEO -> Icons.Default.VideocamOff
        CallControlKind.HOLD -> Icons.Default.Pause
        else -> null
    }
    val selected = control?.active == true
    val motionEnabled = interactive && !LocalPowerSaveMode.current && LocalInteractionMotionAllowed.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && motionEnabled) .88f else 1f,
        if (motionEnabled) spring(dampingRatio = .65f, stiffness = 700f) else tween(0),
        label = "callControlPress")
    val confirmation = rememberControlConfirmation(selected, active = interactive)
    val background by animateColorAsState(when {
        action.isHangUp -> Color(0xFFFF3B48)
        selected -> Color.White
        else -> Color(0xFF28282A)
    }, tween(if (motionEnabled) 180 else 0), label = "callControlSelection")
    val tint by animateColorAsState(if (selected && !action.isHangUp) Color(0xFF1C1C1E) else Color(0xFFAEAEB2),
        tween(if (motionEnabled) 180 else 0), label = "callControlTint")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The 48 dp hit target stays fixed while its visual compresses and rebounds.
        Box(Modifier.size(48.dp).then(if (interactive) Modifier.clickable(
            interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = action.title,
            onClick = { runCatching { action.intent.send() }.onFailure { onOpenSource() } }) else Modifier),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer {
                    scaleX = 1f + .14f * confirmation.value
                    scaleY = scaleX
                    rotationZ = 4f * confirmation.value
                }, contentAlignment = Alignment.Center) {
                    if (action.isHangUp) Icon(Icons.Default.CallEnd, action.title, Modifier.size(25.dp), tint = Color.White)
                    else if (fallbackIcon != null) Icon(fallbackIcon, action.title, Modifier.size(25.dp), tint = tint)
                    else if (action.icon != null) Image(action.icon.asImageBitmap(), action.title,
                        Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                    else Text(action.title.take(2), color = Color.White, fontSize = 15.sp)
                }
            }
        }
        // Label changes (Mute ↔ Unmute, Speaker ↔ Earpiece) crossfade with the button state.
        ContentUpdateText(action.title, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            style = TextStyle(color = Color(0xFFAEAEB2), fontSize = 10.sp, lineHeight = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
