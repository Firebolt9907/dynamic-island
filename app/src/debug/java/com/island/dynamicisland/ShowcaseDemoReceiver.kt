package com.island.dynamicisland

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import com.island.dynamicisland.live.LiveUpdateRepository
import com.island.dynamicisland.live.readLiveUpdate

/**
 * Shell-only, debug-only showcase that cycles through common live activities for recording
 * smoothness GIFs.
 *
 *   adb shell am broadcast -n com.island.dynamicisland/.ShowcaseDemoReceiver --es action start
 *   adb shell am broadcast -n com.island.dynamicisland/.ShowcaseDemoReceiver --es action stop
 *
 * Extras for `start`: `stepMs` (int, default 3200), `loop` (bool, default true),
 * `music` (bool, default true), `scenes` (comma list, default all, in the order given).
 *
 * Each activity is a synthetic notification shaped like the real source app's (the same
 * fields, layouts, and controls the README screenshots show). It is parsed by the production
 * [readLiveUpdate] and published to [LiveUpdateRepository], like the hotspot fixture. Nothing
 * is posted to the shade, and the island attributes each one to the real source package.
 * Music is a real local MediaSession, which the listener binds to like any player.
 */
class ShowcaseDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.getStringExtra("action") ?: "start") {
            "stop" -> Showcase.stop()
            "noop" -> Unit
            else -> Showcase.start(
                app,
                stepMs = intent.getIntExtra("stepMs", 3200).toLong().coerceAtLeast(800L),
                loop = intent.getBooleanExtra("loop", true),
                music = intent.getBooleanExtra("music", true),
                scenes = intent.getStringExtra("scenes")?.split(',')?.map { it.trim().lowercase() }
                    ?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() } ?: Showcase.allScenes,
            )
        }
    }
}

private object Showcase {
    val allScenes = listOf("agent", "sports", "nav", "transfer", "timer", "call", "ride", "multi")

    private const val AGENT = 1901
    private const val SPORTS = 1902
    private const val NAV = 1903
    private const val TRANSFER = 1904
    private const val TIMER = 1905
    private const val STOPWATCH = 1906
    private const val CALL = 1907
    private const val RIDE = 1908

    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private val published = mutableSetOf<String>()
    private var session: MediaSession? = null
    private var track = 0

    fun start(context: Context, stepMs: Long, loop: Boolean, music: Boolean, scenes: List<String>) {
        stop()
        schedule(context, stepMs, loop, music, scenes)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(token)
        clearActivities()
        session?.run { isActive = false; release() }
        session = null
        track = 0
    }

    private fun schedule(context: Context, stepMs: Long, loop: Boolean, music: Boolean, scenes: List<String>) {
        val origin = SystemClock.uptimeMillis()
        var cursor = 0L
        fun at(offset: Long, block: () -> Unit) {
            handler.postAtTime(block, token, origin + cursor + offset)
        }
        if (music) {
            at(0) { playMusic(context, track) }
            cursor += stepMs
        }
        for (scene in scenes) {
            // Each scene replaces the previous one, so every arrival gets its own expansion.
            at(0) { clearActivities() }
            cursor += 350L
            cursor += when (scene) {
                "agent" -> {
                    at(0) { agent(context, listOf(
                        "Working Add Taste Profiles to Food Log · boiler_fuel",
                        "Working Fix Retro Zoom Gesture Conflict · droidmirror",
                        "Done Remap Android Enter Key to Send · droidmirror",
                        "Done Meal Logging & Taste Sync · boiler_fuel",
                    )) }
                    // Only a thread starting to wait on the user reopens the island.
                    at(stepMs) { agent(context, listOf(
                        "Needs approval Add Taste Profiles to Food Log · boiler_fuel",
                        "Working Fix Retro Zoom Gesture Conflict · droidmirror",
                        "Done Remap Android Enter Key to Send · droidmirror",
                        "Done Meal Logging & Taste Sync · boiler_fuel",
                    )) }
                    stepMs * 2
                }
                "sports" -> {
                    at(0) { sports(context, 24, 17, "2:15 - 4th", "1st & 15\nHAWK 32", possession = 1,
                        "Receiving Leaders", listOf(
                            Player("D. Staley", "5 REC, 50 YDS, 0 TD", 0xFFFF8200.toInt()),
                            Player("M. Bennett Jr.", "6 REC, 88 YDS, 1 TD", 0xFF73000A.toInt()))) }
                    // A score change reopens the card, ticks the digits, and flips possession.
                    at(stepMs) { sports(context, 24, 24, "1:48 - 4th", "1st & 10\nHAWK 25", possession = 2,
                        "Receiving Leaders", listOf(
                            Player("D. Staley", "5 REC, 50 YDS, 0 TD", 0xFFFF8200.toInt()),
                            Player("M. Bennett Jr.", "7 REC, 135 YDS, 2 TD", 0xFF73000A.toInt()))) }
                    stepMs * 2
                }
                "nav" -> {
                    at(0) { navigation(context, "Head toward Market St", "0.4 mi", "Arrive 6:42 PM") }
                    at(stepMs / 2) { navigation(context, "Head toward Market St", "500 ft", "Arrive 6:42 PM") }
                    at(stepMs) { navigation(context, "Turn right onto Market St", "800 ft", "Arrive 6:42 PM") }
                    at(stepMs * 3 / 2) { navigation(context, "Turn right onto Market St", "200 ft", "Arrive 6:41 PM") }
                    stepMs * 2
                }
                "transfer" -> {
                    val ticks = listOf(4, 18, 37, 55, 72, 88, 97)
                    ticks.forEachIndexed { i, p -> at(i * stepMs / ticks.size) { transfer(context, p) } }
                    stepMs + stepMs / 4
                }
                "timer" -> {
                    at(0) { timer(context) }
                    stepMs
                }
                "call" -> {
                    at(0) { call(context) }
                    stepMs
                }
                "ride" -> {
                    at(0) { ride(context, 40, "4 min", "Alex is 4 min away") }
                    at(stepMs / 2) { ride(context, 70, "2 min", "Alex is 2 min away") }
                    stepMs
                }
                "stopwatch" -> {
                    at(0) { stopwatch(context) }
                    stepMs
                }
                "music" -> {
                    at(0) { playMusic(context, ++track) }
                    stepMs
                }
                "multi" -> {
                    // Several concurrent activities share the compact island.
                    at(0) { agent(context, listOf(
                        "Working Add Taste Profiles to Food Log · boiler_fuel",
                        "Working Fix Retro Zoom Gesture Conflict · droidmirror",
                    )) }
                    at(stepMs / 3) { stopwatch(context) }
                    if (music) at(stepMs) { playMusic(context, ++track) }
                    stepMs * 2
                }
                else -> 0L
            }
        }
        at(0) { clearActivities() }
        cursor += 900L
        // Keep the media session across loops so playback continues through the seam.
        if (loop) at(0) { schedule(context, stepMs, loop, music, scenes) }
        else at(0) { stop() }
    }

    private fun clearActivities() {
        published.forEach { LiveUpdateRepository.put(it, null) }
        published.clear()
    }

    /**
     * Parses a notification exactly as the listener would, then attributes it to [sourcePackage].
     * The synthetic record keeps this package so its resources and RemoteViews resolve.
     */
    private fun publish(context: Context, id: Int, sourcePackage: String, appName: String,
        channel: String = "showcase", build: Notification.Builder.() -> Unit) {
        val launch = context.packageManager.getLaunchIntentForPackage(sourcePackage)
            ?: Intent(context, MainActivity::class.java)
        val notification = Notification.Builder(context, channel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_IMMUTABLE))
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .apply(build)
            .build()
        val sbn = StatusBarNotification(context.packageName, context.packageName, id, null, Process.myUid(),
            Process.myPid(), 0, notification, Process.myUserHandle(), System.currentTimeMillis())
        val update = readLiveUpdate(context, sbn)?.copy(packageName = sourcePackage, appName = appName)
        if (update == null) {
            android.util.Log.w("IslandShowcase", "$appName fixture was not recognized as a live activity")
            return
        }
        published += sbn.key
        LiveUpdateRepository.put(sbn.key, update)
    }

    private fun action(context: Context, label: String): Notification.Action = Notification.Action.Builder(null, label,
        PendingIntent.getBroadcast(context, label.hashCode(), Intent(context, ShowcaseDemoReceiver::class.java)
            .putExtra("action", "noop"), PendingIntent.FLAG_IMMUTABLE)).build()

    private fun Notification.Builder.critical(text: String) = apply {
        if (Build.VERSION.SDK_INT >= 36) setShortCriticalText(text)
    }

    // T3 Code: one aggregate notification, a "<Status> <thread> · <project>" line per thread.
    private fun agent(context: Context, lines: List<String>) =
        publish(context, AGENT, "com.t3tools.t3code", "T3 Code") {
            setSmallIcon(Icon.createWithBitmap(textGlyph("T3", 0xFF5AC8FA.toInt())))
            setColor(0xFF5AC8FA.toInt())
            setContentTitle("T3 Code")
            setContentText(lines.first())
            setStyle(Notification.InboxStyle().also { style -> lines.forEach(style::addLine) })
            addAction(action(context, "Open"))
            addAction(action(context, "Dismiss"))
        }

    private data class Player(val name: String, val stats: String, val color: Int)

    // ESPN: custom RemoteViews with logos, scores, multi-line game state, possession, and leaders.
    private fun sports(context: Context, home: Int, away: Int, clock: String, situation: String, possession: Int,
        notesTitle: String, players: List<Player>) =
        publish(context, SPORTS, "com.espn.score_center", "ESPN", channel = "espn_live_scores") {
            val views = RemoteViews(context.packageName, R.layout.showcase_sports).apply {
                setImageViewBitmap(R.id.iv_team_one_logo, teamLogo("H", 0xFFFF8200.toInt(), 0xFFFFFFFF.toInt()))
                setTextViewText(R.id.tv_live_team_one_name, "HAWK")
                setTextViewText(R.id.tv_live_team_one_score, home.toString())
                setImageViewBitmap(R.id.iv_team_two_logo, teamLogo("B", 0xFF73000A.toInt(), 0xFFFFFFFF.toInt()))
                setTextViewText(R.id.tv_live_team_two_name, "BEAR")
                setTextViewText(R.id.tv_live_team_two_score, away.toString())
                setTextViewText(R.id.tv_status_primary, clock)
                setTextViewText(R.id.tv_status_secondary, situation)
                setViewVisibility(R.id.tv_status_secondary, android.view.View.VISIBLE)
                setViewVisibility(R.id.team_one_possession, if (possession == 1) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(R.id.team_two_possession, if (possession == 2) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(R.id.tv_game_notes_title, notesTitle)
                listOf(
                    Triple(R.id.iv_note_avatar_first, R.id.tv_note_label_first, R.id.tv_note_message_first),
                    Triple(R.id.iv_note_avatar_second, R.id.tv_note_label_second, R.id.tv_note_message_second),
                ).zip(players).forEach { (ids, player) ->
                    setImageViewBitmap(ids.first, portrait(player.color))
                    setTextViewText(ids.second, player.name)
                    setTextViewText(ids.third, player.stats)
                }
            }
            setSmallIcon(android.R.drawable.ic_media_play)
            setColor(0xFFCC0000.toInt())
            setContentTitle("HAWK vs BEAR")
            setContentText(clock)
            setCustomContentView(views)
            setCustomBigContentView(views)
            setStyle(Notification.DecoratedCustomViewStyle())
            addExtras(Bundle().apply { putBoolean("android.contains.customView", true) })
            critical("$home - $away")
        }

    // Google Maps: the direction as title, distance as the critical status, arrival as detail.
    private fun navigation(context: Context, direction: String, distance: String, arrival: String) =
        publish(context, NAV, "com.google.android.apps.maps", "Maps") {
            setSmallIcon(Icon.createWithBitmap(arrowGlyph(0xFF1EA362.toInt())))
            setColor(0xFF1EA362.toInt())
            setCategory(Notification.CATEGORY_NAVIGATION)
            setContentTitle(direction)
            setContentText(arrival)
            critical(distance)
            addAction(action(context, "Exit navigation"))
        }

    // Blip: a device-to-device send with determinate progress and a Cancel control.
    private fun transfer(context: Context, percent: Int) =
        publish(context, TRANSFER, "net.blip.android", "Blip") {
            setSmallIcon(Icon.createWithBitmap(uploadGlyph(0xFFB57BFF.toInt())))
            setColor(0xFFB57BFF.toInt())
            setCategory(Notification.CATEGORY_PROGRESS)
            setContentTitle("IMG_4821.mov")
            setContentText("Sending to MacBook Pro")
            setSubText("of 16.2 MB")
            setProgress(100, percent, false)
            addAction(action(context, "Cancel"))
        }

    // Clock: a running countdown with its controls.
    private fun timer(context: Context) =
        publish(context, TIMER, "com.google.android.deskclock", "Clock") {
            setSmallIcon(Icon.createWithBitmap(stopwatchGlyph(0xFFFFFFFF.toInt())))
            setColor(0xFF7FA7FF.toInt())
            setCategory(Notification.CATEGORY_ALARM)
            setContentTitle("Timer")
            setUsesChronometer(true)
            setChronometerCountDown(true)
            setWhen(System.currentTimeMillis() + 4 * 60_000L + 59_000L)
            addAction(action(context, "Pause"))
            addAction(action(context, "+1:00"))
        }

    // Clock: a running stopwatch. Stopwatches stay compact until held, by design.
    private fun stopwatch(context: Context) =
        publish(context, STOPWATCH, "com.google.android.deskclock", "Clock") {
            setSmallIcon(Icon.createWithBitmap(stopwatchGlyph(0xFFFFFFFF.toInt())))
            setColor(0xFF7FA7FF.toInt())
            setCategory(Notification.CATEGORY_STOPWATCH)
            setContentTitle("Stopwatch")
            setUsesChronometer(true)
            setWhen(System.currentTimeMillis() - 8_000L)
            addAction(action(context, "Pause"))
            addAction(action(context, "Lap"))
        }

    // Phone: an ongoing CallStyle call with caller avatar, timer, and in-call controls.
    private fun call(context: Context) =
        publish(context, CALL, "com.google.android.dialer", "Phone") {
            val caller = Person.Builder().setName("Jordan Lee")
                .setIcon(Icon.createWithBitmap(portrait(0xFF3A7BD5.toInt()))).build()
            setSmallIcon(android.R.drawable.sym_call_outgoing)
            setCategory(Notification.CATEGORY_CALL)
            setStyle(Notification.CallStyle.forOngoingCall(caller, action(context, "Hang up").actionIntent))
            setContentText("Mobile")
            setUsesChronometer(true)
            setWhen(System.currentTimeMillis() - 65_000L)
            addAction(action(context, "Mute"))
            addAction(action(context, "Speaker"))
        }

    // Uber: a ride ETA with trip progress.
    private fun ride(context: Context, progress: Int, eta: String, text: String) =
        publish(context, RIDE, "com.ubercab", "Uber") {
            setSmallIcon(android.R.drawable.ic_menu_directions)
            setColor(0xFFFFFFFF.toInt())
            setContentTitle(text)
            setContentText("Gray Toyota Camry · 7ABC123")
            setProgress(100, progress, false)
            critical(eta)
            addAction(action(context, "Contact"))
        }

    private data class Track(val title: String, val artist: String, val album: String, val durationMs: Long,
        val colors: IntArray)

    private val tracks = listOf(
        Track("Neon Tides", "Static Harbor", "Night Shift", 234_000L, intArrayOf(0xFFFF4D8D.toInt(), 0xFF6A2BFF.toInt())),
        Track("Paper Satellites", "Lumen Fields", "Low Orbit", 187_000L, intArrayOf(0xFF00C2A8.toInt(), 0xFF0A3D91.toInt())),
        Track("Golden Hour Drive", "The Overpass", "Westbound", 236_000L, intArrayOf(0xFFFFB443.toInt(), 0xFFE0412F.toInt())),
    )

    /** Starts (or switches) a playing local media session the island binds to like any player. */
    private fun playMusic(context: Context, index: Int) {
        val track = tracks[index % tracks.size]
        val media = session ?: MediaSession(context, "IslandShowcase").also { created ->
            created.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = setPlaying(created, true)
                override fun onPause() = setPlaying(created, false)
                override fun onSkipToNext() = playMusic(context, ++this@Showcase.track)
                override fun onSkipToPrevious() {
                    this@Showcase.track = (this@Showcase.track + tracks.size - 1) % tracks.size
                    playMusic(context, this@Showcase.track)
                }
                override fun onSeekTo(pos: Long) = setPlaying(created, true, pos)
            })
            session = created
        }
        media.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "showcase-${index % tracks.size}")
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork(track.colors))
            .build())
        setPlaying(media, true, 15_000L)
        media.isActive = true
    }

    private fun setPlaying(media: MediaSession, playing: Boolean, position: Long? = null) {
        val current = media.controller.playbackState
        val now = SystemClock.elapsedRealtime()
        val resumeAt = position ?: current?.let {
            if (it.state == PlaybackState.STATE_PLAYING) it.position + (now - it.lastPositionUpdateTime) else it.position
        } ?: 0L
        media.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, resumeAt,
                if (playing) 1f else 0f, now)
            .build())
    }

    // Generated art only: no third-party logos, portraits, or album covers ship in the demo.

    private fun canvas(size: Int = 96, draw: Canvas.(Paint) -> Unit): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            Canvas(it).draw(Paint(Paint.ANTI_ALIAS_FLAG))
        }

    private fun textGlyph(text: String, color: Int) = canvas { paint ->
        paint.color = color
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 52f
        drawText(text, 48f, 48f - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    private fun arrowGlyph(color: Int) = canvas { paint ->
        paint.color = color
        drawPath(Path().apply {
            moveTo(48f, 10f); lineTo(80f, 44f); lineTo(58f, 44f); lineTo(58f, 86f)
            lineTo(38f, 86f); lineTo(38f, 44f); lineTo(16f, 44f); close()
        }, paint)
    }

    private fun uploadGlyph(color: Int) = canvas { paint ->
        paint.color = color
        drawPath(Path().apply {
            moveTo(48f, 10f); lineTo(80f, 42f); lineTo(60f, 42f); lineTo(60f, 66f)
            lineTo(36f, 66f); lineTo(36f, 42f); lineTo(16f, 42f); close()
        }, paint)
        drawRect(18f, 76f, 78f, 86f, paint)
    }

    private fun stopwatchGlyph(color: Int) = canvas { paint ->
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        drawCircle(48f, 54f, 32f, paint)
        drawLine(48f, 54f, 48f, 36f, paint)
        drawLine(40f, 10f, 56f, 10f, paint)
        drawLine(48f, 10f, 48f, 20f, paint)
    }

    private fun teamLogo(letter: String, fill: Int, ink: Int) = canvas(128) { paint ->
        paint.color = fill
        drawRoundRect(RectF(10f, 10f, 118f, 118f), 30f, 30f, paint)
        paint.color = ink
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 84f
        drawText(letter, 64f, 64f - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    private fun portrait(jersey: Int) = canvas { paint ->
        paint.color = 0xFF2C2C2E.toInt()
        drawCircle(48f, 48f, 48f, paint)
        paint.color = jersey
        drawOval(RectF(14f, 60f, 82f, 120f), paint)
        paint.color = 0xFFB98A68.toInt()
        drawCircle(48f, 40f, 18f, paint)
    }

    private fun artwork(colors: IntArray): Bitmap = canvas(512) { paint ->
        paint.shader = LinearGradient(0f, 0f, 512f, 512f, colors[0], colors[1], Shader.TileMode.CLAMP)
        drawRect(0f, 0f, 512f, 512f, paint)
        paint.shader = RadialGradient(358f, 154f, 230f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        drawCircle(358f, 154f, 230f, paint)
        paint.shader = null
        paint.color = 0x33000000
        drawCircle(154f, 400f, 112f, paint)
    }
}
