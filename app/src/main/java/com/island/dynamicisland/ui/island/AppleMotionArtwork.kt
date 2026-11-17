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

/** Captured only on a track change, rather than copying video frames during playback. */
internal class MotionArtworkFrame {
    var capture: (() -> android.graphics.Bitmap?)? = null
        internal set

    fun snapshot(): android.graphics.Bitmap? = runCatching { capture?.invoke() }.getOrNull()
}

/** Texture rendering follows the shared artwork's clipping, scaling, and flip layers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun AppleMotionArtwork(url: String, playing: Boolean, modifier: Modifier = Modifier, expansion: () -> Float = { 1f }, frame: MotionArtworkFrame? = null) {
    val context = LocalContext.current
    val currentExpansion by rememberUpdatedState(expansion)
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var texture by remember(url) { mutableStateOf<TextureView?>(null) }
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(AppleMotionCache.factory(context)))
            // Defaults buffer up to 50 s of video in a large heap budget. A muted, looping
            // cover clip needs a few seconds; segments replay from the disk cache.
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 5_000, 500, 1_000)
                .setTargetBufferBytes(4 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build())
            .build().apply {
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
    DisposableEffect(frame, player) {
        val capture = { if (ready && !failed && currentExpansion() > 0f) texture?.bitmap else null }
        frame?.capture = capture
        onDispose {
            if (frame?.capture === capture) frame.capture = null
        }
    }
    SideEffect { player.playWhenReady = playing && !failed }
    // Keep the static bitmap underneath while the first decoded frame fades in.
    val opacity = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready && !failed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420), label = "motionArtworkFade",
    )
    // AndroidView retains its factory-created view across recompositions. A new player
    // needs a new TextureView, rather than the surface attached to the released player.
    key(player) {
        AndroidView(
            modifier = modifier.graphicsLayer { alpha = opacity.value * expansion().coerceIn(0f, 1f) },
            factory = {
                TextureView(it).apply {
                    texture = this
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
}

/** One process-wide cache: HLS playlists and segments survive player recreation. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private object AppleMotionCache {
    private var cache: androidx.media3.datasource.cache.SimpleCache? = null

    @Synchronized
    fun factory(context: android.content.Context): androidx.media3.datasource.DataSource.Factory {
        val app = context.applicationContext
        val shared = cache ?: androidx.media3.datasource.cache.SimpleCache(
            java.io.File(app.cacheDir, "apple-motion-artwork"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(app),
        ).also { cache = it }
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(shared)
            .setUpstreamDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(app))
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
