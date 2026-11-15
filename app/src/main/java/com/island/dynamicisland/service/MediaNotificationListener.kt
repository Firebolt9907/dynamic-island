package com.island.dynamicisland.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import com.island.dynamicisland.config.LiveActivityConfig
import com.island.dynamicisland.config.MusicControlsConfig
import com.island.dynamicisland.live.LiveUpdateRepository
import com.island.dynamicisland.live.readLiveUpdate
import com.island.dynamicisland.media.MediaCommands
import com.island.dynamicisland.media.MediaRepository
import com.island.dynamicisland.media.NowPlaying
import com.island.dynamicisland.media.PlaybackAction
import com.island.dynamicisland.media.PlaybackTimeline
import com.island.dynamicisland.power.InteractiveDisplayMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Listens to the system's active media sessions and mirrors the "best" one into
 * [MediaRepository]. Also exposes transport controls back to that session.
 *
 * NotificationListenerService is required because MediaSessionManager.getActiveSessions()
 * only works for a component that holds notification-listener access.
 */
class MediaNotificationListener : NotificationListenerService(), MediaCommands {

    private lateinit var sessionManager: MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private var activitySettingsJob: Job? = null

    private val displayMonitor = InteractiveDisplayMonitor(this) { active ->
        if (active) activeController?.let { publish(it) }
    }

    private var activeController: MediaController? = null
    private var pendingPlayback: NowPlaying? = null
    private val playbackTimeout = Runnable {
        pendingPlayback = null
        activeController?.let { publish(it) }
    }
    private val actionIcons = LinkedHashMap<Int, Bitmap?>()
    private var cachedArt: Bitmap? = null
    private var cachedTrimmedArt: Bitmap? = null
    private var cachedAccent = Color.rgb(24, 24, 27)
    private var controllerCallback: MediaController.Callback? = null

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            // Ignore deliveries retained by an old listener after disconnect/replacement.
            if (MediaRepository.commands === this) bindBestController(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.island.dynamicisland.media.AppleMusicArtwork.init(this)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, MediaNotificationListener::class.java)
        MediaRepository.commands = this
        LiveActivityConfig.init(this)
        LiveUpdateRepository.clear()
        activeNotifications?.forEach { onNotificationPosted(it) }
        activitySettingsJob?.cancel()
        activitySettingsJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            LiveActivityConfig.changes.collect {
                if (MediaRepository.commands === this@MediaNotificationListener) {
                    activeNotifications?.forEach { publishLiveActivity(it, remember = false) }
                }
            }
        }
        displayMonitor.start()
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            bindBestController(sessionManager.getActiveSessions(component))
        } catch (_: SecurityException) {
            // Access not yet granted; nothing to do until it is.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (MediaRepository.commands !== this) return
        publishLiveActivity(sbn, remember = true)
    }

    private fun publishLiveActivity(sbn: StatusBarNotification, remember: Boolean) {
        val update = runCatching { readLiveUpdate(this, sbn) }.getOrNull()
        if (update != null && remember) {
            LiveActivityConfig.observe(this, sbn.packageName, sbn.notification.channelId, update.appName, update.title)
        }
        LiveUpdateRepository.put(sbn.key, update?.takeIf {
            LiveActivityConfig.isEnabled(sbn.packageName, sbn.notification.channelId)
        })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (MediaRepository.commands === this) LiveUpdateRepository.put(sbn.key, null)
    }

    override fun onListenerDisconnected() {
        releaseSession()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    private fun releaseSession() {
        activitySettingsJob?.cancel()
        activitySettingsJob = null
        displayMonitor.stop()
        if (::sessionManager.isInitialized) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        detachController()
        if (MediaRepository.commands === this) {
            LiveUpdateRepository.clear()
            MediaRepository.commands = null
            MediaRepository.update(null)
        }
    }

    private fun bindBestController(controllers: List<MediaController>?) {
        val list = controllers ?: emptyList()
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (best == null) {
            detachController()
            MediaRepository.update(null)
            return
        }
        if (best.sessionToken == activeController?.sessionToken) {
            publish(best)
            return
        }
        detachController()
        activeController = best
        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (activeController === best) publish(best)
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (activeController === best) publish(best)
            }
            override fun onSessionDestroyed() {
                if (activeController === best) {
                    detachController()
                    MediaRepository.update(null)
                    if (::sessionManager.isInitialized) {
                        try {
                            val comp = ComponentName(this@MediaNotificationListener, MediaNotificationListener::class.java)
                            bindBestController(sessionManager.getActiveSessions(comp))
                        } catch (_: SecurityException) {}
                    }
                }
            }
        }
        controllerCallback = cb
        best.registerCallback(cb, handler)
        publish(best)
    }

    private fun detachController() {
        handler.removeCallbacks(playbackTimeout)
        pendingPlayback = null
        cachedArt = null
        cachedTrimmedArt = null
        actionIcons.clear()
        cachedAccent = Color.rgb(24, 24, 27)
        val controller = activeController
        val cb = controllerCallback
        controllerCallback = null
        activeController = null
        if (cb != null && controller != null) {
            controller.unregisterCallback(cb)
        }
    }

    private fun publish(controller: MediaController) {
        val meta = controller.metadata
        val state = controller.playbackState
        if (meta == null) {
            cachedArt = null
        cachedTrimmedArt = null
            cachedAccent = Color.rgb(24, 24, 27)
            MediaRepository.update(null)
            return
        }

        val rawArt = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.takeUnless { it.isRecycled }
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)?.takeUnless { it.isRecycled }
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.takeUnless { it.isRecycled }

        if (rawArt !== cachedArt) {
            cachedArt = rawArt
            cachedTrimmedArt = rawArt?.let { com.island.dynamicisland.media.ArtworkLetterbox.trim(it) }
            cachedAccent = cachedTrimmedArt?.let { com.island.dynamicisland.media.ArtworkColors.fromBitmap(it) } ?: Color.rgb(24, 24, 27)
        }
        val art = cachedTrimmedArt

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val nowElapsed = SystemClock.elapsedRealtime()
        val positionMs = PlaybackTimeline.position(
            state?.position ?: -1L,
            state?.lastPositionUpdateTime ?: 0L,
            nowElapsed,
            durationMs,
            isPlaying,
            state?.playbackSpeed ?: 0f,
        )

        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Unknown"
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        val publishedAtElapsedMs = if (state != null && state.position >= 0L && state.lastPositionUpdateTime > 0L) {
            nowElapsed
        } else 0L

        val customActions = (state?.customActions ?: emptyList()).map { action ->
            val iconRes = action.icon
            val bitmap = actionIcons.getOrPut(iconRes) {
                runCatching {
                    val pkg = controller.packageName
                    val res = packageManager.getResourcesForApplication(pkg)
                    val iconSize = (24f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    res.getDrawable(iconRes, null).mutate().toBitmap(iconSize, iconSize)
                }.getOrNull()
            }
            PlaybackAction(
                id = action.action,
                label = action.name.toString(),
                icon = bitmap,
                iconResource = action.icon,
            )
        }

        val nowPlaying = NowPlaying(
            title = title,
            artist = artist,
            albumArt = art,
            album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            albumArtist = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            packageName = controller.packageName,
            accentColor = cachedAccent,
            mediaId = meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            playbackSpeed = state?.playbackSpeed ?: 0f,
            publishedAtElapsedMs = publishedAtElapsedMs,
            extraActions = customActions,
        )

        MusicControlsConfig.observe(this, nowPlaying)
        val pending = pendingPlayback
        if (pending != null && pending.trackIdentity == nowPlaying.trackIdentity &&
            pending.isPlaying != nowPlaying.isPlaying) {
            // Metadata and stale playback callbacks must not undo immediate tap feedback.
            MediaRepository.update(nowPlaying.copy(
                isPlaying = pending.isPlaying,
                positionMs = pending.positionAt(nowElapsed),
                playbackSpeed = pending.playbackSpeed,
                publishedAtElapsedMs = pending.publishedAtElapsedMs.takeIf { it == 0L } ?: nowElapsed,
            ))
        } else {
            handler.removeCallbacks(playbackTimeout)
            pendingPlayback = null
            MediaRepository.update(nowPlaying)
        }
    }

    override fun playPause() {
        val controller = activeController ?: return
        val snapshot = MediaRepository.nowPlaying.value
            ?.takeIf { it.packageName == controller.packageName } ?: return
        val now = SystemClock.elapsedRealtime()
        val target = snapshot.copy(
            isPlaying = !snapshot.isPlaying,
            positionMs = snapshot.positionAt(now),
            publishedAtElapsedMs = if (snapshot.publishedAtElapsedMs > 0L) now else 0L,
            playbackSpeed = snapshot.playbackSpeed.takeIf { it.isFinite() && it != 0f } ?: 1f,
        )
        pendingPlayback = target
        MediaRepository.update(target)
        handler.removeCallbacks(playbackTimeout)
        handler.postDelayed(playbackTimeout, 1500L)
        try {
            if (target.isPlaying) controller.transportControls.play()
            else controller.transportControls.pause()
        } catch (e: RuntimeException) {
            handler.removeCallbacks(playbackTimeout)
            pendingPlayback = null
            publish(controller)
            Log.w("IslandMedia", "Playback command failed", e)
        }
    }

    override fun next() {
        activeController?.transportControls?.skipToNext()
    }

    override fun previous() {
        activeController?.transportControls?.skipToPrevious()
    }

    override fun customAction(packageName: String, actionId: String) {
        val controller = activeController?.takeIf { it.packageName == packageName } ?: return
        val customAction = controller.playbackState?.customActions?.firstOrNull { it.action == actionId } ?: return
        val notifAction = runCatching {
            val matchingSbns = (activeNotifications ?: emptyArray()).filter { sbn ->
                sbn.packageName == packageName &&
                    BundleCompat.getParcelable(sbn.notification.extras, "android.mediaSession", MediaSession.Token::class.java) == controller.sessionToken
            }
            matchingSbns.flatMap { it.notification.actions?.toList() ?: emptyList() }
                .singleOrNull { it.title?.toString() == customAction.name.toString() && it.actionIntent != null }
        }.getOrNull()

        if (notifAction != null) {
            try {
                notifAction.actionIntent.send()
                Log.d("IslandMedia", "Sent action ${customAction.name} via notification intent")
                return
            } catch (e: android.app.PendingIntent.CanceledException) {
                Log.w("IslandMedia", "Source action expired; using current session", e)
            }
        }

        runCatching {
            controller.transportControls.sendCustomAction(customAction.action, customAction.extras)
        }.onSuccess {
            Log.d("IslandMedia", "Sent action ${customAction.name} via transportControls")
        }.onFailure {
            Log.w("IslandMedia", "Failed to send action ${customAction.name}", it)
        }
    }

    override fun seekTo(fraction: Float) {
        val duration = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return
        if (duration > 0 && !fraction.isInfinite() && !fraction.isNaN()) {
            activeController?.transportControls?.seekTo((duration.toFloat() * fraction.coerceIn(0f, 1f)).toLong())
        }
    }
}

