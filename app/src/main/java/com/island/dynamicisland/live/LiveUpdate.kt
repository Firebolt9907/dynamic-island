package com.island.dynamicisland.live

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveAction(val title: String, val intent: PendingIntent, val icon: Bitmap? = null, val isHangUp: Boolean = false, val callControl: CallControl? = null)

data class LiveUpdate(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val details: String,
    val status: String,
    val icon: Bitmap?,
    val progress: Float?,
    val indeterminate: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<LiveAction>,
    val stopwatch: Stopwatch? = null,
    val packageName: String = "",
    val customContent: RemoteViews? = null,
    val customParts: CustomActivityParts? = null,
    val accentColor: Int = 0xFF2C2C2E.toInt(),
    val isNavigation: Boolean = false,
    val turnDistance: String? = null,
    val sportsScore: SportsScore? = null,
    val screenCapture: ScreenCaptureKind? = null,
    val countdown: Countdown? = null,
    val progressStyle: LiveProgressStyle? = null,
    val isHotspot: Boolean = false,
    val isCall: Boolean = false,
    val callAvatar: Bitmap? = null,
    val transfer: TransferActivity? = null,
)

object LiveUpdateRepository {
    private val active = LinkedHashMap<String, LiveUpdate>()
    private val sportsStatusMemory = SportsStatusMemory()
    private val footballPlayHistory = FootballPlayHistory()
    private val listState = MutableStateFlow<List<LiveUpdate>>(emptyList())
    private val state = MutableStateFlow<LiveUpdate?>(null)
    val activities: StateFlow<List<LiveUpdate>> = listState.asStateFlow()
    val current: StateFlow<LiveUpdate?> = state.asStateFlow()

    private fun priority(u: LiveUpdate): Int = when {
        u.isCall -> -1
        u.isNavigation || u.packageName == "com.google.android.apps.maps" -> 0
        u.stopwatch == null -> 1
        u.stopwatch.startedAtElapsedMs != null -> 2
        else -> 3
    }

    fun put(key: String, update: LiveUpdate?) {
        if (update != null) {
            active[key] = update.sportsScore?.let {
                update.copy(sportsScore = footballPlayHistory.remember(key, sportsStatusMemory.remember(key, it)))
            } ?: update.also { sportsStatusMemory.forget(key); footballPlayHistory.forget(key) }
        } else {
            active.remove(key)
            sportsStatusMemory.forget(key)
            footballPlayHistory.forget(key)
        }
        val all = active.values.sortedBy { priority(it) }
        listState.value = all
        state.value = all.firstOrNull()
    }
    fun get(key: String): LiveUpdate? = active[key]
    fun clear() {
        sportsStatusMemory.clear()
        footballPlayHistory.clear()
        if (active.isEmpty()) return
        active.clear()
        listState.value = emptyList()
        state.value = null
    }
}

internal fun isEspnNotification(sbn: StatusBarNotification): Boolean {
    val pkg = sbn.packageName
    val channel = sbn.notification.channelId.orEmpty()
    return pkg.contains("espn", ignoreCase = true) ||
        channel.contains("espn", ignoreCase = true)
}

private fun isOngoingCallNotification(sbn: StatusBarNotification): Boolean {
    val n = sbn.notification
    val extras = n.extras
    val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
    return isOngoingCallNotification(
        callType = extras.getInt("android.callType", 0), ongoing = sbn.isOngoing,
        foregroundService = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        media = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || template.endsWith("MediaStyle"),
        autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        categoryCall = n.category == Notification.CATEGORY_CALL,
        callStyle = template.endsWith("CallStyle"),
        texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .map { extras.getCharSequence(it)?.toString().orEmpty() },
        actions = n.actions.orEmpty().filter { it.actionIntent != null && !it.isContextual && it.remoteInputs.isNullOrEmpty() }
            .map { callControl(it.title?.toString().orEmpty(), it.semanticAction) },
    )
}

private fun notificationTransfer(sbn: StatusBarNotification): TransferActivity? {
    val extras = sbn.notification.extras
    val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
    return detectTransferActivity(
        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT).map { extras.getCharSequence(it)?.toString().orEmpty() } +
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() },
        ongoing = sbn.isOngoing,
        progress = if (max > 0) (extras.getInt(Notification.EXTRA_PROGRESS).toFloat() / max).coerceIn(0f, 1f) else null,
        indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
    )
}

/** Check if this notification is an ongoing foreground task (timer, ride, order) or a final game score. */
fun isLiveUpdate(sbn: StatusBarNotification, sportsFinal: Boolean = false, metricStopwatch: Boolean = false): Boolean {
    val n = sbn.notification
    val isMedia = n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
        n.extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MediaStyle"
    val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
    val isAutoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0
    val isStopwatch = n.category == Notification.CATEGORY_STOPWATCH || metricStopwatch
    val callType = n.extras.getInt("android.callType", 0)
    // CallStyle state and ringing controls veto generic promotion/progress hints.
    if (isOngoingCallNotification(sbn)) return true
    if (n.category == Notification.CATEGORY_CALL || callType != 0 ||
        n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("CallStyle")) return false

    if (hasIncomingCallText(listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .map { n.extras.getCharSequence(it)?.toString().orEmpty() })) return false
    val callActions = n.actions.orEmpty().map { callControl(it.title?.toString().orEmpty()).kind }
    if (CallControlKind.ANSWER in callActions && CallControlKind.DECLINE in callActions) return false

    val transfer = notificationTransfer(sbn)
    if (transfer != null && !isSummary && !isMedia && !isAutoCancel) return true
    if (!isLiveActivityNotification(
            ongoing = sbn.isOngoing,
            groupSummary = isSummary,
            mediaSession = isMedia,
            autoCancel = isAutoCancel,
            pausedStopwatch = isStopwatch,
            sportsFinal = sportsFinal,
        )) {
        return false
    }

    if (sportsFinal) return true
    if (isHotspotNotification(sbn.packageName, listOf(
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        ))) return true
    // Custom layouts alone are not evidence of a live activity. Require a
    // recognized task category, promotion request, progress, or critical status.
    if (isStopwatch) return true
    if (isScreenCaptureNotification(sbn.packageName, n.channelId, listOf(
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
        ))) return true
    if (n.category == Notification.CATEGORY_NAVIGATION) return true
    if (n.category == Notification.CATEGORY_PROGRESS) return true
    if (n.category == Notification.CATEGORY_WORKOUT) return true
    val extras = n.extras
    if (extras.getBoolean("android.requestPromotedOngoing", false)) return true
    if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true
    return Build.VERSION.SDK_INT >= 36 && n.shortCriticalText?.isNotEmpty() == true
}

fun readLiveUpdate(context: Context, sbn: StatusBarNotification): LiveUpdate? {
    val n = sbn.notification
    val isMedia = n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
        n.extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MediaStyle"
    val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
    if (isMedia || isSummary) return null

    val custom = if (n.extras.getBoolean("android.contains.customView", false) ||
        n.contentView != null || n.bigContentView != null) {
        n.bigContentView ?: n.contentView
    } else null
    val sportsScore = custom?.let { readCustomSportsScore(context, sbn.packageName, it) }

    val isEspn = isEspnNotification(sbn)
    val wasActiveSports = LiveUpdateRepository.get(sbn.key)?.sportsScore != null
    val isFinal = isSportsFinalStatus(sportsScore?.status) ||
        isSportsFinalStatus(sportsScore?.secondaryStatus) ||
        isSportsFinalStatus(sportsScore?.gameStatus) ||
        isSportsFinalStatus(n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()) ||
        isSportsFinalStatus(n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()) ||
        isSportsFinalStatus(n.tickerText?.toString())

    val isSportsFinal = (isEspn || wasActiveSports) && sportsScore != null &&
        (isFinal || (wasActiveSports && !sbn.isOngoing))

    // Clock's MetricStyle stopwatch may omit CATEGORY_STOPWATCH, including when
    // paused with AUTO_CANCEL. Read its typed clock before applying eligibility.
    val isCall = isOngoingCallNotification(sbn)
    val clock = if (isCall) null else readNotificationClock(context, n)
    if (!isLiveUpdate(sbn, sportsFinal = isSportsFinal, metricStopwatch = clock?.stopwatch != null)) return null

    val extras = n.extras
    val pm = context.packageManager
    val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
        .getOrDefault(sbn.packageName)
    fun text(key: String): String = extras.getCharSequence(key)?.toString()?.trim().orEmpty()
    val isHotspot = isHotspotNotification(sbn.packageName, listOf(text(Notification.EXTRA_TITLE)))
    val callClock = if (isCall && extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) &&
        !extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) && n.`when` > 0L &&
        n.`when` <= System.currentTimeMillis()) {
        Stopwatch(android.os.SystemClock.elapsedRealtime() - (System.currentTimeMillis() - n.`when`))
    } else null
    val callPerson = if (isCall) androidx.core.os.BundleCompat.getParcelable(
        extras, Notification.EXTRA_CALL_PERSON, android.app.Person::class.java) else null
    val title = if (isCall) ongoingCallHeading(app, callPerson?.name?.toString(),
        text(Notification.EXTRA_TITLE), text(Notification.EXTRA_TEXT))
    else text(Notification.EXTRA_TITLE).ifBlank {
        sportsScore?.summaryTitle ?: clock?.label.orEmpty().ifBlank { app }
    }
    val content = liveUpdateText(title, text(Notification.EXTRA_TEXT), text(Notification.EXTRA_BIG_TEXT),
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
        text(Notification.EXTRA_SUB_TEXT))
    val body = content.body.ifBlank { sportsScore?.status.orEmpty() }
    val style = if (Build.VERSION.SDK_INT >= 36) runCatching {
        Notification.Builder.recoverBuilder(context, n).style as? Notification.ProgressStyle
    }.getOrNull() else null
    val max = if (Build.VERSION.SDK_INT >= 36 && style != null) style.progressMax else extras.getInt(Notification.EXTRA_PROGRESS_MAX)
    val value = if (Build.VERSION.SDK_INT >= 36 && style != null) style.progress else extras.getInt(Notification.EXTRA_PROGRESS)
    val indeterminate = if (Build.VERSION.SDK_INT >= 36 && style != null) style.isProgressIndeterminate else extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
    val transfer = if (!isCall) notificationTransfer(sbn) else null
    val shortText = if (Build.VERSION.SDK_INT >= 36) n.shortCriticalText.orEmpty() else ""
    val status = if (isHotspot) hotspotCompactStatus(listOf(body, content.detail)) else if (isCall) "In call" else shortText.ifBlank { sportsScore?.formattedScore.orEmpty() }
    val primaryIcon = sportsScore?.teamOneLogo ?: runCatching { n.smallIcon?.loadDrawable(context)?.toBitmap(96, 96) }.getOrNull()
    fun displayText(value: String) = if (transfer != null) stripTransferPercentage(value) else value
    return LiveUpdate(sbn.key, app, displayText(title).ifBlank { transfer?.label ?: app }, displayText(body), displayText(content.detail),
        displayText(status),
        primaryIcon,
        if (indeterminate) null else if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else transfer?.progress,
        indeterminate || (transfer != null && transfer.progress == null && !transfer.paused), n.contentIntent,
        n.actions.orEmpty().filter { !it.isContextual && it.remoteInputs.isNullOrEmpty() }
            .mapNotNull { action ->
                // Controls need a usable accessible label. Preserve the end-call
                // action even when a source publishes more than five controls.
                val label = action.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                action.actionIntent?.let { token -> LiveAction(label, token,
                    icon = if (isCall) runCatching { action.getIcon()?.loadDrawable(context)?.toBitmap(64, 64) }.getOrNull() else null,
                    isHangUp = isCall && (callControl(label, action.semanticAction).kind == CallControlKind.END ||
                        token == androidx.core.os.BundleCompat.getParcelable(extras, "android.hangUpIntent", PendingIntent::class.java)),
                    callControl = if (isCall) callControl(label, action.semanticAction) else null) }
            }.sortedByDescending { isCall && it.isHangUp }.take(if (isCall) 5 else 3), callClock ?: clock?.stopwatch, sbn.packageName,
        // Retain the app-authored timer and PendingIntent controls in custom layouts.
        // Do not infer a recording start from notification.when or scrape private fields.
        if (isCall) null else custom, if (isCall) null else custom?.let { readCustomActivityParts(context, it) },
        accentColor = if (isHotspot) 0xFF30B0FF.toInt() else if (isCall) 0xFF34C759.toInt() else liveActivityAccent(context, sbn.packageName, n.color),
        countdown = clock?.countdown,
        progressStyle = if (Build.VERSION.SDK_INT >= 36 && style != null) readLiveProgressStyle(context, style) else null,
        transfer = transfer,
        isHotspot = isHotspot,
        isCall = isCall,
        callAvatar = if (isCall) runCatching {
            (callPerson?.icon?.loadDrawable(context) ?: n.getLargeIcon()?.loadDrawable(context)
                ?: pm.getApplicationIcon(sbn.packageName)).toBitmap(128, 128)
        }.getOrNull() else null,
        isNavigation = n.category == Notification.CATEGORY_NAVIGATION,
        turnDistance = if (n.category == Notification.CATEGORY_NAVIGATION) nextTurnDistance(
            status, title, text(Notification.EXTRA_TEXT), text(Notification.EXTRA_BIG_TEXT),
        ) else null,
        sportsScore = sportsScore,
        screenCapture = if (isScreenCaptureNotification(sbn.packageName, n.channelId, listOf(title, body))) {
            if (sbn.packageName == "com.android.systemui" && n.channelId == "screen_record" ||
                listOf(title, body).any { it.contains("record", ignoreCase = true) }) ScreenCaptureKind.RECORDING
            else ScreenCaptureKind.SHARING
        } else null)
}
