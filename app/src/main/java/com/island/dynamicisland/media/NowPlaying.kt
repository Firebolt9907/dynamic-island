package com.island.dynamicisland.media

import android.graphics.Bitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable snapshot of what is currently playing. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String,
    /** Dominant color extracted from album art, ARGB int. */
    val accentColor: Int,
    val mediaId: String? = null,
    val playbackSpeed: Float = 1f,
    val publishedAtElapsedMs: Long = 0L,
    val extraActions: List<PlaybackAction> = emptyList(),
    val album: String = "",
    val appleAlbumArt: Bitmap? = null,
    val appleMotionUrl: String? = null,
    val appleArtworkAccentColor: Int? = null,
    val albumArtist: String = "",
) {
    /** Color and bitmap use exactly the same source priority in every island mode. */
    val staticAlbumArt: Bitmap? get() = albumArt ?: appleAlbumArt
    val staticArtworkAccentColor: Int
        get() = if (albumArt != null) accentColor else appleArtworkAccentColor ?: accentColor

    internal val trackIdentity: TrackIdentity
        get() = mediaId?.takeIf { it.isNotBlank() }?.let {
            TrackIdentity(packageName, it)
        } ?: TrackIdentity(packageName, null, title, artist, durationMs)

    /** Read the monotonic timeline without polling or republishing the media session. */
    internal fun positionAt(nowElapsedMs: Long): Long = PlaybackTimeline.position(
        positionMs, publishedAtElapsedMs, nowElapsedMs, durationMs, isPlaying, playbackSpeed,
    )

    internal fun advancesAt(nowElapsedMs: Long): Boolean {
        if (!isPlaying || publishedAtElapsedMs <= 0L || durationMs <= 0L ||
            !playbackSpeed.isFinite() || playbackSpeed == 0f) return false
        val position = positionAt(nowElapsedMs)
        return if (playbackSpeed > 0f) position < durationMs else position > 0L
    }

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Actions and icons are supplied by the source player, including toggle-state icon changes. */
data class PlaybackAction(val id: String, val label: String, val icon: Bitmap?, val iconResource: Int = 0)

/** A stable session-provided id takes precedence over editable display metadata. */
internal data class TrackIdentity(
    val packageName: String,
    val mediaId: String?,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

/**
 * Actions the UI can send back to the active media session.
 * Implemented by the NotificationListener, which owns the MediaController.
 */
interface MediaCommands {
    fun playPause()
    fun next()
    fun previous()
    fun customAction(packageName: String, actionId: String) {}
    fun seekTo(fraction: Float)
}

/**
 * Single source of truth shared between the NotificationListener (producer) and the
 * overlay Compose UI (consumer). Kept as a process-wide singleton because the two live
 * in separate service instances.
 */
object MediaRepository {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    @Volatile
    var commands: MediaCommands? = null

    private val artworkScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var artworkJob: kotlinx.coroutines.Job? = null
    private var artworkKey: List<String>? = null
    private val artworkCache = object : LinkedHashMap<List<String>, Pair<Long, AppleArtwork>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<String>, Pair<Long, AppleArtwork>>?) = size > 8
    }

    fun update(np: NowPlaying?) {
        val key = np?.let { AppleMusicArtwork.cacheKey(it.title, it.artist, it.album, it.albumArtist) }
        val cached = key?.let { artworkCache[it] }?.takeIf {
            it.second.image != null || it.second.motionUrl != null ||
                android.os.SystemClock.elapsedRealtime() - it.first < 60 * 1000L
        }?.second
        val previous = _nowPlaying.value
        _nowPlaying.value = np?.copy(
            appleAlbumArt = cached?.image ?: previous?.takeIf { key == artworkKey }?.appleAlbumArt,
            appleMotionUrl = cached?.motionUrl ?: previous?.takeIf { key == artworkKey }?.appleMotionUrl,
            appleArtworkAccentColor = cached?.accentColor ?: previous?.takeIf { key == artworkKey }?.appleArtworkAccentColor,
        )
        if (key == artworkKey && artworkJob?.isActive == true) return
        artworkJob?.cancel()
        artworkKey = key
        if (np == null || key == null || cached != null) return
        artworkJob = artworkScope.launch {
            val artwork = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { AppleMusicArtwork.lookup(np.title, np.artist, np.album, np.durationMs, np.albumArtist) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { AppleArtwork(null, null) }
            }
            if (artworkKey == key) {
                artworkCache[key] = android.os.SystemClock.elapsedRealtime() to artwork
                _nowPlaying.value = _nowPlaying.value?.copy(appleAlbumArt = artwork.image, appleMotionUrl = artwork.motionUrl, appleArtworkAccentColor = artwork.accentColor)
            }
        }
    }
}
