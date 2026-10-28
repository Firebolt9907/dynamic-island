package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class OngoingCallTest {
    @Test fun onlyExplicitOngoingCallsAppear() {
        assertTrue(isOngoingCall(2, true, false, false))
        for (type in listOf(0, 1, 3, 4)) assertFalse(isOngoingCall(type, true, false, false))
    }
    @Test fun endedSummaryAndMediaCallsAreExcluded() {
        assertFalse(isOngoingCall(2, false, false, false))
        assertFalse(isOngoingCall(2, true, true, false))
        assertFalse(isOngoingCall(2, true, false, true))
    }
    @Test fun meetBoilerplateFallsBackToSourceApp() {
        assertEquals("Meet", ongoingCallHeading("Meet", "Your video call", "Your video call"))
        assertEquals("Meet", ongoingCallHeading("Meet", null, "  YOUR VIDEO CALL  "))
    }
    @Test fun prefersContactAndPreservesMeetingTitle() {
        assertEquals("Alex", ongoingCallHeading("Meet", "Alex", "Your video call"))
        assertEquals("Project sync", ongoingCallHeading("Meet", "Your video call", "Project sync"))
        assertEquals("Meet", ongoingCallHeading("Meet", " ", null))
    }
    private fun legacy(text: String = "", labels: List<String> = listOf("Disconnect", "Unmute", "Deafen"),
                       ongoing: Boolean = true, foreground: Boolean = true, category: Boolean = false,
                       summary: Boolean = false, media: Boolean = false, autoCancel: Boolean = false) =
        isOngoingCallNotification(ongoing = ongoing, foregroundService = foreground, categoryCall = category,
            summary = summary, media = media, autoCancel = autoCancel,
            texts = listOf(text), actions = labels.map { callControl(it) })

    @Test fun recognizesLegacyCallsWithoutPackageOrChannelRules() {
        assertTrue(legacy("Voice Connected — Tap to return to call"))
        assertTrue(legacy("Call in progress", listOf("Hang up")))
        assertTrue(legacy("Team sync", listOf("Leave meeting", "Mute microphone")))
        assertTrue(legacy("Alex", listOf("Speaker", "End call"), foreground = false, category = true))
        assertTrue(legacy("Call on hold", listOf("Resume call", "Hang up")))
    }
    @Test fun rejectsRingingScreeningAndAmbiguousNotifications() {
        assertFalse(legacy("Incoming video call", listOf("End call", "Mute")))
        assertFalse(legacy("Alex", listOf("Answer", "Decline", "End call"), category = true))
        assertFalse(legacy("Call screening", listOf("Answer call", "Hang up")))
        assertFalse(legacy("VPN connected", listOf("Disconnect")))
        assertFalse(legacy("Recording", listOf("Stop", "Mute")))
        assertFalse(legacy("New messages", listOf("Reply", "Mute notifications")))
        assertFalse(legacy("Uploading", listOf("Cancel", "Pause")))
        assertFalse(legacy("Voice connected", listOf("Open app")))
        assertFalse(legacy("Missed call", listOf("Call back", "Delete")))
    }
    @Test fun rejectsInactiveAndNonLiveNotifications() {
        assertFalse(legacy(ongoing = false))
        assertFalse(legacy(foreground = false))
        assertFalse(legacy(summary = true))
        assertFalse(legacy(media = true))
        assertFalse(legacy(autoCancel = true))
    }
    @Test fun explicitCallStyleStateWinsOverLegacyHints() {
        for (type in listOf(1, 3, 4)) {
            assertFalse(isOngoingCallNotification(callType = type, ongoing = true, foregroundService = true,
                texts = listOf("Call in progress"), actions = listOf(callControl("End call"), callControl("Mute"))))
        }
        assertFalse(isOngoingCallNotification(ongoing = true, foregroundService = true, callStyle = true,
            actions = listOf(callControl("End call"), callControl("Mute"))))
        assertTrue(isOngoingCallNotification(callType = 2, ongoing = true))
    }
    @Test fun controlsAreIndependentOfOrderAndKeepToggleState() {
        assertEquals(CallControl(CallControlKind.END), callControl("  HANG-UP  "))
        assertEquals(CallControl(CallControlKind.MUTE, true), callControl("Unmute microphone"))
        assertEquals(CallControl(CallControlKind.DEAFEN, true), callControl("Undeafen"))
        assertEquals(CallControl(CallControlKind.SPEAKER, true), callControl("Earpiece"))
        assertEquals(CallControl(CallControlKind.MUTE, true), callControl("Localized label", 7))
        assertEquals(CallControlKind.OTHER, callControl("Mute notifications").kind)
        assertTrue(legacy(labels = listOf("Deafen", "Disconnect", "Unmute")))
    }
    @Test fun legacyBoilerplateUsesChannelOrMeetingName() {
        assertEquals("Study Room 2", ongoingCallHeading("Chat", null,
            "Voice Connected — Tap to return to call", "Study Room 2"))
        assertEquals("Chat", ongoingCallHeading("Chat", null, "Call in progress", "Tap to return to call"))
        assertEquals("Alex", ongoingCallHeading("Chat", "Alex", "Call in progress", "Tap to return to call"))
    }
}
