package com.island.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews

/** Shell-only, debug-only fixture exercising the real notification listener and actions. */
class LiveUpdateDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("inspectClockNotification", false)) {
            val listener = com.island.dynamicisland.media.MediaRepository.commands as?
                com.island.dynamicisland.service.MediaNotificationListener
            resultData = listener?.activeNotifications.orEmpty()
                .filter { it.packageName == "com.google.android.deskclock" }
                .joinToString("\n") { sbn ->
                    val parsed = com.island.dynamicisland.live.readLiveUpdate(context, sbn)
                    "key=${sbn.key} clock=${com.island.dynamicisland.live.readNotificationClock(context, sbn.notification)} " +
                        "title=${parsed?.title} countdown=${parsed?.countdown} stopwatch=${parsed?.stopwatch} " +
                        "display=${parsed?.countdown?.format(android.os.SystemClock.elapsedRealtime())} " +
                        "actions=${parsed?.actions?.map { it.title }}"
                }.ifBlank { "No active Clock notification or listener" }
            return
        }
        if (intent.getBooleanExtra("inspectClockApi", false)) {
            resultData = listOf("forStopwatch", "forPausedStopwatch", "forTimer", "forPausedTimer").joinToString("\n") { name ->
                runCatching {
                    val type = Class.forName("android.app.Notification\$Metric\$TimeDifference")
                    val paused = name.contains("Paused")
                    val value = type.getMethod(name, if (paused) java.time.Duration::class.java else Long::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType).invoke(null,
                        if (paused) java.time.Duration.ofSeconds(65) else android.os.SystemClock.elapsedRealtime() +
                            (if (name == "forTimer") 65000L else -65000L), 2)
                    val metricType = Class.forName("android.app.Notification\$Metric")
                    val metric = metricType.constructors.first { it.parameterCount == 2 }.newInstance(value, "Clock fixture")
                    val styleType = Class.forName("android.app.Notification\$MetricStyle")
                    val style = styleType.getConstructor().newInstance()
                    styleType.getMethod("addMetric", metricType).invoke(style, metric)
                    val notification = Notification.Builder(context, "clock-fixture")
                        .setSmallIcon(android.R.drawable.stat_notify_sync).setStyle(style as Notification.Style).build()
                    "$name: ${com.island.dynamicisland.live.readNotificationClock(context, notification)}"
                }.getOrElse { "$name: $it" }
            }
            return
        }
        if (intent.hasExtra("bluetoothPreview")) {
            com.island.dynamicisland.audio.BluetoothAudioMonitor.active?.showPreview(
                intent.getStringExtra("bluetoothPreview").orEmpty(), intent.getIntExtra("battery", 60))
            return
        }
        if (intent.getBooleanExtra("dumpViews", false)) {
            val pkg = intent.getStringExtra("package") ?: "com.espn.score_center"
            val activity = com.island.dynamicisland.live.LiveUpdateRepository.activities.value
                .firstOrNull { it.packageName == pkg }
            if (activity == null || activity.customContent == null) {
                resultData = "No activity or customContent found for $pkg"
                return
            }
            val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull()
            val raw = activity.customContent.apply(context, android.widget.FrameLayout(context))
            val lines = mutableListOf<String>()
            fun dump(view: android.view.View, indent: String = "") {
                val idName = if (view.id != android.view.View.NO_ID && res != null) {
                    runCatching { res.getResourceEntryName(view.id) }.getOrDefault(view.id.toString())
                } else view.id.toString()
                val text = if (view is android.widget.TextView) " text=\"${view.text}\"" else ""
                val desc = if (view.contentDescription != null) " desc=\"${view.contentDescription}\"" else ""
                val drawable = if (view is android.widget.ImageView) " drawable=${view.drawable?.javaClass?.simpleName}:${view.drawable?.intrinsicWidth}x${view.drawable?.intrinsicHeight}" else ""
                val vis = when (view.visibility) {
                    android.view.View.VISIBLE -> "VISIBLE"
                    android.view.View.INVISIBLE -> "INVISIBLE"
                    else -> "GONE"
                }
                val line = "$indent${view.javaClass.simpleName} id=$idName vis=$vis$text$desc$drawable"
                lines += line
                android.util.Log.d("IslandDumpViews", line)
                if (view is android.view.ViewGroup) {
                    for (i in 0 until view.childCount) {
                        dump(view.getChildAt(i), "$indent  ")
                    }
                }
            }
            dump(raw)
            resultData = lines.joinToString("\n")
            return
        }
        if (intent.getBooleanExtra("inspectHistory", false) || intent.hasExtra("sourcePackage")) {
            val config = com.island.dynamicisland.config.LiveActivityConfig
            config.init(context)
            if (intent.hasExtra("sourcePackage")) {
                val source = config.history.value.firstOrNull {
                    it.packageName == intent.getStringExtra("sourcePackage") && it.channelId == intent.getStringExtra("sourceChannel")
                }
                if (source == null) { resultData = "Source not found"; return }
                config.setEnabled(source.key, intent.getBooleanExtra("sourceEnabled", true))
            }
            resultData = config.history.value.joinToString { "${it.packageName}/${it.channelId}: ${it.title} enabled=${it.enabled}" }
                .ifBlank { "No history" }
            return
        }
        if (intent.getBooleanExtra("inspectCustom", false)) {
            resultData = com.island.dynamicisland.live.LiveUpdateRepository.activities.value
                .filter { it.customContent != null }.joinToString { activity ->
                    runCatching {
                        val host = com.island.dynamicisland.ui.island.CustomNotificationHost(context)
                        host.bind(activity.customContent!!, activity.customParts)
                        var clocks = 0
                        var controls = 0
                        fun inspect(view: android.view.View) {
                            if (view is android.widget.Chronometer) clocks++
                            if (view.hasOnClickListeners()) controls++
                            if (view is android.view.ViewGroup) {
                                for (index in 0 until view.childCount) inspect(view.getChildAt(index))
                            }
                        }
                        inspect(host)
                        host.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                            (320 * context.resources.displayMetrics.density).toInt(), android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
                        val timer = activity.customParts?.let { host.findViewById<android.widget.Chronometer>(it.timerId) }
                        val preview = com.island.dynamicisland.ui.island.CustomNotificationHost(context)
                        preview.bind(activity.customContent!!, activity.customParts, timerOnly = true)
                        val previewTimer = activity.customParts?.let { preview.findViewById<android.widget.Chronometer>(it.timerId) }
                        val raw = activity.customContent!!.apply(context, android.widget.FrameLayout(context))
                        val sourceControls = mutableListOf<String>()
                        fun source(view: android.view.View, visible: Boolean = true) {
                            val shown = visible && view.visibility == android.view.View.VISIBLE
                            if (view.hasOnClickListeners()) sourceControls += "${view.javaClass.simpleName}:${view.contentDescription}:visible=$shown:id=${view.id}"
                            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) source(view.getChildAt(i), shown)
                        }
                        source(raw)
                        "${activity.packageName}: normalized=${activity.customParts != null}, height=${host.measuredHeight}, chronometers=$clocks, controls=$controls, actions=${activity.actions.map { it.title }}, sourceControls=$sourceControls, time=${timer?.text}, preview=${previewTimer?.text}, sameBase=${timer != null && timer.base == previewTimer?.base}"
                    }.getOrElse { "${activity.packageName}: ${it.javaClass.simpleName}" }
                }.ifBlank { "No custom activities" }
            return
        }
        if (intent.getBooleanExtra("inspectActivities", false)) {
            resultData = com.island.dynamicisland.live.LiveUpdateRepository.activities.value
                .joinToString { "${it.packageName}: ${it.title} status='${it.status}', call=${it.isCall}, controls=${it.actions.map { a -> "${a.title}:${a.callControl?.kind}:${a.callControl?.active}:end=${a.isHangUp}" }}, sports=${it.sportsScore != null}, gameStatus=${it.sportsScore?.gameStatus}, teams=${it.sportsScore?.summaryTitle}, score=${it.sportsScore?.formattedScore}, notes=${it.sportsScore?.notes}, players=${it.sportsScore?.players?.map { p -> "${p.name}: ${p.stats} portrait=${p.portrait != null}" }}, t1Logo=${it.sportsScore?.teamOneLogo?.let { b -> "${b.width}x${b.height}" }}, t2Logo=${it.sportsScore?.teamTwoLogo?.let { b -> "${b.width}x${b.height}" }}" }.ifBlank { "No live activities" }
            return
        }
        if (intent.getBooleanExtra("inspectStopwatch", false)) {
            val clocks = com.island.dynamicisland.live.LiveUpdateRepository.activities.value.mapNotNull { it.stopwatch }
            resultData = clocks.joinToString {
                val state = if (it.startedAtElapsedMs == null) "paused" else "running"
                "$state: ${it.format(android.os.SystemClock.elapsedRealtime(), true)}"
            }.ifBlank { "No stopwatch time source found" }
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)

        if (intent.getStringExtra("type") == "hotspot") {
            // A normal app cannot post as Android System. Parse a synthetic system
            // notification through the production reader without enabling tethering.
            val key = "hotspot-demo"
            if (intent.getBooleanExtra("clear", false)) {
                com.island.dynamicisland.live.LiveUpdateRepository.put(key, null)
                return
            }
            val notification = Notification.Builder(context, "TETHERING_STATUS")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentTitle(intent.getStringExtra("title") ?: "Hotspot is on")
                .setContentText(intent.getStringExtra("text") ?: "2 devices connected")
                .setOngoing(intent.getBooleanExtra("ongoing", true))
                .setAutoCancel(intent.getBooleanExtra("autoCancel", false))
                .setGroupSummary(intent.getBooleanExtra("summary", false))
                .setContentIntent(PendingIntent.getActivity(context, 1711,
                    Intent("android.settings.TETHER_SETTINGS"), PendingIntent.FLAG_IMMUTABLE))
                .build()
            val sbn = android.service.notification.StatusBarNotification(
                "android", "android", 1711, null, android.os.Process.SYSTEM_UID, 0, 0,
                notification, android.os.Process.myUserHandle(), System.currentTimeMillis())
            val update = com.island.dynamicisland.live.readLiveUpdate(context, sbn)?.copy(key = key)
            com.island.dynamicisland.live.LiveUpdateRepository.put(key, update)
            resultData = update?.let { "hotspot=${it.isHotspot}, status=${it.status}, source=${it.contentIntent != null}" }
                ?: "No hotspot activity"
            return
        }

        if (intent.getStringExtra("type") == "call") {
            val id = 1710
            if (intent.getBooleanExtra("clear", false)) { manager.cancel(id); return }
            manager.createNotificationChannel(NotificationChannel("call_demo", "Call demo", NotificationManager.IMPORTANCE_LOW))
            fun control(name: String): PendingIntent = PendingIntent.getBroadcast(context, name.hashCode(),
                Intent(context, LiveUpdateDemoReceiver::class.java).putExtra("type", "call")
                    .putExtra("clear", name == "End call").putExtra("muted", name == "Mute")
                    .putExtra("speaker", name == "Speaker")
                    .putExtra("legacy", intent.getBooleanExtra("legacy", false))
                    .putExtra("title", intent.getStringExtra("title"))
                    .putExtra("text", intent.getStringExtra("text")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // Publish the public CallStyle metadata without claiming a real phone FGS.
            // Android rejects actual CallStyle fixtures outside a foreground call service.
            val callExtras = Bundle().apply {
                if (!intent.getBooleanExtra("legacy", false)) {
                    putInt("android.callType", if (intent.getBooleanExtra("incoming", false)) 1 else 2)
                    putParcelable("android.hangUpIntent", control("End call"))
                }
            }
            manager.notify(id, Notification.Builder(context, "call_demo")
                .setSmallIcon(android.R.drawable.sym_call_outgoing).setCategory(Notification.CATEGORY_CALL)
                .setContentTitle(intent.getStringExtra("title") ?: if (intent.getBooleanExtra("incoming", false)) "Incoming call" else "Demo caller")
                .setContentText(intent.getStringExtra("text")).addExtras(callExtras)
                .setOngoing(true).setWhen(System.currentTimeMillis() - 65000)
                .setUsesChronometer(true)
                .setContentIntent(PendingIntent.getActivity(context, 1710, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .addAction(Notification.Action.Builder(null, "End call", control("End call")).build())
                .addAction(Notification.Action.Builder(null, if (intent.getBooleanExtra("muted", false)) "Unmute" else "Mute", control(if (intent.getBooleanExtra("muted", false)) "Unmute" else "Mute")).build())
                .addAction(Notification.Action.Builder(null, if (intent.getBooleanExtra("speaker", false)) "Earpiece" else "Speaker", control(if (intent.getBooleanExtra("speaker", false)) "Earpiece" else "Speaker")).build())
                .build())
            return
        }

        val isEspn = intent.getStringExtra("type")?.equals("espn", ignoreCase = true) == true ||
            intent.getStringExtra("type")?.equals("sports", ignoreCase = true) == true ||
            intent.hasExtra("team1") || intent.hasExtra("score1")

        if (isEspn) {
            val id = intent.getIntExtra("id", 1703)
            if (intent.getBooleanExtra("clear", false)) { manager.cancel(id); return }
            val team1 = intent.getStringExtra("team1") ?: "TENN"
            val score1 = intent.getIntExtra("score1", 21)
            val team2 = intent.getStringExtra("team2") ?: "SC"
            val score2 = intent.getIntExtra("score2", 17)
            val gameStatus = intent.getStringExtra("status") ?: "Halftime"
            val notesTitle = intent.getStringExtra("notesTitle") ?: "Rushing Leaders"
            val notesMessage = intent.getStringExtra("notes") ?: "D. Bishop: 16 CAR, 61 YDS, 1 TD · L. Sellers: 8 CAR, 61 YDS, 1 TD"
            val scoreText = "$score1 - $score2"

            fun createBadge(name: String, bg: Int): Bitmap {
                val size = 96
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                paint.apply {
                    color = android.graphics.Color.WHITE
                    textSize = 32f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                val y = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
                canvas.drawText(name.take(3).uppercase(), size / 2f, y, paint)
                return bmp
            }

            val t1Logo = createBadge(team1, 0xFFFF8200.toInt())
            val t2Logo = createBadge(team2, 0xFF73000A.toInt())

            val views = RemoteViews(context.packageName, R.layout.live_update_espn_demo).apply {
                setTextViewText(R.id.tv_live_team_one_name, team1)
                setTextViewText(R.id.tv_live_team_one_score, score1.toString())
                setImageViewBitmap(R.id.iv_team_one_logo, t1Logo)
                setTextViewText(R.id.tv_live_team_two_name, team2)
                setTextViewText(R.id.tv_live_team_two_score, score2.toString())
                setImageViewBitmap(R.id.iv_team_two_logo, t2Logo)
                setTextViewText(R.id.tv_status_primary, gameStatus)
                setTextViewText(R.id.tv_game_notes_title, notesTitle)
                setTextViewText(R.id.tv_note_message, notesMessage)
            }

            manager.createNotificationChannel(NotificationChannel("espn_live_scores", "Live Game Scores", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, "espn_live_scores")
                .setColor(intent.getIntExtra("color", 0xCC0000.toInt()))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("$team1 vs $team2")
                .setContentText(gameStatus)
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setOngoing(!intent.getBooleanExtra("temporary", false))
                .setAutoCancel(intent.getBooleanExtra("autoCancel", false))
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addExtras(Bundle().apply {
                    putBoolean("android.contains.customView", true)
                    putBoolean("android.requestPromotedOngoing", !intent.getBooleanExtra("plainCustom", false))
                })
            if (Build.VERSION.SDK_INT >= 36 && !intent.getBooleanExtra("plainCustom", false)) builder.setShortCriticalText(scoreText)
            manager.notify(id, builder.build())
            return
        }

        val id = intent.getIntExtra("id", 1701)
        if (intent.getBooleanExtra("clear", false)) { manager.cancel(id); return }
        manager.createNotificationChannel(NotificationChannel("live_demo", "Live Update demo", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getBroadcast(context, id, Intent(context, LiveUpdateDemoReceiver::class.java)
            .putExtra("clear", true).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val progress = intent.getIntExtra("progress", 65)
        val builder = Notification.Builder(context, "live_demo")
            .setColor(intent.getIntExtra("color", Notification.COLOR_DEFAULT))
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Your ride is arriving")
            .setContentText("Alex is 3 minutes away")
            .setSubText("Blue sedan · ABC 1234")
            .setStyle(Notification.BigTextStyle().bigText("Meet at the main entrance.\nPickup confirmed · 2 passengers"))
            .setOngoing(!intent.getBooleanExtra("temporary", false)).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100, progress, false)
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", !intent.getBooleanExtra("plainCustom", false)) })
            .addAction(Notification.Action.Builder(null, "View ride", open).build())
            .addAction(Notification.Action.Builder(null, "End demo", cancel).build())
        if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText("3 min")
        manager.notify(id, builder.build())
    }
}
