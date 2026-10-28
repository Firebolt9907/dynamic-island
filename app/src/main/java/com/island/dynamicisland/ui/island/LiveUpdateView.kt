package com.island.dynamicisland.ui.island

import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import com.island.dynamicisland.ui.theme.IslandFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.compactLiveStatus
import com.island.dynamicisland.live.LiveUpdate
import com.island.dynamicisland.live.SportsScore
import com.island.dynamicisland.media.NowPlaying

internal const val compactLiveIconFraction = .72f

private val sportsExpandedInset = 20.dp

internal val LocalLiveAccent = androidx.compose.runtime.staticCompositionLocalOf { Color(0xFFDADAE0) }
private val liveAccent: Color
    @Composable get() = LocalLiveAccent.current

@Composable
internal fun IslandArtwork(np: NowPlaying?, live: LiveUpdate?, size: Dp, corner: Dp, modifier: Modifier, insetFraction: () -> Float = { 0f }, innerCorner: (() -> Dp)? = null, liveIconInset: () -> Float = { .12f }, expandedArtwork: Boolean = false, artworkExpansion: () -> Float = { 1f }) {
    if (live?.isCall == true) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Call,
                "Ongoing call", Modifier.size(size * .65f), tint = Color(0xFF34C759))
        }
    } else if (live?.isHotspot == true) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.WifiTethering,
                "Mobile hotspot", Modifier.size(size * .65f), tint = Color(live.accentColor))
        }
    } else if (live?.screenCapture != null) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            CaptureSymbol(live, size * .55f)
        }
    } else if (live == null) AlbumArt(np, size, corner, modifier, insetFraction, innerCorner, expandedArtwork, artworkExpansion) else Box(
        modifier.size(size), contentAlignment = Alignment.Center,
    ) {
        val isEspn = live.packageName.contains("espn", ignoreCase = true)
        val bitmap = live.sportsScore?.teamOneLogo ?: (if (isEspn) null else live.icon)
        bitmap?.let { Image(it.asImageBitmap(), live.appName, Modifier.fillMaxSize().padding(size * liveIconInset())) }
            ?: Text(if (isEspn) "" else live.appName.take(1), color = Color(live.accentColor), fontSize = 22.sp)
    }
}

private fun indicatorStyle(live: LiveUpdate) = TextStyle(
    fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
    fontFamily = IslandFontFamily,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
internal fun sportsScoreLeadingWidth(score: SportsScore, height: Dp): Dp {
    val logoSize = height * .62f
    val style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = IslandFontFamily,
        fontFeatureSettings = "tnum",
    )
    val measured = rememberTextMeasurer().measure(score.scoreOne, style, maxLines = 1, softWrap = false)
    return logoSize + with(LocalDensity.current) { measured.size.width.toDp() } + 6.dp
}

@Composable
internal fun sportsScoreTrailingWidth(score: SportsScore, height: Dp): Dp {
    val logoSize = height * .62f
    val style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = IslandFontFamily,
        fontFeatureSettings = "tnum",
    )
    val measured = rememberTextMeasurer().measure(score.scoreTwo, style, maxLines = 1, softWrap = false)
    return logoSize + with(LocalDensity.current) { measured.size.width.toDp() } + 6.dp
}

@Composable
internal fun compactLeadingWidth(live: LiveUpdate?, height: Dp): Dp {
    if (live?.sportsScore != null) {
        return sportsScoreLeadingWidth(live.sportsScore, height)
    }
    return height * if (live == null) .60f else compactLiveIconFraction
}

@Composable
private fun indicatorLabel(live: LiveUpdate): String = live.countdown?.let { countdownTime(it) } ?: live.stopwatch?.let { stopwatchTime(it) }
    ?: if (live.isNavigation) live.turnDistance ?: "Next turn"
    else compactLiveStatus(live.status, live.progress, live.text, live.details)

@Composable
internal fun compactIndicatorWidth(live: LiveUpdate?, height: Dp = 36.dp): Dp {
    if (live == null) return 24.dp
    if (live.sportsScore != null) {
        return sportsScoreTrailingWidth(live.sportsScore, height)
    }
    live.customParts?.let { parts ->
        // Reserve enough digits at hour boundaries without imposing a fixed empty slot.
        // The original widget still owns the actual displayed running/paused value.
        val elapsed = stopwatchTime(com.island.dynamicisland.live.Stopwatch(parts.timerBase))
        val sample = if (elapsed.length > parts.timerText.length) elapsed else parts.timerText
        val measured = rememberTextMeasurer().measure(sample, indicatorStyle(live), maxLines = 1, softWrap = false)
        return with(LocalDensity.current) { measured.size.width.toDp() } + 2.dp
    }
    val label = indicatorLabel(live)
    if (label.isEmpty()) return 0.dp
    val measured = rememberTextMeasurer().measure(label, indicatorStyle(live),
        maxLines = 1, softWrap = false)
    return with(LocalDensity.current) { measured.size.width.toDp() } + 2.dp
}

@Composable
internal fun IslandIndicator(np: NowPlaying?, live: LiveUpdate?, color: Color, modifier: Modifier, active: Boolean = true) {
    if (live == null) Equalizer(color, playing = np?.isPlaying == true, modifier = modifier)
    else if (live.customParts != null && live.customContent != null) {
        CustomNotificationContent(live.customContent, live.key, false, active,
            modifier.size(compactIndicatorWidth(live), 20.dp), live.customParts, timerOnly = true, compactTimer = true,
            accentColor = live.accentColor)
    }
    else Box(modifier.size(compactIndicatorWidth(live), 20.dp), contentAlignment = Alignment.Center) {
        val label = indicatorLabel(live)
        val tint = if (live.screenCapture == com.island.dynamicisland.live.ScreenCaptureKind.RECORDING)
            Color(0xFFFF554B) else if (live.screenCapture != null) Color(0xFF34C759) else Color(live.accentColor)
        NumericTickerText(label, color = tint, style = indicatorStyle(live), enabled = active, compact = true)
    }
}

/** Measure an untransformed wrapper so each row's travel reflects its own depth. */
@Composable
internal fun LiveActivityReveal(appear: () -> Float, order: Int, compactHeight: Dp,
                               topPadding: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var centerY by remember { mutableFloatStateOf(topPadding.value) }
    Box(Modifier.fillMaxWidth().onPlaced { coordinates ->
        centerY = topPadding.value + with(density) {
            (coordinates.positionInParent().y + coordinates.size.height / 2f).toDp().value
        }
    }) {
        Box(Modifier.fillMaxWidth().expandedContentEntrance(appear, order, centerY, compactHeight.value)) { content() }
    }
}

@Composable
internal fun ExpandedLiveUpdate(live: LiveUpdate, appear: () -> Float, interactive: Boolean,
                                headerHeight: Dp, topPadding: Dp, islandCorner: Dp,
                                stopwatchActive: Boolean, onOpenSource: () -> Unit, compactHeight: Dp = 36.dp, cameraSafeTop: Dp = 32.dp,
                                replacementKeys: Map<String, Int> = emptyMap()) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLiveAccent provides Color(live.accentColor)) {
        ExpandedLiveUpdateContent(live, appear, interactive, headerHeight, topPadding, islandCorner,
            stopwatchActive, onOpenSource, compactHeight, cameraSafeTop, replacementKeys)
    }
}

@Composable
private fun ExpandedLiveUpdateContent(live: LiveUpdate, appear: () -> Float, interactive: Boolean,
                                headerHeight: Dp, topPadding: Dp, islandCorner: Dp,
                                stopwatchActive: Boolean, onOpenSource: () -> Unit, compactHeight: Dp, cameraSafeTop: Dp,
                                replacementKeys: Map<String, Int>) {
    if (live.isCall) {
        CallCard(live, appear, interactive, stopwatchActive, topPadding, cameraSafeTop, compactHeight, onOpenSource)
        return
    }
    if (live.screenCapture != null) {
        ScreenCaptureCard(live, appear, interactive, stopwatchActive, compactHeight, onOpenSource)
        return
    }
    live.sportsScore?.let { score ->
        // The persistent team layer owns the header; no duplicate app attribution.
        Column(Modifier.fillMaxWidth().padding(horizontal = sportsExpandedInset)
            .padding(top = topPadding, bottom = sportsExpandedInset)) {
            LiveActivityReveal(appear, 0, compactHeight, topPadding) {
                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp)
                    .padding(start = 82.dp, end = 82.dp,
                        top = (cameraSafeTop - topPadding).coerceAtLeast(0.dp)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score.gameStatus.takeIf { it.isNotBlank() } ?: live.text,
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 21.sp)
                        if (live.packageName.equals("com.espn.score_center", ignoreCase = true)) {
                            Text("ESPN", color = Color(0xFF8E8E93), fontSize = 10.sp, lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp,
                                modifier = Modifier.padding(top = 1.dp)
                                    .then(if (interactive) Modifier.clickable(
                                        role = androidx.compose.ui.semantics.Role.Button,
                                        onClickLabel = "Open ESPN", onClick = onOpenSource) else Modifier))
                        }
                    }
                }
            }
            val sectionTitle = score.notes.firstOrNull()?.let { com.island.dynamicisland.live.sportsSectionTitle(it) }.orEmpty()
            if (score.notes.isNotEmpty() || score.players.isNotEmpty()) {
                LiveActivityReveal(appear, 1, compactHeight, topPadding) {
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
                        if (sectionTitle.isNotBlank()) {
                            ContentUpdateText(sectionTitle, replacementKey = 0,
                                modifier = Modifier.background(Color.Black).padding(horizontal = 8.dp),
                                style = TextStyle(color = Color(0xFFDADAE0), fontSize = 11.sp, lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            score.notes.drop(1).forEachIndexed { index, note ->
                LiveActivityReveal(appear, index + 2, compactHeight, topPadding) {
                    ContentUpdateText(note, replacementKey = 0, style = TextStyle(color = Color(0xFFDADAE0), fontSize = 11.sp, lineHeight = 14.sp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), maxLines = 3,
                        contentAlignment = Alignment.CenterStart)
                }
            }
            val sidedPlayers = score.players.isNotEmpty() && score.players.all { it.teamSide in 1..2 }
            if (sidedPlayers) {
                LiveActivityReveal(appear, score.notes.size + 1, compactHeight, topPadding) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (side in 1..2) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                score.players.filter { it.teamSide == side }.forEach { player ->
                                    SportsPlayerSummary(player, trailing = side == 2)
                                }
                            }
                        }
                    }
                }
            } else {
                score.players.forEachIndexed { index, player ->
                    LiveActivityReveal(appear, score.notes.size + index + 1, compactHeight, topPadding) {
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SportsPlayerPortrait(player, 24.dp)
                            ContentUpdateText(player.name, replacementKey = 0, modifier = Modifier.weight(1f),
                                style = TextStyle(color = Color.White, fontSize = 11.sp, lineHeight = 14.sp,
                                    fontWeight = FontWeight.SemiBold), contentAlignment = Alignment.CenterStart,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BoxWithConstraints(Modifier.weight(1.8f)) {
                                val style = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
                                val width = rememberTextMeasurer().measure(player.stats, style,
                                    maxLines = 1, softWrap = false).size.width
                                val available = with(LocalDensity.current) { maxWidth.toPx() } - 1f
                                val fit = if (width > 0) (available / width).coerceIn(.01f, 1f) else 1f
                                ContentUpdateText(player.stats, replacementKey = 0, style = style.copy(fontSize = 10.sp * fit,
                                    color = Color(0xFFDADAE0), textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd, fillWidth = true, maxLines = 1)
                            }
                        }
                    }
                }

            }
        }

        return
    }
    if (live.customParts != null && live.customContent != null) {
        CompactTimedLiveUpdate(live, appear, interactive, headerHeight, topPadding, islandCorner, stopwatchActive, onOpenSource, compactHeight, cameraSafeTop)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val edgeInset = 12.dp
    val buttonCorner = 18.dp
    val secondaryClock = live.text.isNotBlank() || live.details.isNotBlank()
    val isSports = live.sportsScore != null
    val headline = com.island.dynamicisland.live.activityHeadline(live)
    val cameraInset = com.island.dynamicisland.live.activityCameraInset(cameraSafeTop.value, topPadding.value).dp
    Column(Modifier.fillMaxWidth().padding(horizontal = edgeInset).padding(top = topPadding, bottom = edgeInset)) {
        LiveActivityReveal(appear, 0, compactHeight, topPadding) {
            Row(Modifier.heightIn(min = headerHeight).fillMaxWidth()
                .then(if (interactive) Modifier.clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = "Open ${live.appName}", onClick = onOpenSource) else Modifier),
                verticalAlignment = Alignment.Top) {
                Spacer(Modifier.width(if (live.isNavigation) 60.dp else 44.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    // Attribution and elapsed time sit beside the camera. Only the
                    // full-width headline needs to clear its lower edge.
                    Row(Modifier.fillMaxWidth().heightIn(min = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(live.appName, color = Color(0xFF8E8E93), fontSize = 10.sp, lineHeight = 13.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (secondaryClock) live.stopwatch?.let { clock ->
                            NumericTickerText(stopwatchTime(clock, active = stopwatchActive), enabled = stopwatchActive, color = Color(0xFFA5A5AD),
                                fontSize = 13.sp, lineHeight = 17.sp, fontFamily = IslandFontFamily)
                        }
                    }
                    Spacer(Modifier.height((cameraInset - 17.dp).coerceAtLeast(3.dp)))
                    if (live.isNavigation) {
                        ContentUpdateText(live.turnDistance ?: "Next turn", replacementKey = 0,
                            style = TextStyle(color = liveAccent, fontSize = 22.sp, lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold), contentAlignment = Alignment.CenterStart,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    ContentUpdateText(headline, replacementKey = replacementKeys["headline"] ?: 0, style = TextStyle(color = Color.White,
                        fontSize = if (live.isNavigation) 15.sp else 18.sp,
                        lineHeight = if (live.isNavigation) 19.sp else 22.sp,
                        fontWeight = FontWeight.SemiBold), contentAlignment = Alignment.CenterStart,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                live.countdown?.let { clock ->
                    NumericTickerText(countdownTime(clock, active = stopwatchActive), enabled = stopwatchActive, color = liveAccent,
                        fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium,
                        fontFamily = IslandFontFamily, style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp, end = 8.dp))
                }
            }
        }
        if (live.customContent != null && !isSports) {
            LiveActivityReveal(appear, 1, compactHeight, topPadding) {
                CustomNotificationContent(live.customContent, live.key, interactive, stopwatchActive,
                    Modifier.fillMaxWidth()
                        .then(if (interactive) Modifier.clickable(onClick = onOpenSource) else Modifier)
                        .padding(top = 8.dp))
            }
        }
        if (!secondaryClock && !isSports) live.stopwatch?.let { clock ->
            LiveActivityClock(clock, appear, compactHeight, topPadding, stopwatchActive, interactive, onOpenSource)
        }
        if (live.text.isNotBlank() && !live.text.equals(headline, ignoreCase = true) && !isSports) LiveActivityReveal(appear, 1, compactHeight, topPadding) {
            ContentUpdateText(live.text, replacementKey = replacementKeys["text"] ?: 0,
                style = TextStyle(color = Color(0xFFDADAE0), fontSize = 16.sp, lineHeight = 22.sp),
                contentAlignment = Alignment.CenterStart, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier
                    .then(if (interactive) Modifier.clickable(onClick = onOpenSource) else Modifier)
                    .padding(horizontal = 8.dp).padding(top = 12.dp))
        }
        if (live.details.isNotBlank() && !live.details.equals(live.text, ignoreCase = true) && !live.details.equals(live.title, ignoreCase = true) && !isSports) LiveActivityReveal(appear, 2, compactHeight, topPadding) {
            ContentUpdateText(live.details, replacementKey = 0,
                style = TextStyle(color = Color(0xFFA5A5AD), fontSize = 14.sp, lineHeight = 20.sp),
                contentAlignment = Alignment.CenterStart, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier
                    .then(if (interactive) Modifier.clickable(onClick = onOpenSource) else Modifier)
                    .padding(horizontal = 8.dp).padding(top = if (live.text.isNotBlank()) 6.dp else 12.dp))
        }
        if (!live.isNavigation && !isSports && (live.indeterminate || live.progress != null)) {
            Spacer(Modifier.height(12.dp))
            LiveActivityReveal(appear, 2, compactHeight, topPadding) {
                if (live.indeterminate) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(4.dp), color = liveAccent)
                else live.progress?.let { progress ->
                    if (live.progressStyle != null) LiveProgressTrack(live.progressStyle, progress, liveAccent, active = stopwatchActive)
                    else {
                        val displayedProgress = rememberLiveProgress(progress, active = stopwatchActive)
                        LinearProgressIndicator(progress = { displayedProgress.value },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(4.dp),
                            color = liveAccent, trackColor = liveAccent.copy(alpha = .18f))
                    }
                }
            }
        }
        if (live.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LiveActivityReveal(appear, 3, compactHeight, topPadding) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    live.actions.forEach { action ->
                        LiveActivityActionButton(action.title, interactive, buttonCorner, edgeInset,
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) {
                            sendLiveActivityIntent(context, action.intent)
                        }
                    }
                }
            }
        }
    }
}

/** One row: source, elapsed time, then the source notification's original controls. */
@Composable
private fun CompactTimedLiveUpdate(live: LiveUpdate, appear: () -> Float, interactive: Boolean,
                                   headerHeight: Dp, topPadding: Dp, islandCorner: Dp, active: Boolean,
                                   onOpenSource: () -> Unit, compactHeight: Dp, cameraSafeTop: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasInlineControls = live.actions.isEmpty() && live.customParts!!.controlIds.isNotEmpty()
    var lastInline by remember(live.key) { mutableStateOf(live) }
    var lastActions by remember(live.key) { mutableStateOf(live.actions) }
    SideEffect {
        if (hasInlineControls) lastInline = live
        if (live.actions.isNotEmpty()) lastActions = live.actions
    }
    val inline = if (hasInlineControls) live else lastInline
    val displayedActions = live.actions.ifEmpty { lastActions }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = topPadding, bottom = 12.dp)) {
        LiveActivityReveal(appear, 0, compactHeight, topPadding) {
            Row(Modifier.fillMaxWidth().heightIn(min = headerHeight.coerceAtLeast(48.dp)),
                verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(44.dp))
                Column(Modifier.weight(1f).then(if (interactive) Modifier.clickable(
                    onClickLabel = "Open ${live.appName}", onClick = onOpenSource) else Modifier)) {
                    val headline = com.island.dynamicisland.live.activityHeadline(live)
                    Text(live.appName, color = Color(0xFF8E8E93), fontSize = 10.sp, lineHeight = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height((com.island.dynamicisland.live.activityCameraInset(
                        cameraSafeTop.value, topPadding.value).dp - 13.dp).coerceAtLeast(3.dp)))
                    Text(headline, color = Color.White, fontSize = 15.sp, lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // Paused Recorder publishes Resume/Save as standard actions instead
                // of buttons inside its custom layout. Never drop them on this path.
                // Keep the timer host mounted while the control slot expands/contracts.
                CustomNotificationContent(live.customContent!!, live.key, interactive, active,
                    Modifier.width(96.dp).height(48.dp)
                        .then(if (interactive) Modifier.clickable(onClick = onOpenSource) else Modifier),
                    live.customParts, timerOnly = true)
                AnimatedVisibility(hasInlineControls,
                    enter = expandHorizontally(tween(320), expandFrom = Alignment.End) + fadeIn(tween(240)),
                    exit = shrinkHorizontally(tween(320), shrinkTowards = Alignment.End) + fadeOut(tween(180))) {
                    CustomNotificationContent(inline.customContent!!, live.key,
                        interactive && hasInlineControls, active,
                        Modifier.width((inline.customParts!!.controlIds.size * 54).dp).height(48.dp),
                        inline.customParts, controlsOnly = true)
                }
            }
        }
        AnimatedVisibility(live.actions.isNotEmpty(),
            enter = expandVertically(tween(320), expandFrom = Alignment.Top) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(320), shrinkTowards = Alignment.Top) + fadeOut(tween(180))) {
            Column {
              Spacer(Modifier.height(8.dp))
              LiveActivityReveal(appear, 1, compactHeight, topPadding) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayedActions.forEach { action ->
                        LiveActivityActionButton(action.title, interactive && live.actions.isNotEmpty(), (islandCorner - 12.dp).coerceAtLeast(0.dp), 12.dp,
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) {
                            sendLiveActivityIntent(context, action.intent)
                        }
                    }
                }
              }
            }
        }
    }
}


/** Leader rotations are routine updates; only score changes trigger expansion. */
@Composable
private fun SportsPlayerPortrait(player: com.island.dynamicisland.live.SportsPlayer, size: Dp) {
    ContentUpdateCrossfade(player, modifier = Modifier.size(size),
        stateKey = { 0 }) { value ->
        value.portrait?.let {
            Image(it.asImageBitmap(), null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        } ?: Box(Modifier.size(size))
    }
}

/** Team-aligned, compact leader summary with the portrait preserved. */
@Composable
private fun SportsPlayerSummary(player: com.island.dynamicisland.live.SportsPlayer, trailing: Boolean) {
    val alignment = if (trailing) Alignment.End else Alignment.Start
    val textAlign = if (trailing) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!trailing) SportsPlayerPortrait(player, 18.dp)
            ContentUpdateText(player.name, replacementKey = 0, modifier = Modifier.weight(1f),
                style = TextStyle(color = Color.White, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = textAlign),
                contentAlignment = if (trailing) Alignment.CenterEnd else Alignment.CenterStart, fillWidth = true,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing) SportsPlayerPortrait(player, 18.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val style = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
            val measured = rememberTextMeasurer().measure(player.stats, style, maxLines = 1, softWrap = false).size.width
            val available = with(LocalDensity.current) { maxWidth.toPx() } - 1f
            val fit = if (measured > 0) (available / measured).coerceIn(.01f, 1f) else 1f
            ContentUpdateText(player.stats, replacementKey = 0, style = style.copy(fontSize = 10.sp * fit,
                color = Color(0xFFA5A5AD), textAlign = textAlign), modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (trailing) Alignment.CenterEnd else Alignment.CenterStart, fillWidth = true, maxLines = 1)
        }
    }
}

@Composable
private fun LiveActivityClock(clock: com.island.dynamicisland.live.Stopwatch,
                              appear: () -> Float, compactHeight: Dp, topPadding: Dp, active: Boolean,
                              interactive: Boolean, onOpenSource: () -> Unit) {
    LiveActivityReveal(appear, 1, compactHeight, topPadding) {
        NumericTickerText(stopwatchTime(clock, precise = true, active = active), enabled = active, fractionalTime = true, color = liveAccent,
            fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light,
            fontFamily = IslandFontFamily,
            modifier = Modifier.fillMaxWidth()
                .then(if (interactive) Modifier.clickable(onClick = onOpenSource) else Modifier)
                .padding(top = 8.dp, bottom = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LiveActivityActionButton(title: String, interactive: Boolean, corner: Dp, inset: Dp,
                                     modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && interactive) .94f else 1f,
        spring(dampingRatio = .65f, stiffness = 650f), label = "liveActionPress")
    val background by animateColorAsState(if (pressed && interactive) liveAccent.copy(alpha = .30f) else liveAccent.copy(alpha = .12f),
        label = "liveActionHighlight")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(corner)).background(background)
        // Hidden controls must not intercept the compact island's hold gesture.
        .then(if (interactive) Modifier.clickable(interactionSource = interaction,
            indication = ripple(color = liveAccent), role = androidx.compose.ui.semantics.Role.Button,
            onClick = onClick) else Modifier)
        .padding(inset), contentAlignment = Alignment.Center) {
        Text(title, color = liveAccent, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TeamLogo(bitmap: android.graphics.Bitmap?, name: String?, size: Dp) {
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * .2f)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    } else {
        Text(
            name?.take(3)?.uppercase() ?: "",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IslandFontFamily
        )
    }
}

/** The same two team groups stay mounted from camera pill to expanded corners. */
@Composable
internal fun CompactSportsLiveActivity(score: SportsScore, height: Dp, grouped: Boolean = false,
                                      expansion: Float = 0f, celebration: SportsScoreChange? = null,
                                      grouping: Float = if (grouped) 1f else 0f) {
    val p = expansion.coerceIn(0f, 1f)
    val logoSize = androidx.compose.ui.unit.lerp(height * .62f, 44.dp, p)
    val edge = androidx.compose.ui.unit.lerp(height * .20f, 40.dp, p)
    val teamHeight = androidx.compose.ui.unit.lerp(height, 104.dp, p)
    val compactTeamsWidth = sportsScoreLeadingWidth(score, height) + sportsScoreTrailingWidth(score, height)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val group = grouping.coerceIn(0f, 1f)
        val cameraSideSpace = (sportsCameraSideSpace(maxWidth.value, edge.value,
            compactTeamsWidth.value, p) * group).dp
        androidx.compose.ui.layout.Layout(modifier = Modifier.fillMaxWidth().height(teamHeight)
            .offset(y = sportsExpandedInset * p), content = {
            @Composable fun Team(leading: Boolean) {
                val name = if (leading) score.teamOne else score.teamTwo
                // A shared layout moves from a horizontal compact pair to a
                // vertical logo/score stack without ever crossing the center status.
                val logo = if (leading) score.teamOneLogo else score.teamTwoLogo
                val value = if (leading) score.scoreOne else score.scoreTwo
                androidx.compose.ui.layout.Layout(content = {
                    TeamLogo(logo, name, logoSize)
                    Text(value, color = Color(com.island.dynamicisland.live.readableTeamColor(
                        if (leading) score.teamOneColor else score.teamTwoColor)), fontSize = (13f + 17f * p).sp,
                        fontWeight = FontWeight.Bold, fontFamily = IslandFontFamily,
                        maxLines = 1, softWrap = false)
                    Text(name.orEmpty(), color = Color(com.island.dynamicisland.live.readableTeamColor(
                        if (leading) score.teamOneColor else score.teamTwoColor)), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            val radius = ExpandedContentMotion.blurRadiusDp(p).dp.toPx()
                            renderEffect = if (alpha > 0f && radius > .01f) BlurEffect(radius, radius, TileMode.Decal) else null
                            clip = false
                        })
                    val delta = if (leading) celebration?.deltaOne ?: 0 else celebration?.deltaTwo ?: 0
                    Text(if (delta > 0) "+$delta" else "", fontSize = 15.sp, lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(com.island.dynamicisland.live.readableTeamColor(
                            if (leading) score.teamOneColor else score.teamTwoColor)),
                        modifier = Modifier.graphicsLayer {
                            val t = celebration?.progress?.value ?: 0f
                            alpha = if (p < .99f) 0f else
                                (t / .18f).coerceIn(0f, 1f) * ((1f - t) / .3f).coerceIn(0f, 1f)
                            translationY = -10.dp.toPx() * t
                            scaleX = .8f + .2f * (t / .2f).coerceIn(0f, 1f)
                            scaleY = scaleX
                        })
                }) { items, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val image = items[0].measure(loose)
                    val points = items[1].measure(loose)
                    val label = items[2].measure(loose)
                    val badge = items[3].measure(loose)
                    val gap = 6.dp.toPx()
                    val horizontalWidth = image.width + gap + points.width
                    val verticalWidth = maxOf(image.width, points.width, label.width).toFloat()
                    val w = (horizontalWidth + (verticalWidth - horizontalWidth) * p).toInt()
                    val h = (maxOf(image.height, points.height) +
                        (image.height + points.height + label.height + 4.dp.toPx() - maxOf(image.height, points.height)) * p).toInt()
                    fun mix(start: Float, end: Float) = (start + (end - start) * p).toInt()
                    // Compact content stays vertically centered; expanded logos
                    // stay anchored to their expanded top inset.
                    val stackTop = (((teamHeight.toPx() - h) / 2f) * (1f - p)).toInt()
                    layout(w, teamHeight.roundToPx()) {
                        image.place(mix(if (leading) 0f else points.width + gap, (w - image.width) / 2f),
                            stackTop + mix((h - image.height) / 2f, 0f))
                        points.place(mix(if (leading) image.width + gap else 0f, (w - points.width) / 2f),
                            stackTop + mix((h - points.height) / 2f, image.height + 4.dp.toPx()))
                        label.place((w - label.width) / 2, stackTop + h - label.height)
                        badge.place(if (leading) -badge.width - 4.dp.roundToPx() else w + 4.dp.roundToPx(),
                            stackTop + image.height + 4.dp.roundToPx())
                    }
                }
            }
            Team(true)
            Team(false)
            Box(Modifier.size(width = 4.dp, height = 1.5.dp)
                .background(Color.White.copy(alpha = .45f), RoundedCornerShape(1.dp)))
        }) { items, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val first = items[0].measure(loose)
            val second = items[1].measure(loose)
            val separator = items[2].measure(loose)
            val firstX = edge.toPx() + cameraSideSpace.toPx()
            val secondX = constraints.maxWidth - edge.toPx() - second.width
            layout(constraints.maxWidth, teamHeight.roundToPx()) {
                first.place(firstX.toInt(), 0)
                second.place(secondX.toInt(), 0)
                val separatorX = (firstX + first.width + secondX - separator.width) / 2f
                val separatorY = (height.toPx() - separator.height) / 2f
                separator.placeWithLayer(separatorX.toInt(), separatorY.toInt()) {
                    alpha = group * (1f - p * 3f).coerceIn(0f, 1f)
                }
            }
        }
    }
}

@Composable
internal fun CompactLiveActivity(live: LiveUpdate, height: Dp, grouped: Boolean = false,
                                 grouping: Float = if (grouped) 1f else 0f) {
    if (live.sportsScore != null) {
        CompactSportsLiveActivity(live.sportsScore, height, grouped, grouping = grouping)
        return
    }
    Row(Modifier.fillMaxSize().padding(horizontal = height * .2f), verticalAlignment = Alignment.CenterVertically) {
        if (grouped) Spacer(Modifier.weight(1f))
        IslandArtwork(null, live, height * compactLiveIconFraction, 0.dp, Modifier, liveIconInset = { 0f })
        if (grouped) Spacer(Modifier.width(6.dp)) else Spacer(Modifier.weight(1f))
        IslandIndicator(null, live, liveAccent, Modifier)
    }
}

/** One continuously placed layer carries the secondary's contents into the camera pill and back. */
@Composable
internal fun SecondaryActivityContent(np: NowPlaying?, live: LiveUpdate?, music: Boolean,
                                      height: Dp, transfer: () -> Float) {
    val liveIndicatorWidth = compactIndicatorWidth(live, height)
    live?.sportsScore?.takeIf { !music }?.let { score ->
        Box(Modifier.fillMaxSize()) {
            IslandArtwork(null, live, height * .6f, 0.dp,
                Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - transfer().coerceIn(0f, 1f) })
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = transfer().coerceIn(0f, 1f) }) {
                CompactSportsLiveActivity(score, height, grouped = true)
            }
        }
        return
    }
    androidx.compose.ui.layout.Layout(content = {
        IslandArtwork(np, if (music) null else live, height * (if (music) .6f else compactLiveIconFraction), height * .6f * .32f,
            Modifier.graphicsLayer { alpha = if (music) transfer() else 1f }, liveIconInset = { 0f })
        if (music) Equalizer(np?.let { Color(it.staticArtworkAccentColor).brightAccent() } ?: Color.White,
            np?.isPlaying == true, modifier = Modifier.size(height * .54f, height * .42f))
        else IslandIndicator(null, live, liveAccent, Modifier.graphicsLayer { alpha = transfer() })
    }) { measurables, constraints ->
        val artSize = (height * (if (music) .6f else compactLiveIconFraction)).roundToPx()
        val art = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(artSize, artSize))
        val indicatorWidth = if (music) (height * .54f).roundToPx() else liveIndicatorWidth.roundToPx()
        val indicatorHeight = if (music) (height * .42f).roundToPx() else 20.dp.roundToPx()
        val indicator = measurables[1].measure(androidx.compose.ui.unit.Constraints.fixed(indicatorWidth, indicatorHeight))
        val t = transfer().coerceIn(0f, 1f)
        val h = height.roundToPx()
        val pad = (height * .2f).toPx()
        val minimalWidth = minOf(h, constraints.maxWidth)
        val artX = (minimalWidth - artSize) / 2f * (1f - t) + pad * t
        val endX = if (music) constraints.maxWidth - pad - indicatorWidth else constraints.maxWidth - indicatorWidth - pad
        val indicatorX = (minimalWidth - indicatorWidth) / 2f * (1f - t) + endX * t
        layout(constraints.maxWidth, h) {
            art.place(artX.toInt(), (h - artSize) / 2)
            indicator.place(indicatorX.toInt(), (h - indicatorHeight) / 2)
        }
    }
}
