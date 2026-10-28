package com.island.dynamicisland.ui.island

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.MarqueeDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.island.dynamicisland.media.MediaRepository
import com.island.dynamicisland.media.NowPlaying

/** Small rounded album thumbnail, or a music-note fallback. */
@Composable
fun AlbumArt(np: NowPlaying?, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, insetFraction: () -> Float = { 0f }, innerCorner: (() -> androidx.compose.ui.unit.Dp)? = null, expandedArtwork: Boolean = false, artworkExpansion: () -> Float = { 1f }) {
    val reduceMotion = com.island.dynamicisland.power.LocalPowerSaveMode.current ||
        !com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
    val playbackScale by rememberArtworkPlaybackScale(np?.isPlaying != false, animate = !reduceMotion)
    val flip = remember { androidx.compose.animation.core.Animatable(1f) }
    var shownTrack by remember { androidx.compose.runtime.mutableStateOf(np) }
    var outgoingArt by remember { androidx.compose.runtime.mutableStateOf(np?.staticAlbumArt) }
    val identity = np?.trackIdentity
    LaunchedEffect(identity, reduceMotion) {
        if (reduceMotion) {
            shownTrack = np
            outgoingArt = np?.staticAlbumArt
            flip.snapTo(1f)
            return@LaunchedEffect
        }
        if (identity != shownTrack?.trackIdentity) {
            val hadTrack = shownTrack != null
            // Rapid skips restart with the face that was actually visible, not a stale track.
            outgoingArt = if (ArtworkFlipMotion.showsNewArtwork(flip.value)) shownTrack?.staticAlbumArt else outgoingArt
            shownTrack = np
            if (hadTrack && np != null) {
                flip.snapTo(0f)
                flip.animateTo(1f, tween(580, easing = androidx.compose.animation.core.CubicBezierEasing(.45f, 0f, .2f, 1f)))
            } else flip.snapTo(1f)
        }
    }
    androidx.compose.runtime.SideEffect {
        if (shownTrack?.trackIdentity == identity) shownTrack = np
    }
    Box(
        modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val scale = (1f - 2f * insetFraction().coerceIn(0f, .2f)) * playbackScale
            scaleX = scale
            scaleY = scale
            rotationY = ArtworkFlipMotion.rotationY(flip.value)
            cameraDistance = this.size.width * 8f
            shape = RoundedCornerShape(innerCorner?.invoke() ?: corner)
            clip = true
        }
            .background(Color(0xFF1C1C1F)), contentAlignment = Alignment.Center) {
        val bmp = if (ArtworkFlipMotion.showsNewArtwork(flip.value)) shownTrack?.staticAlbumArt else outgoingArt
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(size * 0.5f),
            )
        }
        val motionTrack = shownTrack.takeIf { ArtworkFlipMotion.showsNewArtwork(flip.value) }
        if (expandedArtwork && !reduceMotion && motionTrack?.appleMotionUrl != null) {
            androidx.compose.runtime.key(motionTrack.appleMotionUrl) {
                AppleMotionArtwork(motionTrack.appleMotionUrl, motionTrack.isPlaying, Modifier.fillMaxSize(), artworkExpansion)
            }
        }
        }
    }
}

/**
 * Live equalizer bars, iOS-style. Bars animate only while playing; when paused they settle to
 * a calm low level. Each bar has its own period so the motion reads as organic, not synced.
 */
@Composable
fun Equalizer(
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 4,
) {
    val count = bars.coerceIn(1, 5)
    val powerSaveMode = com.island.dynamicisland.power.LocalPowerSaveMode.current
    val levels = remember(count) { List(count) { androidx.compose.animation.core.Animatable(0.30f) } }
    androidx.compose.runtime.LaunchedEffect(playing, count, powerSaveMode) {
        animateEqualizer(levels, playing, animationsEnabled = !powerSaveMode)
    }
    Canvas(modifier) {
        val gap = size.width * 0.22f / (count - 1).coerceAtLeast(1)
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val level = levels[i].value
            val h = size.height * level
            val x = i * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/**
 * Content shown when the island is in its compact pill form. The album art and visualizer are
 * sized as fractions of [heightDp] so they grow and shrink with the pill's configured height.
 */
@Composable
fun CollapsedMusic(np: NowPlaying?, heightDp: androidx.compose.ui.unit.Dp) {
    val pad = heightDp * 0.20f
    val art = heightDp * 0.60f
    val eqHeight = heightDp * 0.42f
    val eqWidth = heightDp * 0.54f
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(np, size = art, corner = art * 0.32f)
        // Center gap sits over the camera cutout.
        Spacer(Modifier.weight(1f))
        val accent = np?.let { Color(it.staticArtworkAccentColor) }?.brightAccent() ?: Color(0xFF34C759)
        Equalizer(
            color = accent,
            playing = np?.isPlaying == true,
            modifier = Modifier
                .height(eqHeight)
                .width(eqWidth),
        )
    }
}

private val titleStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
private val artistStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)

/** Measure the actual fallback glyphs and system font scaling, once per text/style change. */
@Composable
internal fun rememberMusicTextHeight(np: NowPlaying?): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val inheritedStyle = LocalTextStyle.current
    val title = np?.title ?: "Not playing"
    val artist = np?.artist?.ifBlank { "—" } ?: "Tap a song to begin"
    return remember(title, artist, measurer, density, inheritedStyle) {
        val titleHeight = measurer.measure(title, inheritedStyle.merge(titleStyle.copy(fontSize = 16.sp, lineHeight = 20.sp)),
            maxLines = 1, softWrap = false).size.height
        val artistHeight = measurer.measure(artist, inheritedStyle.merge(artistStyle.copy(fontSize = 13.sp, lineHeight = 17.sp)),
            maxLines = 1, softWrap = false).size.height
        with(density) { (titleHeight + artistHeight).toDp() + 2.dp }
    }
}

/**
 * Content shown when the island is expanded into the full music card.
 *
 * [appear] is the 0..1 entrance progress handed down from the island's expansion. Each row reads
 * a closely staggered slice of it. Text, timeline and controls fade and settle
 * outward from the compact pill as the card grows; shared artwork and visualizer movement is owned by the enclosing island.
 */
@Composable
fun ExpandedMusic(np: NowPlaying?, appear: () -> Float = { 1f }, interactive: Boolean = true, topPadding: androidx.compose.ui.unit.Dp = 12.dp, sharedElements: Boolean = false, headerHeight: androidx.compose.ui.unit.Dp = 72.dp, artworkCorner: androidx.compose.ui.unit.Dp = 26.dp, onOpenSource: () -> Unit = {}, compactHeight: androidx.compose.ui.unit.Dp = 36.dp) {
    val textHeight = rememberMusicTextHeight(np)
    val headerCenter = topPadding.value + headerHeight.value / 2f
    val timelineCenter = topPadding.value + headerHeight.value + 15f
    val controlsCenter = topPadding.value + headerHeight.value + 65f
    // Timeline spacer + timeline + padded controls + half the padded lyric row.
    val lyricsCenter = topPadding.value + headerHeight.value + 3f + 24f + 78f + 18f
    val animateContentGrowth = interactive && !com.island.dynamicisland.power.LocalPowerSaveMode.current &&
        com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
    val commands = MediaRepository.commands
    val accent = np?.let { Color(it.staticArtworkAccentColor) }?.brightAccent() ?: Color(0xFF34C759)

    Column(
        Modifier
            .fillMaxWidth()
            // Report each intermediate height to the island's surface and touch bounds.
            // The header stays anchored while clipping gradually reveals loaded lyrics.
            .animateContentSize(animationSpec = if (animateContentGrowth)
                tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                else androidx.compose.animation.core.snap())
            .padding(start = 12.dp, end = 12.dp, top = topPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.height(headerHeight).fillMaxWidth().then(if (interactive && np != null) Modifier.clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = "Open music app",
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpenSource,
            ) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sharedElements) Spacer(Modifier.size(headerHeight))
            else AlbumArt(np, size = headerHeight, corner = artworkCorner, expandedArtwork = true, artworkExpansion = appear)
            // Shared artwork has a 10dp inner inset; keep the visible artwork/text gap at 12dp.
            Spacer(Modifier.width(if (sharedElements) 2.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                FadingMusicText(
                    text = np?.title ?: "Not playing",
                    color = Color.White,
                    style = LocalTextStyle.current.merge(titleStyle),
                    modifier = Modifier.expandedContentEntrance(appear, 0, headerCenter - textHeight.value / 4f, compactHeight.value),
                    interactive = interactive,
                )
                Spacer(Modifier.height(2.dp))
                FadingMusicText(
                    text = np?.artist?.ifBlank { "—" } ?: "Tap a song to begin",
                    color = Color(0xFFB0B0B6),
                    style = LocalTextStyle.current.merge(artistStyle),
                    modifier = Modifier.expandedContentEntrance(appear, 1, headerCenter + textHeight.value / 4f, compactHeight.value),
                    interactive = interactive,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (sharedElements) Spacer(Modifier.size(width = 34.dp, height = 20.dp))
            else Equalizer(
                color = accent,
                playing = np?.isPlaying == true,
                modifier = Modifier.size(width = 24.dp, height = 20.dp),
            )
        }

        Spacer(Modifier.height(3.dp))
        // iOS keeps the timeline neutral, leaving the album's color to the visualizer.
        val progress = rememberPlaybackProgress(np, active = appear() > 0f)
        val duration = np?.durationMs ?: 0L
        val canSeek = interactive && duration > 0L && commands != null
        var scrubFraction by remember(np?.trackIdentity, duration, canSeek) { mutableStateOf<Float?>(null) }
        val displayedProgress = scrubFraction ?: progress.value
        val scrubMotionEnabled = appear() > 0f && !com.island.dynamicisland.power.LocalPowerSaveMode.current &&
            com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
        val scrubEmphasis by rememberTimelineScrubEmphasis(scrubFraction != null, animate = scrubMotionEnabled)
        Row(Modifier.expandedContentEntrance(appear, 2, timelineCenter, compactHeight.value).fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val elapsed = (duration * displayedProgress).toLong()
            NumericTickerText(playbackTime(elapsed), enabled = appear() > 0f && scrubFraction == null, color = Color(0xFF8E8E93), fontSize = 10.sp, lineHeight = 14.sp)
            Canvas(Modifier.weight(1f).height(24.dp)
                .then(if (canSeek) Modifier.pointerInput(np?.trackIdentity, duration, canSeek) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun fraction(x: Float) = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        scrubFraction = fraction(down.position.x)
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                scrubFraction = fraction(change.position.x)
                                change.consume()
                                if (!change.pressed) {
                                    commands?.seekTo(scrubFraction!!)
                                    break
                                }
                            } while (true)
                        } finally {
                            scrubFraction = null
                        }
                    }
                }.semantics {
                    contentDescription = "Playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(displayedProgress, 0f..1f)
                    setProgress { target ->
                        if (target.isFinite()) {
                            commands?.seekTo(target.coerceIn(0f, 1f))
                            true
                        } else false
                    }
                } else Modifier)) {
                val barHeight = (6f + 3f * scrubEmphasis).dp.toPx()
                val top = (size.height - barHeight) / 2f
                drawRoundRect(androidx.compose.ui.graphics.lerp(Color(0xFF29292B), Color(0xFF515155),
                    scrubEmphasis.coerceIn(0f, 1f)), topLeft = Offset(0f, top),
                    size = Size(size.width, barHeight), cornerRadius = CornerRadius(barHeight / 2))
                drawRoundRect(androidx.compose.ui.graphics.lerp(Color(0xFFA5A5A8), Color.White,
                    scrubEmphasis.coerceIn(0f, 1f)), topLeft = Offset(0f, top),
                    size = Size(size.width * displayedProgress, barHeight), cornerRadius = CornerRadius(barHeight / 2))
            }
            NumericTickerText(if (duration > 0) "−${playbackTime(duration - elapsed)}" else "−0:00",
                enabled = appear() > 0f && scrubFraction == null,
                color = Color(0xFF8E8E93), fontSize = 10.sp, lineHeight = 14.sp)
        }

        Box(Modifier.expandedContentEntrance(appear, 3, controlsCenter, compactHeight.value).fillMaxWidth().padding(top = 16.dp, bottom = 14.dp)) {
            Row(Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ControlButton(Icons.Rounded.SkipPrevious, 48.dp, "Previous track", interactive) { commands?.previous() }
                ControlButton(if (np?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    48.dp, if (np?.isPlaying == true) "Pause" else "Play", interactive) { commands?.playPause() }
                ControlButton(Icons.Rounded.SkipNext, 48.dp, "Next track", interactive) { commands?.next() }
            }
            val settings by com.island.dynamicisland.config.MusicControlsConfig.settings.collectAsState()
            // Read the revision so saved choices update the live overlay immediately.
            val slots = remember(np?.packageName, settings.revision) {
                com.island.dynamicisland.config.MusicControlsConfig.slots(np?.packageName)
            }
            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) {
                ExtraControlSlot(slots.left, np, interactive)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)) {
                ExtraControlSlot(slots.right, np, interactive)
            }
        }
        MusicLyrics(np, active = interactive && appear() > 0f,
            previewPositionMs = scrubFraction?.let { (duration * it).toLong() },
            modifier = Modifier.expandedContentEntrance(appear, 4, lyricsCenter, compactHeight.value))
    }
}

/** Slots use the current session's actions; cached icons are only for the settings picker. */
@Composable
private fun ExtraControlSlot(id: String, np: NowPlaying?, interactive: Boolean) {
    if (id == "none") return
    val context = LocalContext.current
    val action = np?.extraActions?.firstOrNull { it.id == id }
    if (id != "cast" && action == null) return
    val label = action?.label ?: "Audio output controls"
    Box(Modifier.size(40.dp).then(if (interactive) Modifier.clickable(
        role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label) {
        if (id == "cast") {
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audio.adjustVolume(android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI)
        } else if (np != null) MediaRepository.commands?.customAction(np.packageName, id)
    } else Modifier), contentAlignment = Alignment.Center) {
        if (id == "cast") Icon(Icons.Rounded.Cast, label, Modifier.size(26.dp), tint = Color(0xFF8E8E93))
        else if (action?.icon != null) androidx.compose.foundation.Image(action.icon.asImageBitmap(), label,
            Modifier.size(24.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFB5B5BA)))
        else Text(label, color = Color(0xFFB5B5BA), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mask only overflowing text, outside the marquee so its trailing fade stays stationary. */
@Composable
private fun FadingMusicText(
    text: String, color: Color, style: TextStyle, modifier: Modifier, interactive: Boolean,
) {
    FadingMusicText(androidx.compose.ui.text.AnnotatedString(text), color, style, modifier, interactive)
}

internal data class TextGlyphLift(val from: Int, val until: Int, val liftDp: Float)

/** Annotated lyrics share the same fixed edge fade without restarting on word color changes. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FadingMusicText(
    text: androidx.compose.ui.text.AnnotatedString, color: Color, style: TextStyle, modifier: Modifier, interactive: Boolean,
    glyphLifts: List<TextGlyphLift> = emptyList(),
    scrollProgress: Float? = null,
    onOverflowChanged: ((Boolean) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val textWidth = remember(text.text, style, measurer) {
        measurer.measure(text.text, style, maxLines = 1, softWrap = false).size.width
    }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var glyphLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val timedScroll = rememberScrollState()
    LaunchedEffect(scrollProgress, timedScroll.maxValue) {
        if (scrollProgress != null && timedScroll.maxValue != Int.MAX_VALUE) {
            timedScroll.scrollTo(kotlin.math.round(timedScroll.maxValue * scrollProgress.coerceIn(0f, 1f)).toInt())
        }
    }
    LaunchedEffect(text.text, textWidth, viewportWidth) {
        onOverflowChanged?.invoke(viewportWidth > 0 && textWidth > viewportWidth)
    }
    Text(
        text = text, color = color, style = style, maxLines = 1, softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { glyphLayout = it },
        modifier = modifier.fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .graphicsLayer {
                compositingStrategy = if (textWidth > viewportWidth) CompositingStrategy.Offscreen
                    else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                if (textWidth > viewportWidth && size.width > 0f) {
                    val fade = minOf(20.dp.toPx(), size.width / 2f)
                    if (scrollProgress == null || timedScroll.value < timedScroll.maxValue) drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            startX = size.width - fade, endX = size.width,
                        ), blendMode = BlendMode.DstIn,
                    )
                    if (scrollProgress != null && timedScroll.value > 0) drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White), startX = 0f, endX = fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .then(if (scrollProgress != null) Modifier.horizontalScroll(timedScroll, enabled = false)
                else Modifier.basicMarquee(iterations = if (interactive) MarqueeDefaults.Iterations else 0))
            .drawWithContent {
                val layout = glyphLayout
                if (glyphLifts.isEmpty() || layout == null || layout.layoutInput.text.text != text.text) {
                    drawContent()
                } else {
                    // Draw-only translation keeps every other glyph on the original baseline.
                    // This is inside the marquee, so ranges travel with the scrolling text.
                    val bands = glyphLifts.mapNotNull { lift ->
                        if (lift.from < 0 || lift.until > text.length || lift.until <= lift.from) null
                        else {
                            val first = layout.getBoundingBox(lift.from)
                            val last = layout.getBoundingBox(lift.until - 1)
                            Triple(minOf(first.left, last.left).coerceAtLeast(0f),
                                maxOf(first.right, last.right).coerceAtMost(size.width), lift.liftDp.dp.toPx())
                        }
                    }.sortedBy { it.first }
                    var cursor = 0f
                    fun drawBand(left: Float, right: Float, lift: Float) {
                        if (right <= left) return
                        clipRect(left = left, right = right) {
                            drawText(layout, topLeft = Offset(0f, -lift))
                        }
                    }
                    for ((left, right, lift) in bands) {
                        drawBand(cursor, left, 0f)
                        drawBand(maxOf(cursor, left), right, lift)
                        cursor = maxOf(cursor, right)
                    }
                    drawBand(cursor, size.width, 0f)
                }
            },
    )
}

@Composable
private fun ControlButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, description: String, enabled: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val skipProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    val toggleProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    var skipJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    var displayedIcon by remember { androidx.compose.runtime.mutableStateOf(icon) }
    var outgoingIcon by remember { androidx.compose.runtime.mutableStateOf(icon) }
    val isToggle = icon == Icons.Rounded.PlayArrow || icon == Icons.Rounded.Pause
    LaunchedEffect(icon) {
        if (isToggle && displayedIcon != icon) {
            outgoingIcon = displayedIcon
            displayedIcon = icon
            toggleProgress.snapTo(0f)
            toggleProgress.animateTo(1f, androidx.compose.animation.core.spring(
                dampingRatio = .52f, stiffness = 650f))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.45f,
            stiffness = 900f,
        ),
        label = "ctrlScale",
    )
    Canvas(
        modifier = Modifier.size(width = size, height = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // Hidden/disabled controls must not take the compact pill's press gesture.
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null,
                onClickLabel = description, onClick = {
                    if (!isToggle) {
                        skipJob?.cancel()
                        skipJob = scope.launch {
                            skipProgress.snapTo(0f)
                            skipProgress.animateTo(1f, tween(360,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        }
                    }
                    onClick()
                }) else Modifier)
            .then(Modifier.semantics { contentDescription = description; role = androidx.compose.ui.semantics.Role.Button }),
    ) {
        // Filled double triangles match the iOS backward/forward symbols (no Android stop bar).
        val unit = this.size.width / 48f * 1.25f
        drawContext.transform.translate(this.size.width / 2f - 24f * unit,
            this.size.height / 2f - 24f * unit)
        fun triangle(left: Float, right: Float, top: Float, bottom: Float, reverse: Boolean = false, alpha: Float = 1f) {
            val path = Path().apply {
                moveTo((if (reverse) right else left) * unit, top * unit)
                lineTo((if (reverse) left else right) * unit, (top + bottom) / 2 * unit)
                lineTo((if (reverse) right else left) * unit, bottom * unit)
                close()
            }
            drawPath(path, Color.White, alpha = alpha)
            drawPath(path, Color.White, alpha = alpha, style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.2f * unit, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        fun toggleGlyph(glyph: ImageVector, glyphScale: Float, opacity: Float) {
            if (opacity <= 0f) return
            withTransform({
                scale(glyphScale, glyphScale, pivot = Offset(24f * unit, 24f * unit))
            }) {
                if (glyph == Icons.Rounded.Pause) {
                    for (x in listOf(16f, 27f)) drawRoundRect(Color.White,
                        topLeft = Offset(x * unit, 12 * unit), size = Size(6 * unit, 24 * unit),
                        cornerRadius = CornerRadius(1.5f * unit), alpha = opacity)
                } else triangle(15f, 37f, 9f, 39f, alpha = opacity)
            }
        }
        if (isToggle) {
            val p = toggleProgress.value
            val blend = p.coerceIn(0f, 1f)
            if (blend < 1f) toggleGlyph(outgoingIcon, 1f - .45f * blend, 1f - blend)
            toggleGlyph(icon, .55f + .45f * p, blend)
        } else {
            val previous = icon == Icons.Rounded.SkipPrevious
            PlaybackControlMotion.triangles(skipProgress.value, previous).forEach { frame ->
                if (frame.alpha > 0f && frame.scale > 0f) {
                    withTransform({
                        scale(frame.scale, frame.scale, pivot = Offset((frame.left + 7.5f) * unit, 24f * unit))
                    }) {
                        triangle(frame.left, frame.left + 15f, 15f, 33f, previous, frame.alpha)
                    }
                }
            }
        }
    }
}

/** Lighten a possibly-dark album color so it stays visible on the black island. */
internal fun Color.brightAccent(): Color {
    val max = maxOf(red, green, blue)
    if (max <= 0f) return Color(0xFFB0B0B6)
    val boost = (0.85f / max).coerceIn(1f, 3.5f)
    return Color(
        red = (red * boost).coerceAtMost(1f),
        green = (green * boost).coerceAtMost(1f),
        blue = (blue * boost).coerceAtMost(1f),
        alpha = 1f,
    )
}

private fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
