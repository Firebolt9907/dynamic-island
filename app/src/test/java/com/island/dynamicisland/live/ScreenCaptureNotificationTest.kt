package com.island.dynamicisland.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureNotificationTest {
    @Test fun recognizesSystemRecordingWithoutLocalizedText() {
        assertTrue(isScreenCaptureNotification("com.android.systemui", "screen_record", emptyList()))
        assertFalse(isScreenCaptureNotification("other.app", "screen_record", emptyList()))
    }

    @Test fun recognizesActiveRecordingAndSharing() {
        listOf("Screen recording", "Recording screen", "Screen sharing", "Sharing your screen",
            "Meet is sharing your screen", "DroidMirror is capturing your screen").forEach {
            assertTrue(it, isScreenCaptureNotification("other.app", "service", listOf(it)))
        }
    }

    @Test fun rejectsInvitationsAndCompletedCaptures() {
        listOf("Screen recording saved", "Start screen sharing", "Tap to record your screen",
            "Screen sharing ended", "Not sharing your screen").forEach {
            assertFalse(it, isScreenCaptureNotification("other.app", "service", listOf(it)))
        }
    }
}
