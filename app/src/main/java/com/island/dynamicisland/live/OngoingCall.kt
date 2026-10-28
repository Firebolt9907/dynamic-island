package com.island.dynamicisland.live

import java.util.Locale

/** Public CallStyle types: 1 incoming, 2 ongoing, 3 screening. */
internal fun isOngoingCall(callType: Int, ongoing: Boolean, summary: Boolean, media: Boolean): Boolean =
    callType == 2 && ongoing && !summary && !media

enum class CallControlKind { END, MUTE, DEAFEN, SPEAKER, VIDEO, HOLD, ANSWER, DECLINE, OTHER }
data class CallControl(val kind: CallControlKind, val active: Boolean = false)

private val callTextSeparators = Regex("[^\\p{L}\\p{N}]+")
private fun normalizedCallText(value: String): String = value.lowercase(Locale.ROOT)
    .replace(callTextSeparators, " ").trim()

/** Exact action phrases avoid interpreting 'mute notifications' as a microphone control.
 * Semantic mute/unmute hints are used only after the notification qualifies as a call.
 */
internal fun callControl(title: String, semanticAction: Int = 0): CallControl {
    val label = normalizedCallText(title)
    fun control(kind: CallControlKind, active: Boolean = false) = CallControl(kind, active)
    return when (label) {
        "hang up", "hangup", "end call", "end video call", "end voice call", "disconnect",
        "leave call", "leave meeting", "end meeting", "leave voice channel" -> control(CallControlKind.END)
        "mute", "mute microphone", "mute mic", "turn microphone off" -> control(CallControlKind.MUTE)
        "unmute", "unmute microphone", "unmute mic", "turn microphone on" -> control(CallControlKind.MUTE, true)
        "deafen" -> control(CallControlKind.DEAFEN)
        "undeafen" -> control(CallControlKind.DEAFEN, true)
        "speaker", "speakerphone", "turn speaker on", "speaker on" -> control(CallControlKind.SPEAKER)
        "earpiece", "turn speaker off", "speaker off" -> control(CallControlKind.SPEAKER, true)
        "stop video", "turn camera off", "camera off" -> control(CallControlKind.VIDEO)
        "start video", "turn camera on", "camera on" -> control(CallControlKind.VIDEO, true)
        "hold", "hold call" -> control(CallControlKind.HOLD)
        "unhold", "resume call" -> control(CallControlKind.HOLD, true)
        "answer", "answer call", "accept", "accept call", "join call" -> control(CallControlKind.ANSWER)
        "decline", "decline call", "reject", "reject call" -> control(CallControlKind.DECLINE)
        else -> when (semanticAction) {
            6 -> control(CallControlKind.MUTE) // Notification.Action.SEMANTIC_ACTION_MUTE
            7 -> control(CallControlKind.MUTE, true) // SEMANTIC_ACTION_UNMUTE
            else -> control(CallControlKind.OTHER)
        }
    }
}

private val incomingCallText = Regex("\\b(incoming (voice |video |audio )?call|incoming call screening|screening call|is calling|calling you|ringing|missed call)\\b")
internal fun hasIncomingCallText(texts: List<String>): Boolean =
    texts.any { incomingCallText.containsMatchIn(normalizedCallText(it)) }

private val connectedCallText = Regex("\\b((voice|audio|video) connected|connected to (voice|call)|call (in progress|ongoing|on hold)|ongoing (voice |video |audio )?call|in (a )?(voice |video |audio )?call|tap to return to (the )?call)\\b")

/** No package names or channel IDs. Legacy detection requires both an end action
 * and independent call evidence. Explicit CallStyle state always takes priority.
 * Text/action fallbacks currently recognize English; unknown labels stay untouched.
 */
internal fun isOngoingCallNotification(callType: Int = 0, ongoing: Boolean, foregroundService: Boolean = false,
    summary: Boolean = false, media: Boolean = false, autoCancel: Boolean = false,
    categoryCall: Boolean = false, callStyle: Boolean = false, texts: List<String> = emptyList(),
    actions: List<CallControl> = emptyList()): Boolean {
    if (callType != 0) return isOngoingCall(callType, ongoing, summary, media)
    if (callStyle || !ongoing || summary || media || autoCancel || (!foregroundService && !categoryCall)) return false
    val text = texts.joinToString(" ") { normalizedCallText(it) }
    if (incomingCallText.containsMatchIn(text) || actions.any {
        it.kind == CallControlKind.ANSWER || it.kind == CallControlKind.DECLINE
    }) return false
    if (actions.none { it.kind == CallControlKind.END }) return false
    return categoryCall || connectedCallText.containsMatchIn(text) || actions.any {
        it.kind == CallControlKind.MUTE || it.kind == CallControlKind.DEAFEN || it.kind == CallControlKind.HOLD
    }
}

/** Prefer a contact/meeting name over boilerplate, including generic CallStyle Person names. */
internal fun ongoingCallHeading(appName: String, personName: String?, title: String?, text: String? = null): String {
    val generic = setOf("your video call", "your call", "video call", "audio call", "ongoing call",
        "ongoing video call", "ongoing audio call", "call ongoing", "call in progress", "in call", "voice connected")
    return sequenceOf(personName, title, text).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull {
            val normalized = normalizedCallText(it)
            normalized !in generic && !connectedCallText.containsMatchIn(normalized) &&
                !normalized.startsWith("tap to ")
        } ?: appName
}
