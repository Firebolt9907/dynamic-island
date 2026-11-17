package com.island.dynamicisland.ui.island

import android.content.Intent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.IslandConfig
import com.island.dynamicisland.media.MediaRepository
import com.island.dynamicisland.ui.theme.DynamicIslandTheme

/** Paint the edge outside the black silhouette without changing content or touch bounds. */
private fun Modifier.islandEdge(
    color: Color,
    expansion: () -> Float,
    collapsedAlpha: Float = 1f,
    smoothing: Float = Squircle.SMOOTHING,
    cornerPx: () -> Float,
): Modifier = drawWithContent {
    val corner = cornerPx().coerceIn(0f, size.minDimension / 2f)
    val edge = 1.5.dp.toPx()
    val silhouette = Squircle.path(Rect(Offset.Zero, size), corner, smoothing = smoothing)
    // Stroke the silhouette itself, twice as wide: the outer half is the visible edge and the
    // inner half sits under the opaque fill. Abutting a separate outer ring against the clip
    // left a hairline of background where both anti-aliased edges met, and the scaled outer
    // path drifted from a true offset around the corners.
    drawPath(silhouette,
        // Fade with the silhouette's expansion, including interactive dragging.
        color = color.copy(alpha = color.alpha * lerpFloat(collapsedAlpha, .3f, expansion().coerceIn(0f, 1f))),
        style = Stroke(width = edge * 2f))
    // Canvas path clips are not reliably anti-aliased, which left a stair-stepped black rim
    // biting into the edge. Paint the silhouette itself (always anti-aliased), and clip the
    // content a pixel inside it so any jagged clip boundary stays hidden under that fill.
    drawPath(silhouette, Color.Black)
    val inset = 1f
    val inner = Squircle.path(Rect(inset, inset, size.width - inset, size.height - inset),
        (corner - inset).coerceAtLeast(0f), smoothing = smoothing)
    clipPath(inner) { this@drawWithContent.drawContent() }
}

/** The visual host covers the display; the service keeps a separate touch target on the pill. */
@Composable
fun IslandRoot(
    bluetoothAudioEvent: com.island.dynamicisland.audio.BluetoothAudioEvent? = null,
    lockVisible: Boolean = false,
    surfaceVisible: Boolean = true,
    onSurfaceHidden: () -> Unit = {},
    locked: Boolean = false,
    keyguardShowing: Boolean = false,
    dismissRequest: Int = 0,
    foregroundPackage: String? = null,
    onBoundsChanged: (Int, Int, Int, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onSecondaryBoundsChanged: (List<ActivityTouchBounds>) -> Unit = {},
    /** Height in px of the screen strip the island can paint into; the host sizes its window to it. */
    onSurfaceHeightChanged: (Int) -> Unit = {},
) {
    DynamicIslandTheme {
        val powerSaveMode by com.island.dynamicisland.power.rememberPowerSaveMode()
        val thermalStatus by com.island.dynamicisland.power.rememberThermalStatus()
        androidx.compose.runtime.CompositionLocalProvider(
            com.island.dynamicisland.power.LocalPowerSaveMode provides powerSaveMode,
            com.island.dynamicisland.power.LocalCompactTickerAllowed provides
                com.island.dynamicisland.power.allowCompactTicker(powerSaveMode, thermalStatus),
            com.island.dynamicisland.power.LocalInteractionMotionAllowed provides
                com.island.dynamicisland.power.allowInteractionMotion(powerSaveMode, thermalStatus),
        ) {
            val calibrating by IslandConfig.calibrating.collectAsStateWithLifecycle()
            LaunchedEffect(surfaceVisible, calibrating) {
                if (!surfaceVisible && calibrating) onSurfaceHidden()
            }
            if (calibrating) CalibrationLayer()
            else if (lockVisible) {
                val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
                val guide by IslandConfig.cameraGuide.collectAsStateWithLifecycle()
                val density = LocalDensity.current
                SideEffect {
                    onBoundsChanged(0, 0, 0, 0, false)
                    onSecondaryBoundsChanged(emptyList())
                    // The lock pill sits on the camera; it needs only the compact strip.
                    val bottom = maxOf(tuning.topOffsetDp + tuning.collapsedHeightDp,
                        (guide?.centerYDp ?: 0f) + tuning.collapsedHeightDp / 2f)
                    onSurfaceHeightChanged(with(density) { (bottom.dp + IslandShadowMargin).roundToPx() })
                }
                LockScreenIsland(locked, keyguardShowing)
            } else BluetoothAudioPresentation(bluetoothAudioEvent.takeIf { surfaceVisible },
                onBoundsChanged = onBoundsChanged) { presenting, reportBounds ->
                IslandPill(dismissRequest, foregroundPackage, reportBounds, { bounds ->
                    onSecondaryBoundsChanged(if (presenting) emptyList() else bounds)
                }, surfaceVisible, onSurfaceHidden, onSurfaceHeightChanged)
            }
        }
    }
}

/**
 * Room below the card for its shadow. The 32 dp blur is a Gaussian of sigma about 18.5 dp,
 * so this keeps over four sigma of falloff, plus the 6 dp the expanded shadow spreads.
 */
private val IslandShadowMargin = 80.dp

/** Full-screen guide used while aligning the island to the physical camera. */
@Composable
private fun CalibrationLayer() {
    val guide by IslandConfig.cameraGuide.collectAsStateWithLifecycle()
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // Same width source as IslandPill: the physical display, not the (inset) configuration.
    val physicalWidthDp by IslandConfig.screenWidthDp.collectAsStateWithLifecycle()
    val screenWidthDp = physicalWidthDp.takeIf { it > 0f } ?: LocalConfiguration.current.screenWidthDp.toFloat()

    Box(Modifier.fillMaxSize()) {
        // Thin white ring marking the real camera cutout — anything under it is hidden by the lens.
        guide?.let { g ->
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f + with(density) { g.centerXFromScreenCenterDp.dp.toPx() }
                val cy = with(density) { g.centerYDp.dp.toPx() }
                val r = with(density) { g.radiusDp.dp.toPx() }
                drawCircle(
                    color = Color.White,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                )
            }
        }

        // Live preview of the collapsed pill at its configured spot.
        val leftDp = screenWidthDp / 2f + tuning.centerXOffsetDp - tuning.collapsedWidthDp / 2f
        Box(
            modifier = Modifier
                .offset(x = leftDp.dp, y = tuning.topOffsetDp.dp)
                .size(tuning.collapsedWidthDp.dp, tuning.collapsedHeightDp.dp)
                .clip(SquircleShape(50))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CollapsedMusic(
                MediaRepository.nowPlaying.collectAsStateWithLifecycle().value,
                heightDp = tuning.collapsedHeightDp.dp,
            )
        }
    }
}

/** One progress value drives the silhouette, shared media elements, and content reveal. */
@Composable
private fun IslandPill(dismissRequest: Int, foregroundPackage: String?, onBoundsChanged: (Int, Int, Int, Int, Boolean) -> Unit,
                       onSecondaryBoundsChanged: (List<ActivityTouchBounds>) -> Unit,
                       surfaceVisible: Boolean, onSurfaceHidden: () -> Unit,
                       onSurfaceHeightChanged: (Int) -> Unit) {
    val currentActivities by com.island.dynamicisland.live.LiveUpdateRepository.activities.collectAsStateWithLifecycle()
    // Opening an app from the island treats it as foreground at once. Accessibility window
    // reports trail the launch animation by hundreds of milliseconds, which would otherwise
    // keep the tapped bubble on screen and the other app's bubble hidden.
    var launchingPackage by remember { mutableStateOf<String?>(null) }
    var launchRequest by remember { mutableStateOf(0) }
    val latestForegroundPackage by rememberUpdatedState(foregroundPackage)
    LaunchedEffect(launchRequest) {
        val target = launchingPackage ?: return@LaunchedEffect
        // Ignore stale reports of the app being left, then hand back once the system agrees.
        delay(LAUNCH_ANTICIPATION_MIN_MS)
        withTimeoutOrNull(LAUNCH_ANTICIPATION_TIMEOUT_MS) {
            snapshotFlow { latestForegroundPackage }.first { it == target }
        }
        launchingPackage = null
    }
    fun anticipateForeground(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        launchingPackage = packageName
        launchRequest++
    }
    @Suppress("NAME_SHADOWING")
    val foregroundPackage = launchingPackage ?: foregroundPackage
    val hiddenActivityKeys = currentActivities.filter { isLiveActivitySourceOpen(foregroundPackage, it) }.map { it.key }.toSet()
    val visibleActivities = currentActivities.filterNot { it.key in hiddenActivityKeys }
    var presentedActivities by remember { mutableStateOf(currentActivities) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var touchedKey by remember { mutableStateOf<String?>(null) }
    val nowPlaying by MediaRepository.nowPlaying.collectAsStateWithLifecycle()
    var retainedActivities by remember { mutableStateOf(visibleActivities) }
    var retainedMusic by remember { mutableStateOf(nowPlaying) }
    var retainedMusicShown by remember { mutableStateOf(false) }
    val musicSource = nowPlaying ?: retainedMusic
    val neutralEdge = Color.White.copy(alpha = .22f)
    // Reuse the cached artwork accent; no bitmap processing in the drawing path.
    val musicEdge by animateColorAsState(
        targetValue = musicSource?.takeIf { it.staticAlbumArt != null }?.let {
            Color(it.staticArtworkAccentColor).brightAccent().copy(alpha = .36f)
        } ?: neutralEdge,
        animationSpec = tween(350), label = "musicEdge",
    )
    val scope = rememberCoroutineScope()
    val reflow = remember { CameraReflowMotion() }
    val haptics = rememberIslandHaptics()
    val motion = remember(scope) { IslandMotion(scope) }
    // At rest, only the compact pill and its shadow are drawn. Sizing the shadow layer and the
    // host window to that strip, rather than to the expanded card, keeps GPU buffers small.
    // derivedStateOf flips once per expansion, so animation frames never trigger remeasure.
    val compactSurface by remember(motion) { derivedStateOf {
        !motion.expanded && !motion.previewActive && !motion.transferringActivities && motion.fraction == 0f
    } }
    var musicHidden by remember { mutableStateOf(musicSource?.isPlaying != true) }
    var interaction by remember { mutableStateOf(0) }
    var activityTouchActive by remember { mutableStateOf(false) }
    /** A peek owns the island: everything leaves through the camera, the peek grows out of it and returns. */
    var musicPeekStaged by remember { mutableStateOf(false) }
    var musicPeekRequest by remember { mutableStateOf(0) }
    /** 0 = the whole island drawn into the camera lens, 1 = at full size. Drives the peek's in/out. */
    val peekLens = remember { Animatable(1f) }
    val compact by remember(motion) { derivedStateOf {
        !motion.expanded && !motion.previewActive &&
            motion.progress.value == 0f && motion.transfer.value == 0f
    } }
    LaunchedEffect(musicSource?.isPlaying, musicSource?.packageName, compact, interaction) {
        if (musicSource?.isPlaying == true) musicHidden = false
        else if (compact && !musicHidden) {
            delay(5_000)
            musicHidden = true
        }
    }
    val sourceAppOpen = isMusicSourceOpen(foregroundPackage, nowPlaying?.packageName)
    val visible = surfaceVisible && (presentedActivities.any { it.key !in hiddenActivityKeys } || (!sourceAppOpen && nowPlaying != null && (!musicHidden || nowPlaying?.isPlaying == true)))
    LaunchedEffect(sourceAppOpen) {
        if (sourceAppOpen && (selectedKey == null || selectedKey == IslandActivities.MUSIC)) motion.dismiss()
        else if (!sourceAppOpen && nowPlaying != null) {
            musicHidden = false
            interaction++ // Restart the paused-music grace period when returning from its app.
        }
    }
    val visibilityMotion = remember { Animatable(0f) }
    val visibility = visibilityMotion.value
    LaunchedEffect(visible, surfaceVisible) {
        visibilityMotion.animateTo(if (visible) 1f else 0f,
            tween(if (visible) 520 else 380, easing = FastOutSlowInEasing))
        if (!surfaceVisible) onSurfaceHidden()
    }
    SideEffect {
        if (visible) {
            retainedActivities = presentedActivities.filterNot { it.key in hiddenActivityKeys }
            retainedMusic = nowPlaying
            retainedMusicShown = !sourceAppOpen && musicSource != null && (!musicHidden || musicSource.isPlaying)
        } else if (visibility == 0f) {
            retainedActivities = emptyList()
            retainedMusic = null
            retainedMusicShown = false
        }
    }
    // Publish notification changes only after deciding whether they need expansion.
    // This also prevents a single frame of new content before the effect starts.
    // Keep the complete snapshot: filtering it at publication time and again at
    // render time creates an empty frame when switching between two source apps.
    var heldActivity by remember { mutableStateOf<com.island.dynamicisland.live.LiveUpdate?>(null) }
    var replacementKeys by remember { mutableStateOf(emptyMap<Pair<String, String>, Int>()) }
    var pendingReplacementKey by remember { mutableStateOf<String?>(null) }
    fun publishActivities(updates: List<com.island.dynamicisland.live.LiveUpdate>, changedKey: String?) {
        val before = presentedActivities.firstOrNull { it.key == changedKey }
        val after = updates.firstOrNull { it.key == changedKey }
        if (before != null && after != null) {
            val fields = com.island.dynamicisland.live.autoExpansionTextChanges(before, after)
            replacementKeys = replacementKeys.toMutableMap().apply {
                fields.forEach { field ->
                    val key = after.key to field
                    this[key] = (this[key] ?: 0) + 1
                }
            }
        }
        presentedActivities = updates
    }
    // Fade out exactly what was last shown. Opening an activity's app hides it at the
    // same moment the island becomes invisible; re-filtering or re-deciding music here
    // would swap a hidden paused-music bubble in for the activity during the fade.
    val fadingOut = !visible && visibility > 0f
    val sourceActivities = if (fadingOut) retainedActivities
        else presentedActivities.filterNot { it.key in hiddenActivityKeys }
    val sportsPresentation = presentSportsScores(sourceActivities, onScoreChangeReady = { haptics.score() }) { key ->
        motion.expanded && motion.fraction >= .99f &&
            (selectedKey ?: sourceActivities.firstOrNull()?.key) == key
    }
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    // Keep paused artwork in place until the fade finishes.
    val selectedSlots = IslandActivities.select(sportsPresentation.activities.map { it.key },
        if (fadingOut) retainedMusicShown && musicSource != null
        else !sourceAppOpen && musicSource != null && (!musicHidden || musicSource.isPlaying))
    // While a peek runs, music is presented alone; the other activities return afterwards.
    val presentedSlots = if (musicPeekStaged && IslandActivities.MUSIC in selectedSlots.keys)
        IslandActivities(IslandActivities.MUSIC, emptyList()) else selectedSlots
    // A membership change in the compact island reflows through the camera. The outgoing
    // layout, including departed content, stays on screen until it is inside the lens.
    var shownSlots by remember { mutableStateOf(presentedSlots) }
    // Tapping a bubble opens its app, hiding it while the tap's rebound is still running.
    // Keep the old layout until the island settles, then reflow; only an expanded card
    // (which owns the transition itself) adopts the new layout immediately.
    val reflowRequested = tuning.reflowThroughCamera && !motion.expanded && visible && visibility >= 1f &&
        shownSlots.primary != null && presentedSlots.primary != null &&
        presentedSlots.keys != shownSlots.keys
    val reflowActive by remember(reflow) { derivedStateOf { reflow.active } }
    val holdingSlots = reflowActive || reflowRequested
    val slots = if (holdingSlots) shownSlots else presentedSlots
    SideEffect { if (!holdingSlots) shownSlots = presentedSlots }
    val latestHoldingSlots by rememberUpdatedState(holdingSlots)
    val departedActivities = remember { mutableMapOf<String, com.island.dynamicisland.live.LiveUpdate>() }
    val departedMusic = remember { RetainedValue<com.island.dynamicisland.media.NowPlaying?>(null) }
    sportsPresentation.activities.forEach { departedActivities[it.key] = it }
    departedActivities.keys.retainAll(sportsPresentation.activities.map { it.key }.toSet() + slots.keys)
    if (musicSource != null) departedMusic.value = musicSource
    val activities = if (!holdingSlots) sportsPresentation.activities
        else sportsPresentation.activities + slots.keys.filter { key ->
            sportsPresentation.activities.none { it.key == key } }.mapNotNull { departedActivities[it] }
    val np = musicSource ?: departedMusic.value.takeIf { holdingSlots && IslandActivities.MUSIC in slots.keys }
    val selection = if (motion.secondary) selectedKey?.takeIf { it in slots.secondaries } ?: slots.primary else slots.primary
    val live = activities.firstOrNull { it.key == selection }
    val primaryLive = activities.firstOrNull { it.key == slots.primary }
    val screenWidthDp by IslandConfig.screenWidthDp.collectAsStateWithLifecycle()
    val cameraGuide by IslandConfig.cameraGuide.collectAsStateWithLifecycle()
    val deviceCorner by IslandConfig.topCornerRadiusDp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val expanded = motion.expanded
    val hasMedia = np != null || live != null
    // Gesture detectors are keyed coarsely so a held press survives recomposition. Read the
    // latest content when a callback fires, rather than what was shown when the detector began.
    val gestureLive by rememberUpdatedState(live)
    val gestureNp by rememberUpdatedState(np)
    val gestureSelection by rememberUpdatedState(selection)
    val gestureSlots by rememberUpdatedState(slots)
    val gestureHasMedia by rememberUpdatedState(hasMedia)
    val gestureActivities by rememberUpdatedState(activities)

    LaunchedEffect(slots.primary, slots.secondaries) {
        if (selectedKey != null && selectedKey != slots.primary && selectedKey !in slots.secondaries) {
            motion.dismiss()
            selectedKey = null
        }
        if (!hasMedia) motion.dismiss()
    }
    LaunchedEffect(motion) {
        snapshotFlow { motion.progress.value == 0f && motion.transfer.value == 0f && !motion.secondary && !motion.expanded }
            .filter { it }.collect { selectedKey = null }
    }
    val significantUpdates = remember { com.island.dynamicisland.live.SignificantLiveUpdateTracker() }
    var automaticExpansionRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var automaticExpansionSequence by remember { mutableStateOf(0) }
    val latestAutomaticSlots by androidx.compose.runtime.rememberUpdatedState(
        IslandActivities.select(visibleActivities.map { it.key },
            !sourceAppOpen && np != null && (!musicHidden || np.isPlaying)))
    LaunchedEffect(hiddenActivityKeys) {
        if (selectedKey in hiddenActivityKeys ||
            (selectedKey == null && currentActivities.firstOrNull()?.key in hiddenActivityKeys)) {
            motion.dismiss()
            selectedKey = null
        }
    }
    LaunchedEffect(currentActivities, hiddenActivityKeys) {
        // A removed notification must keep its content and slot until the expanded
        // card has collapsed. Publishing first swaps the card underneath the exit,
        // and fading the whole island simultaneously makes that collapse disappear.
        val outgoingKey = selection
        if (outgoingKey != null && presentedActivities.any { it.key == outgoingKey } &&
            currentActivities.none { it.key == outgoingKey } &&
            (motion.fraction > 0f || motion.previewActive)) {
            heldActivity = null
            pendingReplacementKey = null
            automaticExpansionRequest = null
            if (!motion.dismissForReplacement()) return@LaunchedEffect
        }
        val changed = significantUpdates.changedActivity(currentActivities, hiddenActivityKeys)
        val incomingSlots = latestAutomaticSlots
        if (changed != null && (changed == incomingSlots.primary || changed in incomingSlots.secondaries)) {
            if (motion.fraction < 1f) {
                heldActivity = heldActivity?.takeIf { it.key == changed }
                    ?: presentedActivities.firstOrNull { it.key == changed }
            }
            pendingReplacementKey = changed
            automaticExpansionRequest = changed to ++automaticExpansionSequence
        }
        val held = heldActivity
        publishActivities(currentActivities.map { update ->
            held?.takeIf { it.key == update.key } ?: update
        }, if (held == null) pendingReplacementKey else null)
        if (held == null) pendingReplacementKey = null
    }
    LaunchedEffect(automaticExpansionRequest, hiddenActivityKeys) {
        val changed = automaticExpansionRequest?.first ?: return@LaunchedEffect
        if (changed in hiddenActivityKeys) return@LaunchedEffect
        val outgoing = selection
        if (outgoing != changed && (motion.fraction > 0f || motion.previewActive)) {
            if (!motion.dismissForReplacement()) {
                automaticExpansionRequest = null
                return@LaunchedEffect
            }
        }
        // An arrival first reflows through the camera; expand from the settled layout.
        snapshotFlow { latestHoldingSlots }.first { !it }
        // The source may disappear or change slots while the outgoing card collapses.
        val incomingSlots = latestAutomaticSlots
        if (changed == incomingSlots.primary || changed in incomingSlots.secondaries) {
            selectedKey = changed
            interaction++
            motion.expandAutomatically(changed != incomingSlots.primary, incomingSlots.secondaries.size == 2)
        }
        automaticExpansionRequest = null
    }
    LaunchedEffect(heldActivity, currentActivities, hiddenActivityKeys) {
        if (heldActivity != null && currentActivities.any { it.key == heldActivity?.key }) {
            // Wait for actual completion, including the companion merge. Dismissal
            // releases the hold too, so an interrupted expansion cannot freeze it.
            snapshotFlow {
                !motion.expanded || (motion.fraction == 1f && !motion.progress.isRunning &&
                    !motion.transfer.isRunning)
            }.first { it }
            heldActivity = null
            publishActivities(currentActivities,
                pendingReplacementKey.takeIf { motion.expanded })
            pendingReplacementKey = null
        }
    }
    LaunchedEffect(expanded, motion.automaticallyExpanded, live?.key, interaction, activityTouchActive) {
        // User-opened controls stay open until explicitly dismissed.
        if (expanded && motion.automaticallyExpanded && live != null && !activityTouchActive) {
            delay(5_000L)
            motion.dismiss()
        }
    }
    LaunchedEffect(dismissRequest) { if (dismissRequest > 0) motion.dismiss() }

    if (!visible && visibility == 0f) {
        SideEffect {
            onBoundsChanged(0, 0, 0, 0, false)
            onSecondaryBoundsChanged(emptyList())
            shownSlots = presentedSlots
        }
        if (reflowActive) LaunchedEffect(Unit) { reflow.reset() }
        return
    }

    val scoresOnly = slots.secondaries.size == 2
    val collapsedH = tuning.collapsedHeightDp.dp
    val groupedPrimary = primaryLive != null && slots.secondaries.size == 1
    val indicatorWidth = compactIndicatorWidth(live, collapsedH, scoresOnly,
        grouped = groupedPrimary && !motion.secondary, secondary = motion.secondary)
    val primaryCameraRadius = (cameraGuide?.horizontalRadiusDp ?: 8f) +
        if (primaryLive?.sportsScore != null) 3f else 0f
    val collapsedW = if (cameraGuide != null) {
        CompactIslandGeometry.width(
            height = collapsedH.value,
            cameraRadius = primaryCameraRadius,
            cameraOffset = cameraGuide!!.centerXFromScreenCenterDp - tuning.centerXOffsetDp,
            indicatorWidth = (if (primaryLive == null) collapsedH * (CompactVisualizerWidthFraction + CompactVisualizerTrailingInset) else compactIndicatorWidth(primaryLive, collapsedH, scoresOnly, grouped = groupedPrimary)).value,
            grouped = groupedPrimary,
            leadingWidth = compactLeadingWidth(primaryLive, collapsedH, scoresOnly, grouped = groupedPrimary).value,
        ).dp + if (scoresOnly && primaryLive?.sportsScore?.let { it.scoreOne.isNotBlank() && it.scoreTwo.isNotBlank() } == true) collapsedH * .24f else 0.dp
    } else tuning.collapsedWidthDp.dp
    val displayWidth = screenWidthDp.takeIf { it > 0f }
        ?: LocalConfiguration.current.screenWidthDp.toFloat()
    val expandedInset = 6.dp
    val expandedTop = if (motion.musicPeek) tuning.topOffsetDp.dp else expandedInset
    val expandedW = (displayWidth - 2f * expandedInset.value).coerceAtLeast(1f)
        .let { if (motion.musicPeek) minOf(it, maxOf(210f, collapsedW.value)) else it }.dp
    val primaryCenter = if (groupedPrimary && cameraGuide != null)
        CompactIslandGeometry.centerFromCameraLeft(collapsedW.value, primaryCameraRadius,
            cameraGuide!!.centerXFromScreenCenterDp - tuning.centerXOffsetDp)
    else 0f
    val compactLeftDp = (displayWidth - collapsedW.value) / 2f + tuning.centerXOffsetDp + primaryCenter
    val secondaryActivity = activities.firstOrNull { it.key == slots.secondaries.singleOrNull() }
    val secondaryStatusWidth = compactIndicatorWidth(secondaryActivity, collapsedH, secondary = true)
    val secondaryIndicatorWidth = if (slots.secondaries.singleOrNull() == IslandActivities.MUSIC)
        collapsedH.value * (CompactVisualizerWidthFraction + CompactVisualizerTrailingInset) else secondaryStatusWidth.value
    val secondaryLeadingWidth = compactLeadingWidth(secondaryActivity, collapsedH, secondary = true).value
    val secondaryWidth = if (slots.secondaries.size == 1)
        compactActivityPillWidth(collapsedH.value, secondaryIndicatorWidth, secondaryLeadingWidth) else collapsedH.value
    val scoreBubbleWidths = slots.secondaries.mapNotNull { key ->
        val activity = activities.firstOrNull { it.key == key }
        val score = activity?.sportsScore
        if (scoresOnly && score != null && score.scoreOne.isNotBlank() && score.scoreTwo.isNotBlank()) {
            key to compactSportsScoresWidth(collapsedH.value,
                sportsScoreLeadingWidth(score, collapsedH, true).value,
                sportsScoreTrailingWidth(score, collapsedH, true).value)
        } else null
    }.toMap()
    val bubblePositions = slots.secondaries.mapIndexed { index, key ->
        activityBubblePosition(index, compactLeftDp, collapsedW.value, scoreBubbleWidths[key] ?: secondaryWidth)
    }
    val destinationPlacements = activityPlacements(slots, collapsedW.value, collapsedH.value,
        primaryCenter, secondaryIndicatorWidth, secondaryLeadingWidth, scoreBubbleWidths)
    val placementMotion = remember { ActivityPlacementMotion(destinationPlacements,
        (cameraGuide?.horizontalRadiusDp ?: 8f) * 2f, trace = { message ->
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                android.util.Log.d("IslandSwitch", message)
        }, reflow = reflow) }
    placementMotion.cameraCenter = (cameraGuide?.centerXFromScreenCenterDp ?: tuning.centerXOffsetDp) - tuning.centerXOffsetDp
    LaunchedEffect(destinationPlacements) {
        // Inside the lens nothing is visible, so the new layout is adopted outright.
        if (reflow.hidden) placementMotion.snap(destinationPlacements)
        else placementMotion.retarget(destinationPlacements)
    }
    // Keyed on holding rather than the request, which clears the moment the slots swap.
    LaunchedEffect(presentedSlots, holdingSlots, musicPeekStaged) {
        // A peek sequences the camera itself; its end restarts this with the full set.
        if (!holdingSlots || musicPeekStaged) return@LaunchedEffect
        if (!reflow.active) snapshotFlow { compact }.first { it }
        val target = presentedSlots
        if (target.keys != shownSlots.keys) {
            // Continues from the current absorption when an emergence is interrupted.
            reflow.collapse(shownSlots.secondaries.toSet())
            shownSlots = target
            snapshotFlow { placementMotion.destinationKeys }.first { it == target.keys }
        } else shownSlots = target
        reflow.emerge(target.secondaries.toSet())
    }
    val primaryDestination = ActivityPlacement(primaryCenter, collapsedW.value, 1f, 1f)
    // The card belongs to the pressed activity, including its original side position.
    fun expansionPlacement(): ActivityPlacement {
        val originKey = if (motion.secondary) selection else slots.primary
        return placementMotion.frame(originKey, destinationPlacements[originKey] ?: primaryDestination)
    }
    val lensWidth = (cameraGuide?.horizontalRadiusDp ?: 8f) * 2f
    val cameraCenter = (cameraGuide?.centerXFromScreenCenterDp ?: tuning.centerXOffsetDp) - tuning.centerXOffsetDp
    fun mergeTarget(ownsPromotion: Boolean): ActivityPlacement =
        if (motion.returningFromCamera && !ownsPromotion)
            ActivityPlacement(cameraCenter, lensWidth, 1f, 0f)
        else expansionPlacement()
    fun mergeLeft(ownsPromotion: Boolean): Float {
        val target = mergeTarget(ownsPromotion)
        return displayWidth / 2f + tuning.centerXOffsetDp + target.center - target.width / 2f
    }
    fun primaryCompanionPlacement(): ActivityPlacement {
        val frame = placementMotion.frame(slots.primary, primaryDestination)
        return if (motion.returningFromCamera)
            returningActivityPlacement(frame, cameraCenter, lensWidth, motion.transfer.value)
        else mergingActivityPlacement(frame, expansionPlacement(),
            if (motion.secondary) motion.transfer.value else 0f)
    }


    val contentTop = if (motion.musicPeek) 4.dp else 12.dp
    val textHeight = rememberMusicTextHeight(np)
    val musicHeaderHeight = if (motion.musicPeek) collapsedH else MusicHeaderGeometry.height(
        textHeight.value, contentTop.value,
        cameraGuide?.bottomDp?.minus(expandedInset.value) ?: tuning.collapsedHeightDp,
    ).dp
    val headerHeight = if (live != null) maxOf(56.dp, ((cameraGuide?.bottomDp ?: 0f) - expandedInset.value).dp) else musicHeaderHeight
    // Parallel inset curves share the screen's corner center. Respect manual calibration.
    val followsDeviceCorners = tuning.useDeviceCorners && deviceCorner != null
    val expandedCorner = if (followsDeviceCorners)
        (deviceCorner!! - expandedInset.value).coerceAtLeast(0f).dp
    else tuning.expandedCornerDp.dp
    // The display reports circular corners; smoothing would pull the card's curve off them.
    val cornerSmoothing = if (followsDeviceCorners) 0f else Squircle.SMOOTHING
    // Measure the expanded content directly so font metrics cannot leave spare card height.
    val density = LocalDensity.current
    var expandedH by remember { mutableStateOf(180.dp) }
    var deferMusicResize by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded) {
            snapshotFlow { motion.fraction == 0f }.first { it }
            deferMusicResize = false
        }
    }
    val compactDragDistance by rememberUpdatedState(
        with(density) { (expandedH - collapsedH).toPx() }.coerceAtLeast(1f))
    val cameraX = displayWidth / 2f + (cameraGuide?.centerXFromScreenCenterDp ?: tuning.centerXOffsetDp)
    val cameraY = cameraGuide?.centerYDp ?: (tuning.topOffsetDp + collapsedH.value / 2f)
    val contentReveal = ((visibility - .15f) / .85f).coerceIn(0f, 1f)
    val trackChanges = remember { MusicTrackChangeTracker() }
    LaunchedEffect(nowPlaying?.trackIdentity) {
        val changed = trackChanges.changed(nowPlaying)
        if (changed && nowPlaying?.isPlaying == true && !sourceAppOpen &&
            (slots.primary == IslandActivities.MUSIC || IslandActivities.MUSIC in slots.secondaries) &&
            (!motion.previewActive || motion.musicPeek) && !activityTouchActive) {
            selectedKey = IslandActivities.MUSIC
            // A new song during an open peek just restarts it with the new label.
            if (musicPeekStaged) {
                if (motion.musicPeek && motion.expanded)
                    motion.peekMusic(fromSecondary = false, mergeCompanions = false, mergePair = false)
            } else musicPeekRequest++
        }
    }
    LaunchedEffect(musicPeekRequest) {
        if (musicPeekRequest == 0) return@LaunchedEffect
        val musicOnly = IslandActivities(IslandActivities.MUSIC, emptyList())
        musicPeekStaged = true
        try {
            // 1. Every activity is drawn into the camera.
            reflow.collapse(shownSlots.secondaries.toSet())
            peekLens.snapTo(0f)
            shownSlots = musicOnly
            snapshotFlow { placementMotion.destinationKeys }.first { it == musicOnly.keys }
            reflow.reset()
            // 2. The peek grows out of the camera.
            motion.peekMusic(fromSecondary = false, mergeCompanions = false, mergePair = false)
            peekLens.animateTo(1f, ActivityReflowSpecs.emergence)
            // 3. On close it shrinks back into the camera, together with the card's own collapse.
            snapshotFlow { !motion.expanded }.first { it }
            // Stop as soon as nothing is visible instead of waiting out the spring's tail.
            try {
                peekLens.animateTo(0f, ActivityReflowSpecs.collapse) {
                    if (value <= PEEK_LENS_HIDDEN) throw PeekLensHidden()
                }
            } catch (_: PeekLensHidden) {
                currentCoroutineContext().ensureActive()
            }
            // Hand the hidden island to the reflow, so everything emerges with its entrance.
            // The card's own collapse is invisible now, so finish it at once.
            reflow.hide()
            motion.snapClosed()
            peekLens.snapTo(1f)
            snapshotFlow { compact }.first { it }
        } finally {
            withContext(NonCancellable) { peekLens.snapTo(1f) }
            // Resumes the slot reflow: the full set emerges from the camera.
            musicPeekStaged = false
        }
    }
    val peekLabel = musicPeekLabel(nowPlaying)
    val peekTextWidth = androidx.compose.ui.text.rememberTextMeasurer().measure(
        peekLabel, MusicPeekTextStyle, softWrap = false, maxLines = 1).size.width
    val peekDuration = musicPeekDuration(with(density) { peekTextWidth.toDp().value }, expandedW.value - 2f * MusicPeekHorizontalPadding.value)
    LaunchedEffect(nowPlaying?.trackIdentity, peekLabel, peekDuration, motion.musicPeek, expanded, interaction, activityTouchActive) {
        if (motion.musicPeek && expanded && !activityTouchActive) {
            delay(peekDuration)
            motion.dismiss()
        }
    }


    androidx.compose.ui.layout.Layout(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            // A peek scales the island into and out of the lens the same way.
            val lens = peekLens.value
            alpha = visibility * lens.coerceIn(0f, 1f)
            // The entire silhouette grows from the physical lens, including its shadow.
            transformOrigin = TransformOrigin(
                with(density) { cameraX.dp.toPx() } / size.width,
                with(density) { cameraY.dp.toPx() } / size.height)
            val lensDiameter = (cameraGuide?.horizontalRadiusDp ?: 8f) * 2f
            val grown = visibility * lens
            scaleX = ((lensDiameter / collapsedW.value).coerceIn(.05f, 1f) * (1f - grown) + grown)
            scaleY = .55f + .45f * grown
        },
        content = {
            // Only the shadow is blurred. It lives outside the card's clipping layer and
            // on a stable surface, so its falloff isn't cropped by the touch window.
            // A wider, denser halo separates the black card from busy backgrounds.
            Canvas(Modifier.blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)) {
                val p = motion.fraction.coerceIn(0f, 1f)
                val w = lerp(expansionPlacement().width.dp, expandedW, IslandGrowth.widthFraction(p)).toPx()
                val h = lerp(collapsedH, expandedH, IslandGrowth.heightFraction(p)).toPx()
                val x = size.width / 2f + (tuning.centerXOffsetDp + expansionPlacement().center).dp.toPx() * (1f - p) - w / 2f
                val corner = lerp(collapsedH / 2, expandedCorner, p).toPx()
                drawSquircle(
                    Color.Black.copy(alpha = (0.26f + 0.64f * p) * expansionPlacement().alpha),
                    topLeft = Offset(x - 6.dp.toPx() * p,
                        lerp(tuning.topOffsetDp.dp, expandedTop, p).toPx() - 6.dp.toPx() * p),
                    size = Size(w + 12.dp.toPx() * p, h + 12.dp.toPx() * p),
                    radius = corner,
                    smoothing = cornerSmoothing,
                )
            }
            androidx.compose.ui.layout.Layout(
                modifier = Modifier
                    .graphicsLayer {
                        // Read motion in rendering, not composition. Do not ease a spring's output:
                        // doing so changes its velocity and creates a hitch at the endpoints.
                        val progress = motion.fraction.coerceIn(0f, 1f)
                        alpha = expansionPlacement().alpha
                        val bounce = if (touchedKey == selection)
                            motion.pressScale.value else 1f
                        scaleX = bounce
                        scaleY = bounce
                        val corner = lerp(collapsedH / 2, expandedCorner, progress)
                        shape = SquircleShape(corner, cornerSmoothing)
                        // islandEdge clips the content, leaving its outer edge exposed.
                        clip = false
                    }
                    .islandEdge(if (selection == IslandActivities.MUSIC) musicEdge else neutralEdge,
                        expansion = { motion.fraction },
                        // Compact music keeps a dimmer outline so it stays close to the camera's black.
                        collapsedAlpha = if (selection == IslandActivities.MUSIC) .45f else 1f,
                        smoothing = cornerSmoothing) {
                        with(density) {
                            lerp(collapsedH / 2, expandedCorner, motion.fraction.coerceIn(0f, 1f)).toPx()
                        }
                    }
                    .background(Color.Black)
                    // Observe child controls too, without consuming their taps or drags.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            activityTouchActive = true
                            interaction++
                            try {
                                do {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                activityTouchActive = false
                                interaction++
                            }
                        }
                    }
                    .pointerInput(motion) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!motion.expanded) return@awaitEachGesture
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var distance = compactDragDistance
                            var started = false
                            var finished = false
                            var collapsing = false
                            try {
                                val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                    if (overSlop < 0f && motion.beginCompactDrag()) {
                                        started = true
                                        deferMusicResize = true
                                        distance = compactDragDistance
                                        change.consume()
                                        motion.dragCompact(overSlop, distance)
                                    }
                                }
                                if (drag != null && started) {
                                    val completed = verticalDrag(drag.id) { change ->
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        motion.dragCompact(change.position.y - change.previousPosition.y, distance)
                                        change.consume()
                                    }
                                    collapsing = motion.finishCompactDrag(tracker.calculateVelocity().y, distance, !completed)
                                    if (collapsing) {
                                        haptics.collapse()
                                    }
                                    finished = true
                                }
                            } finally {
                                // Pointer-input disposal/cancellation must never leave dragProgress
                                // overriding the spring at an intermediate expansion fraction.
                                if (started && !finished) {
                                    motion.finishCompactDrag(0f, distance, cancelled = true)
                                }
                                if (!collapsing) deferMusicResize = false
                            }
                        }
                    }
                    .pointerInput(hasMedia, live?.key ?: np?.packageName, dismissRequest) {
                        detectTapGestures(
                            onPress = {
                                interaction++
                                touchedKey = gestureSelection
                                if (!motion.expanded && motion.fraction == 0f) selectedKey = gestureSlots.primary
                                motion.press(gestureHasMedia, viewConfiguration.longPressTimeoutMillis,
                                    mergeCompanions = gestureSlots.secondaries.size == 2,
                                    mergePair = gestureSlots.secondaries.size == 1) { haptics.expand() }
                                var released = false
                                try {
                                    released = tryAwaitRelease()
                                } finally {
                                    if (!released) motion.cancelPress()
                                }
                            },
                            onTap = {
                                when (motion.releaseTap()) {
                                    IslandMotion.TapResult.COLLAPSED -> haptics.collapse()
                                    IslandMotion.TapResult.OPEN_APP -> {
                                        haptics.tap()
                                        val tapped = gestureLive
                                        if (tapped != null) {
                                            if (openLiveActivity(context, tapped)) anticipateForeground(tapped.packageName)
                                        } else if (openSourceApp(context, gestureNp?.packageName))
                                            anticipateForeground(gestureNp?.packageName)
                                    }
                                    else -> Unit
                                }
                            },
                            onLongPress = {
                                if (motion.recognizeLongPress()) haptics.collapse()
                            },
                        )
                    },
                content = {
                    Column(Modifier.onSizeChanged { expandedH = with(density) { it.height.toDp() } }) {
                        if (live != null) ExpandedLiveUpdate(live, { motion.fraction }, expanded, headerHeight, contentTop, expandedCorner, motion.previewActive,
                            onOpenSource = {
                                if (openLiveActivity(context, live)) anticipateForeground(live.packageName)
                                motion.dismiss()
                            }, compactHeight = collapsedH,
                            replacementKeys = replacementKeys.filterKeys { it.first == live.key }
                                .mapKeys { it.key.second },
                            cameraSafeTop = cameraGuide?.let {
                                (it.bottomDp - expandedTop.value + 3f).coerceAtLeast(0f).dp
                            } ?: 32.dp, cornerSmoothing = cornerSmoothing)
                        else if (motion.musicPeek) MusicPeek(np, musicHeaderHeight, contentTop,
                            np?.let { Color(it.staticArtworkAccentColor).brightAccent() } ?: Color(0xFF9A9AA1)) { motion.fraction }
                        else Box {
                            ExpandedMusic(np, appear = { motion.fraction }, interactive = expanded, compactHeight = collapsedH,
                                deferResize = deferMusicResize,
                                onOpenSource = {
                                    if (openSourceApp(context, np?.packageName)) anticipateForeground(np?.packageName)
                                    motion.dismiss()
                                },
                                topPadding = contentTop, sharedElements = true, headerHeight = musicHeaderHeight,
                                artworkCorner = 8.dp)
                        }
                    }
                    val isSports = live?.sportsScore != null
                    // Agent counts split around the camera (or fill a logo-less bubble) instead of the logo.
                    val agentParts = agentCompactParts(live?.agents, groupedPrimary && !motion.secondary, motion.secondary)
                    Box(Modifier.graphicsLayer {
                        alpha = if (isSports) contentReveal
                            else if (agentParts != null) contentReveal * (1f - motion.fraction.coerceIn(0f, 1f)) else 0f
                    }) {
                        if (agentParts != null && agentParts.leading.isNotEmpty()) {
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().padding(start = collapsedH * .2f),
                                verticalAlignment = Alignment.CenterVertically) {
                                AgentSegmentsRow(agentParts.leading, Color(live!!.accentColor), indicatorStyle(live),
                                    motion.fraction < 1f, Modifier, trailingPad = false, showCounts = agentParts.showCounts)
                            }
                        }
                        if (isSports) SharedSportsContent(live!!.sportsScore!!, collapsedH,
                            grouped = !motion.secondary && groupedPrimary,
                            celebration = sportsPresentation.changes[live.key],
                            scoresOnly = scoresOnly,
                            cameraSplit = !motion.secondary,
                            expansion = { motion.fraction },
                            grouping = { if (motion.secondary) 0f else expansionPlacement().grouping })
                    }
                    // These are the ONLY artwork and visualizer instances. Their layers stay
                    // opaque and alive as they move/resize; reversing keeps the same bar phases.
                    // Parallel artwork/card curves: inner radius = card radius - edge inset.
                    // Normalize for both artwork scaling layers during expansion.
                    IslandArtwork(np, live, size = 54.dp, corner = 0.dp,
                        expandedArtwork = motion.previewActive && !motion.musicPeek,
                        artworkExpansion = { motion.fraction },
                        liveIconInset = { .12f * motion.fraction.coerceIn(0f, 1f) },
                        insetFraction = { if (live == null && !motion.musicPeek) motion.fraction.coerceIn(0f, 1f) * (10f / musicHeaderHeight.value) else 0f },
                        innerCorner = {
                            val p = motion.fraction.coerceIn(0f, 1f)
                            val artSize = lerp(collapsedH * CompactArtworkFraction, headerHeight, p)
                            val inset = if (motion.musicPeek) 0f else p * (10f / musicHeaderHeight.value)
                            val scale = (artSize / 54.dp) * (1f - 2f * inset)
                            val radius = (expandedCorner - contentTop - 10.dp).coerceAtLeast(0.dp)
                            // Compact art keeps its own rounding (in 54dp layer units), so it stays
                            // rounded when the paused playback scale shrinks it inside the outer clip.
                            if (live == null) lerp(54.dp * CompactArtworkCornerFraction, radius / scale, p) else 0.dp
                        },
                        modifier = Modifier.then(if (live != null && expanded) Modifier.clickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = "Open ${live.appName}",
                            onClick = {
                                if (openLiveActivity(context, live)) anticipateForeground(live.packageName)
                                motion.dismiss()
                            },
                        ) else Modifier).graphicsLayer {
                            val p = motion.fraction.coerceIn(0f, 1f)
                            val detail = expansionPlacement().detail
                            val artReveal = if (motion.secondary && live == null) lerpFloat(detail, 1f, p) else 1f
                            val baseAlpha = contentReveal * artReveal
                            // Without a compact logo, the icon only appears as the header grows in.
                            val logoAlpha = if (agentParts != null) p else 1f
                            alpha = if (isSports) 0f else if (live?.isCall == true) baseAlpha * (1f - p) else baseAlpha * logoAlpha
                            // Size and corner geometry are applied once, at placement.

                        },
                    )
                    val accent = np?.let { Color(it.staticArtworkAccentColor).brightAccent() } ?: Color(0xFF34C759)
                    IslandIndicator(np, live, accent,
                        active = motion.fraction < 1f || live?.screenCapture != null || live?.transfer != null,
                        grouped = groupedPrimary && !motion.secondary, secondary = motion.secondary,
                        modifier = Modifier.graphicsLayer {
                            val p = motion.fraction.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            val baseAlpha = (if (live != null && live.screenCapture == null && live.transfer == null) 1f - p else 1f) *
                                (if (motion.secondary && live != null) lerpFloat(expansionPlacement().detail, 1f, p) else 1f) * contentReveal
                            alpha = if (isSports) 0f else if (live?.isCall == true) baseAlpha * (1f - p) else baseAlpha

                        },
                    )
                },
            ) { measurables, _ ->
                val full = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixedWidth(
                    expandedW.roundToPx(),
                ))
                val art = measurables[2].measure(androidx.compose.ui.unit.Constraints.fixed(
                    54.dp.roundToPx(), 54.dp.roundToPx(),
                ))
                val equalizer = measurables[3].measure(androidx.compose.ui.unit.Constraints.fixed(
                    indicatorWidth.roundToPx(), 20.dp.roundToPx(),
                ))
                val p = motion.fraction.coerceIn(0f, 1f)
                val width = lerp(expansionPlacement().width.dp, expandedW, IslandGrowth.widthFraction(p)).roundToPx().coerceAtLeast(1)
                val height = lerp(collapsedH, with(density) { full.height.toDp() }, IslandGrowth.heightFraction(p)).roundToPx().coerceAtLeast(1)
                val sports = live?.sportsScore != null
                val primaryCompact = measurables[1].measure(androidx.compose.ui.unit.Constraints.fixed(
                    if (sports) width else expansionPlacement().width.dp.roundToPx(),
                    if (sports) height else collapsedH.roundToPx()))
                layout(width, height) {
                    val cornerPx = lerp(collapsedH / 2, expandedCorner, p).toPx()
                    val safeInset = IslandContentBounds.inset(width.toFloat(), height.toFloat(), cornerPx)
                    val maxRowHeight = if (live == null) maxOf(headerHeight.toPx(), 78.dp.toPx())
                        else maxOf(headerHeight.toPx(), full.height - headerHeight.toPx() - 24.dp.toPx())
                    val contentEnvelopeHeight = ExpandedContentMotion.envelopeHeight(p,
                        with(density) { full.height.toDp().value }, with(density) { maxRowHeight.toDp().value }, collapsedH.value).dp.toPx()
                    val contentScale = IslandContentBounds.scale(width.toFloat(), height.toFloat(), cornerPx,
                        full.width.toFloat(), contentEnvelopeHeight,
                        (12f - 3f * ExpandedContentMotion.blurRadiusDp(p)).dp.toPx())
                    full.placeWithLayer(((width - full.width * contentScale) / 2f).roundToInt(),
                        ((height - contentEnvelopeHeight * contentScale) / 2f).roundToInt()) {
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = reflow.contentAlpha(slots.primary)
                        // Placement already centers the scaled envelope. A center pivot would
                        // add a second (unscaledSize - scaledSize) / 2 offset down and right.
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    // Compact content already has camera/corner clearance built into its layout.
                    // Fitting its whole padded rectangle to the rounded interior shrinks it again
                    // while the secondary returns outward, then jumps back at the handoff.
                    // During the entrance overshoot, content swells about the pill's center with
                    // the silhouette instead of sitting still inside a stretching outline.
                    val overshoot = placementMotion.overshootScale(slots.primary)
                    fun androidx.compose.ui.graphics.GraphicsLayerScope.swell(left: Int, top: Int) {
                        if (overshoot == 1f) return
                        translationX = (left - width / 2f) * (overshoot - 1f)
                        translationY = (top - height / 2f) * (overshoot - 1f)
                        scaleX *= overshoot
                        scaleY *= overshoot
                    }
                    primaryCompact.placeWithLayer((width - primaryCompact.width) / 2,
                        (height - primaryCompact.height) / 2) {
                        // Fade rather than let the pill's edge cut through it on a reflow.
                        alpha = reflow.contentAlpha(slots.primary)
                        scaleX = overshoot
                        scaleY = overshoot
                    }
                    val artBounds = MusicElementMorph.art(collapsedH.value, contentTop.value, p, headerHeight.value, if (live != null) (if (live.isNavigation) 48f else 32f) else headerHeight.value)
                    val barsBounds = MusicElementMorph.visualizer(collapsedW.value,
                        collapsedH.value, expandedW.value, contentTop.value, p, headerHeight.value)
                    val arrivalPlacement = expansionPlacement()
                    val cameraLocalX = cameraX - (displayWidth / 2f + tuning.centerXOffsetDp +
                        arrivalPlacement.center - arrivalPlacement.width / 2f)
                    val cameraLocalY = cameraY - tuning.topOffsetDp
                    val artSize = lerp(collapsedH * (if (live == null) CompactArtworkFraction else compactLiveIconFraction), if (live != null) (if (live.isNavigation) 48.dp else 32.dp) else headerHeight, p)
                    val arrivalReveal = contentReveal * expansionPlacement().alpha
                    val reveal = 1f - (1f - arrivalReveal) * (1f - p)
                    val musicArtSize = if (motion.musicPeek)
                        MusicElementMorph.sharedSize(collapsedH.value * CompactArtworkFraction, 30f, 1f, p).dp
                    else MusicElementMorph.artworkSlotSize(collapsedH.value * CompactArtworkFraction, headerHeight.value, contentScale, p).dp
                    val capture = live?.screenCapture != null
                    val renderedArtSize = if (capture) lerp(collapsedH * compactLiveIconFraction,
                        (10.dp / .55f) * contentScale, p) else if (live == null) musicArtSize else artSize
                    val musicContentLeft = (width - full.width * contentScale) / 2f
                    val musicContentTop = (height - contentEnvelopeHeight * contentScale) / 2f
                    val musicBarsWidth = MusicElementMorph.sharedSize(collapsedH.value * CompactVisualizerWidthFraction, if (motion.musicPeek) 28f else ExpandedVisualizerWidth, if (motion.musicPeek) 1f else contentScale, p).dp
                    val musicBarsHeight = MusicElementMorph.sharedSize(collapsedH.value * CompactVisualizerHeightFraction, if (motion.musicPeek) 24f else ExpandedVisualizerHeight, if (motion.musicPeek) 1f else contentScale, p).dp
                    val artInternalInset = if (motion.musicPeek) 0f else musicArtSize.toPx() * p * (10f / musicHeaderHeight.value).coerceAtMost(.2f)
                    val musicCompactBarsTrail = (collapsedH * CompactVisualizerTrailingInset).toPx()
                    val sharedPlacement = MusicElementMorph.symmetricPlacement(
                        width.toFloat(), height.toFloat(), safeInset,
                        lerpFloat((collapsedH * .20f).toPx(), musicContentLeft + (if (motion.musicPeek) 10.dp else 22.dp).toPx() * contentScale, p),
                        lerpFloat(collapsedH.toPx() / 2f,
                            musicContentTop + (contentTop + headerHeight / 2).toPx() * contentScale, p),
                        musicArtSize.toPx(), artInternalInset, musicBarsWidth.toPx(), musicBarsHeight.toPx(),
                        artLead = (collapsedH * CompactArtworkLeadingInset).toPx() * (1f - p),
                        barsTrail = musicCompactBarsTrail * (1f - p))
                    // Match the minimal circle as well as the detailed secondary pill at p=0.
                    // The selected card owns these elements throughout expansion and reversal.
                    val secondaryDetail = if (motion.secondary) expansionPlacement().detail else 1f
                    val compactArtX = lerp((collapsedH - artSize) / 2, collapsedH * (if (live == null) .2f + CompactArtworkLeadingInset else .2f), secondaryDetail)
                    val compactBarsX = lerp((collapsedH - (if (live == null) collapsedH * CompactVisualizerWidthFraction else indicatorWidth)) / 2,
                        expansionPlacement().width.dp - (if (live == null) collapsedH * (.2f + CompactVisualizerTrailingInset + CompactVisualizerWidthFraction) else collapsedH * .2f + indicatorWidth), secondaryDetail)
                    val captureCompactArtX = lerp(compactArtX,
                        expansionPlacement().width.dp - collapsedH * .2f - indicatorWidth - 6.dp - collapsedH * compactLiveIconFraction,
                        if (motion.secondary) 0f else expansionPlacement().grouping)
                    val artX = if (capture) lerpFloat(captureCompactArtX.toPx(),
                        musicContentLeft + (24.dp - ((10.dp / .55f) - 10.dp) / 2).toPx() * contentScale, p)
                        else if (live == null) lerpFloat(compactArtX.toPx(), sharedPlacement.artLeft, 1f - (1f - secondaryDetail) * (1f - p))
                        else lerp((cameraLocalX - artSize.value / 2f).dp,
                            lerp(lerp(compactArtX,
                                expansionPlacement().width.dp - collapsedH * .2f - indicatorWidth - 6.dp - collapsedH * compactLiveIconFraction,
                                if (motion.secondary) 0f else expansionPlacement().grouping),
                                artBounds.left.dp, p), reveal).toPx()
                    val artY = if (capture) lerpFloat(((collapsedH - collapsedH * compactLiveIconFraction) / 2).toPx(),
                        musicContentTop + (32.dp - (10.dp / .55f) / 2).toPx() * contentScale, p)
                        else if (live == null) sharedPlacement.artTop
                        else lerp((cameraLocalY - artSize.value / 2f).dp, (artBounds.top - collapsedH.value * (compactLiveIconFraction - .60f) / 2f * (1f - p)).dp, reveal).toPx()
                    val artLeft = IslandContentBounds.position(artX, width.toFloat(), renderedArtSize.toPx(), safeInset).roundToInt()
                    val artTop = IslandContentBounds.position(artY + MusicElementMorph.downwardArcDp(p).dp.toPx(), height.toFloat(), renderedArtSize.toPx(),
                        // Compact music art is itself rounded, so keep it truly centered at rest.
                        if (live == null) safeInset * p else safeInset).roundToInt()
                    art.placeWithLayer(artLeft, artTop) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        
                        scaleX = renderedArtSize / 54.dp
                        scaleY = scaleX
                        alpha = reflow.contentAlpha(slots.primary)
                        shape = RoundedCornerShape(lerp(collapsedH * CompactArtworkFraction * CompactArtworkCornerFraction,
                            (expandedCorner - contentTop).coerceIn(0.dp, headerHeight / 2), p) / scaleX)
                        clip = true
                        swell(artLeft, artTop)
                    }
                    val transferRingInset = 18.dp
                    val indicatorX = if (live != null)
                        lerp(compactBarsX, expandedW - transferRingInset - (if (live.transfer != null) 28.dp else indicatorWidth), p)
                        else barsBounds.left.dp - 10.dp * p
                    val indicatorY = if (live != null)
                        lerp((collapsedH - 20.dp) / 2,
                            if (live.transfer != null) transferRingInset else contentTop + (headerHeight - 20.dp) / 2, p)
                        else barsBounds.top.dp
                    val indicatorVisualWidth = if (live?.transfer != null) lerp(indicatorWidth, 28.dp, p) else if (live != null) indicatorWidth else lerp(collapsedH * CompactVisualizerWidthFraction, 24.dp, p)
                    val indicatorVisualHeight = if (live?.transfer != null) lerp(20.dp, 28.dp, p) else if (live != null) 20.dp else lerp(collapsedH * CompactVisualizerHeightFraction, 20.dp, p)
                    val renderedBarsWidth = if (capture) indicatorWidth * lerpFloat(1f, contentScale, p) else if (live == null) musicBarsWidth else indicatorVisualWidth
                    val renderedBarsHeight = if (capture) 20.dp * lerpFloat(1f, contentScale, p) else if (live == null) musicBarsHeight else indicatorVisualHeight
                    val barsX = if (capture) lerpFloat(compactBarsX.toPx(),
                        musicContentLeft + 40.dp.toPx() * contentScale, p)
                        else if (live == null && motion.secondary) {
                        val compactLeft = (displayWidth / 2f + tuning.centerXOffsetDp +
                            arrivalPlacement.center - arrivalPlacement.width / 2f).dp.toPx()
                        val cardLeft = displayWidth.dp.toPx() / 2f +
                            (tuning.centerXOffsetDp + arrivalPlacement.center).dp.toPx() * (1f - p) - width / 2f
                        val expandedMargin = (if (motion.musicPeek) 10.dp else 22.dp).toPx()
                        // The width the bars finish at, so their right edge lands on the margin.
                        val expandedBarsWidth = if (motion.musicPeek) 28.dp.toPx() else MusicElementMorph.sharedSize(
                            collapsedH.value * CompactVisualizerWidthFraction, ExpandedVisualizerWidth, contentScale, 1f).dp.toPx()
                        val expandedLeft = (displayWidth.dp.toPx() + expandedW.toPx()) / 2f -
                            expandedMargin - expandedBarsWidth
                        MusicElementMorph.travellingElementLeft(compactLeft + compactBarsX.toPx(),
                            expandedLeft, cardLeft, p)
                    } else if (live == null) sharedPlacement.barsLeft
                        else lerp((cameraLocalX - indicatorVisualWidth.value / 2f).dp, indicatorX, reveal).toPx()
                    val barsY = if (capture) lerpFloat(((collapsedH - 20.dp) / 2).toPx(),
                        musicContentTop + 22.dp.toPx() * contentScale, p)
                        else if (live == null) sharedPlacement.barsTop
                        else lerp((cameraLocalY - 10f).dp, indicatorY, reveal).toPx()
                    val barsLeft = IslandContentBounds.position(barsX, width.toFloat(), renderedBarsWidth.toPx(), safeInset).roundToInt()
                    val barsTop = IslandContentBounds.position(barsY + MusicElementMorph.downwardArcDp(p).dp.toPx(), height.toFloat(), renderedBarsHeight.toPx(), safeInset).roundToInt()
                    equalizer.placeWithLayer(barsLeft, barsTop) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        
                        scaleX = renderedBarsWidth.toPx() / equalizer.width
                        scaleY = renderedBarsHeight.toPx() / equalizer.height
                        alpha = reflow.contentAlpha(slots.primary)
                        swell(barsLeft, barsTop)
                    }
                }
            }
            slots.secondaries.forEachIndexed { index, activityKey ->
                key(activityKey) {
                val secondaryLive = activities.firstOrNull { it.key == activityKey }
                val ownsPromotion = motion.secondary && selectedKey == activityKey
                val travelsInward = ownsPromotion || motion.mergingCompanions
                val position = bubblePositions[index]
                val destination = destinationPlacements.getValue(activityKey)
                fun arrivalFrame() = placementMotion.frame(activityKey, destination)
            // The gesture owner spans the full travel path while held, so moving the visual
            // out from under a stationary finger cannot cancel the long press.
            Box(Modifier.pointerInput(activityKey, dismissRequest) {
                detectTapGestures(
                    onPress = {
                        interaction++
                        touchedKey = activityKey
                        selectedKey = activityKey
                        motion.press(true, viewConfiguration.longPressTimeoutMillis, fromSecondary = true,
                            mergeCompanions = gestureSlots.secondaries.size == 2,
                            mergePair = gestureSlots.secondaries.size == 1) { haptics.expand() }
                        var released = false
                        try { released = tryAwaitRelease() }
                        finally { if (!released) motion.cancelPress() }
                    },
                    onTap = {
                        when (motion.releaseTap()) {
                            IslandMotion.TapResult.OPEN_APP -> {
                                haptics.tap()
                                if (activityKey == IslandActivities.MUSIC) {
                                    if (openSourceApp(context, gestureNp?.packageName)) anticipateForeground(gestureNp?.packageName)
                                } else gestureActivities.firstOrNull { it.key == activityKey }?.let {
                                    if (openLiveActivity(context, it)) anticipateForeground(it.packageName)
                                }
                            }
                            IslandMotion.TapResult.COLLAPSED -> haptics.collapse()
                            else -> Unit
                        }
                    },
                    onLongPress = { if (motion.recognizeLongPress()) haptics.collapse() },
                )
            }) {
                fun transfer() = if (travelsInward) motion.transfer.value.coerceIn(0f, 1f) else 0f
                fun bubbleWidth() = lerp(arrivalFrame().width.dp, mergeTarget(ownsPromotion).width.dp, transfer())
                // Read animated geometry in measurement/placement, keeping content composed
                // through rapid interrupted merges and returns.
                Box(Modifier.layout { measurable, _ ->
                    val t = transfer()
                    val targetLeft = mergeLeft(ownsPromotion)
                    val width = bubbleWidth().roundToPx().coerceAtLeast(1)
                    val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, collapsedH.roundToPx()))
                    layout(width, child.height) {
                        val x = if (travelsInward) {
                            val distance = kotlin.math.abs(targetLeft - position.left).dp.toPx()
                            if (position.left < targetLeft) distance * t else distance * (1f - t)
                        } else 0f
                        child.place(x.roundToInt(), if (travelsInward) (position.topOffset.dp.toPx() * (1f - t)).roundToInt() else 0)
                    }
                }.graphicsLayer {
                        // Preserve the outside edge while this layer fades back in.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val bounce = if (touchedKey == activityKey) motion.pressScale.value else 1f
                        val arrivalAlpha = arrivalFrame().alpha
                        // Grow from 70% while fading in, shrink back while fading out.
                        scaleX = bounce * ActivityBubbleMotion.fadeScale(arrivalAlpha)
                        scaleY = scaleX
                        alpha = arrivalAlpha * when {
                            ownsPromotion -> 0f // The expanding card now draws this activity.
                            motion.transferringActivities -> 1f - activityHandoffFraction(transfer())
                            else -> 1f
                        }
                        shape = SquircleShape(50)
                        clip = false
                    }.islandEdge(if (activityKey == IslandActivities.MUSIC) musicEdge else neutralEdge,
                        expansion = { motion.fraction },
                        collapsedAlpha = if (activityKey == IslandActivities.MUSIC) .45f else 1f) { with(density) { (collapsedH / 2).toPx() } }
                    .background(Color.Black)) {
                    Box(Modifier.graphicsLayer {
                        alpha = reflow.contentAlpha(activityKey)
                        // Swell with the bubble's entrance overshoot, and scale with the content fade.
                        scaleX = placementMotion.overshootScale(activityKey) * ActivityBubbleMotion.fadeScale(alpha)
                        scaleY = scaleX
                    },
                        propagateMinConstraints = true) {
                        SecondaryActivityContent(np, secondaryLive, activityKey == IslandActivities.MUSIC,
                            collapsedH,
                            { val frame = arrivalFrame(); frame.detail + (1f - frame.detail) * transfer() }, scoresOnly = scoresOnly)
                    }
                }
            }
                }
            }
            Box(Modifier.layout { measurable, _ ->
                val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(
                    primaryCompanionPlacement().width.dp.roundToPx().coerceAtLeast(1), collapsedH.roundToPx()))
                layout(child.width, child.height) { child.place(0, 0) }
            }.graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = if (motion.secondary) 1f - activityHandoffFraction(motion.transfer.value) else 0f
                shape = SquircleShape(50)
                // The edge extends 1 dp outside the content silhouette.
                clip = false
            }.islandEdge(if (slots.primary == IslandActivities.MUSIC) musicEdge else neutralEdge,
                expansion = { motion.fraction },
                collapsedAlpha = if (slots.primary == IslandActivities.MUSIC) .45f else 1f) { with(density) { (collapsedH / 2).toPx() } }
                .background(Color.Black)) {
                if (motion.secondary) {
                    if (primaryLive == null) CollapsedMusic(np, collapsedH)
                    else PrimaryCompanionContent(primaryLive, collapsedH, groupedPrimary, scoresOnly) {
                        primaryCompanionPlacement().grouping
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val shadowHeight = with(density) {
            (tuning.topOffsetDp.dp + (if (compactSurface) collapsedH else expandedH) + IslandShadowMargin).roundToPx()
        }
        onSurfaceHeightChanged(shadowHeight)
        val shadow = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(
            constraints.maxWidth, shadowHeight,
        ))
        val pill = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val bubbles = slots.secondaries.mapIndexed { index, activityKey ->
            val ownsPromotion = motion.secondary && selectedKey == activityKey
                val travelsInward = ownsPromotion || motion.mergingCompanions
            val position = bubblePositions[index]
            val destination = destinationPlacements.getValue(activityKey)
            val arrivalFrame = placementMotion.frame(activityKey, destination)
            val targetLeft = mergeLeft(ownsPromotion)
            val targetWidth = mergeTarget(ownsPromotion).width.dp
            measurables[index + 2].measure(androidx.compose.ui.unit.Constraints.fixed(
                with(density) { (if (travelsInward) kotlin.math.abs(targetLeft - position.left).dp + maxOf(targetWidth, arrivalFrame.width.dp) else arrivalFrame.width.dp).roundToPx() },
                with(density) { (if (travelsInward) position.topOffset.dp + collapsedH else collapsedH).roundToPx() }))
        }
        val primaryCompanion = measurables.last().measure(androidx.compose.ui.unit.Constraints.fixed(
            with(density) { primaryCompanionPlacement().width.dp.roundToPx() }, with(density) { collapsedH.roundToPx() }))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val progress = motion.fraction.coerceIn(0f, 1f)
            val left = ((constraints.maxWidth - pill.width) / 2f +
                with(density) { (tuning.centerXOffsetDp + expansionPlacement().center).dp.toPx() } * (1f - progress)).roundToInt()
            val top = with(density) { lerp(tuning.topOffsetDp.dp, expandedTop, progress).roundToPx() }
            shadow.place(0, 0)
            // The central card stays above every unselected merging bubble.
            pill.place(left, top, zIndex = 1f)
            val companion = primaryCompanionPlacement()
            // Once a selected side card is compact, the camera/primary covers
            // its return overlap. Switch while the primary companion is invisible.
            primaryCompanion.place(with(density) {
                (displayWidth / 2f + tuning.centerXOffsetDp + companion.center - companion.width / 2f).dp.roundToPx()
            }, top, zIndex = if (motion.returningFromCamera && progress < .12f) 1.5f else 0f)
            val secondaryBounds = slots.secondaries.mapIndexed { index, activityKey ->
                val position = bubblePositions[index]
                val ownsPromotion = motion.secondary && selectedKey == activityKey
                val travelsInward = ownsPromotion || motion.mergingCompanions
                val t = if (travelsInward) motion.transfer.value.coerceIn(0f, 1f) else 0f
                val destination = destinationPlacements.getValue(activityKey)
                val arrivalFrame = placementMotion.frame(activityKey, destination)
                val targetLeft = mergeLeft(ownsPromotion)
                val targetWidth = mergeTarget(ownsPromotion).width.dp
                val arrivalLeft = displayWidth / 2f + tuning.centerXOffsetDp + arrivalFrame.center - arrivalFrame.width / 2f
                val width = with(density) { lerp(arrivalFrame.width.dp, targetWidth, t).roundToPx() }
                val movingLeft = with(density) { (arrivalLeft + (targetLeft - arrivalLeft) * t).dp.roundToPx() }
                val movingTop = top + with(density) { (position.topOffset * (1f - t)).dp.roundToPx() }
                bubbles[index].place(with(density) { (if (travelsInward) minOf(position.left, targetLeft) else arrivalLeft).dp.roundToPx() },
                    top + if (travelsInward) 0 else with(density) { position.topOffset.dp.roundToPx() },
                    // Only the selected activity may sit above the card. Pair companions
                    // return underneath it, just like companions in the three-activity layout.
                    zIndex = if (ownsPromotion) 2f else 0f)
                val visible = placementMotion.progress.value >= .98f && reflow.pressable && progress < .25f && (!motion.transferringActivities || ownsPromotion) &&
                    movingLeft >= 0 && movingLeft + width <= constraints.maxWidth && movingTop + with(density) { collapsedH.roundToPx() } <= constraints.maxHeight
                ActivityTouchBounds(activityKey, movingLeft, movingTop, if (visible) width else 0,
                    if (visible) with(density) { collapsedH.roundToPx() } else 0)
            }
            onSecondaryBoundsChanged(if (visible) secondaryBounds else emptyList())
            // App switches retarget compact activity placement. Keep the native touch
            // surface stationary across that path instead of resizing a window each frame.
            val compactTouch = if (progress == 0f && !motion.transferringActivities && !motion.expanded)
                placementMotion.touchEnvelope(slots.primary, primaryDestination) else null
            val touchLeft = compactTouch?.let { with(density) {
                (displayWidth / 2f + tuning.centerXOffsetDp + it.center - it.width / 2f).dp.roundToPx()
            } } ?: left
            val touchWidth = compactTouch?.let { with(density) { it.width.dp.roundToPx() } } ?: pill.width
            // Nothing is pressable while activities are drawn into the camera, but an entrance
            // accepts taps once it has mostly grown out, so quick repeated switches land.
            val touchable = visible && reflow.pressable
            onBoundsChanged(touchLeft, top, if (touchable) touchWidth else 0, if (touchable) pill.height else 0,
                // A music peek behaves like the compact pill: touches elsewhere reach the app below.
                visible && !motion.musicPeek && (motion.transferringActivities || motion.expanded || progress >= IslandGrowth.hapticProgress))
        }
    }
}

private fun openSourceApp(context: android.content.Context, packageName: String?): Boolean {
    val pkg = packageName ?: return false
    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

private class RetainedValue<T>(var value: T)

/** Dispatch the notification's click token, including broadcast/service PendingIntents. */
private fun openLiveActivity(context: android.content.Context, live: com.island.dynamicisland.live.LiveUpdate): Boolean {
    val intent = live.contentIntent
    if (intent != null && sendLiveActivityIntent(context, intent)) return true
    return openSourceApp(context, live.packageName.takeIf { it.isNotBlank() })
}

/** Keep interpolated grouping from invalidating the surrounding activity content. */
@Composable
private fun PrimaryCompanionContent(live: com.island.dynamicisland.live.LiveUpdate,
                                    height: androidx.compose.ui.unit.Dp,
                                    grouped: Boolean, scoresOnly: Boolean, grouping: () -> Float) {
    CompactLiveActivity(live, height, grouped, grouping = grouping(), scoresOnly = scoresOnly)
}

/** Animation ticks must not recompose the expanded card, shared artwork, and indicator. */
@Composable
private fun SharedSportsContent(score: com.island.dynamicisland.live.SportsScore,
                                height: androidx.compose.ui.unit.Dp, grouped: Boolean,
                                celebration: SportsScoreChange?, scoresOnly: Boolean, cameraSplit: Boolean,
                                expansion: () -> Float, grouping: () -> Float) {
    CompactSportsLiveActivity(score, height, grouped, expansion(), celebration, grouping(), scoresOnly, cameraSplit)
}

/** The peek counts as gone once it is this small; the remaining spring tail is invisible. */
private const val PEEK_LENS_HIDDEN = .06f
/** A launched app is treated as foreground for at least this long, whatever the windows report. */
private const val LAUNCH_ANTICIPATION_MIN_MS = 600L
/** Give up on a launch the system never confirms, e.g. a notification opening another package. */
private const val LAUNCH_ANTICIPATION_TIMEOUT_MS = 1_600L
private class PeekLensHidden : kotlinx.coroutines.CancellationException("peek inside lens")
