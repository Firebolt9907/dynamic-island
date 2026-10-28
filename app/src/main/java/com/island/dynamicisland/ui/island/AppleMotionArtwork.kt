package com.island.dynamicisland.ui.island

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/** Texture rendering follows the shared artwork's clipping, scaling, and flip layers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun AppleMotionArtwork(url: String, playing: Boolean, modifier: Modifier = Modifier, expansion: () -> Float = { 1f }) {
    val context = LocalContext.current
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(512, 512)
                .setForceLowestBitrate(true)
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .build()
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true }
        }
        player.addListener(listener)
        player.prepare()
        onDispose { player.removeListener(listener); player.release() }
    }
    SideEffect { player.playWhenReady = playing && !failed }
    // Keep the static bitmap underneath while the first decoded frame fades in.
    val opacity = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready && !failed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420), label = "motionArtworkFade",
    )
    AndroidView(
        modifier = modifier.graphicsLayer { alpha = opacity.value * expansion().coerceIn(0f, 1f) },
        factory = {
            TextureView(it).apply {
                isOpaque = false
                fun crop() {
                    val video = player.videoSize
                    if (width == 0 || height == 0 || video.width == 0 || video.height == 0) return
                    val ratio = video.width * video.pixelWidthHeightRatio / video.height
                    if (!ratio.isFinite() || ratio <= 0f) return
                    val viewRatio = width.toFloat() / height
                    val sx = if (ratio > viewRatio) ratio / viewRatio else 1f
                    val sy = if (ratio < viewRatio) viewRatio / ratio else 1f
                    // Portrait motion extends below the square cover composition. Keep its
                    // top edge aligned with the static artwork rather than cropping both ends.
                    setTransform(Matrix().apply { setScale(sx, sy, width / 2f, 0f) })
                }
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> crop() }
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) { crop() }
                })
                player.setVideoTextureView(this)
            }
        },
    )
}
