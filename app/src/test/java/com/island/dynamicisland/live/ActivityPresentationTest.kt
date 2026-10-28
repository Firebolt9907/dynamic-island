package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class ActivityPresentationTest {
    private fun activity(title: String, text: String = "") = LiveUpdate(
        "task", "Recorder", title, text, "", "", null, null, false, null, emptyList())

    @Test fun usefulTaskTitleLeadsInsteadOfTheSourceName() {
        assertEquals("Recording paused", activityHeadline(activity("Recording paused", "Tap to resume")))
        assertEquals("Recording interview", activityHeadline(activity("Recorder", "Recording interview\n00:12")))
        assertEquals("Recorder", activityHeadline(activity("", "")))
    }

    @Test fun cameraClearanceUsesMeasuredBoundsWithoutDoublePadding() {
        assertEquals(19f, activityCameraInset(31f, 12f), 0f)
        assertEquals(0f, activityCameraInset(8f, 12f), 0f)
    }

    @Test fun deliveryStageSynonymsStayQuietButArrivalExpands() {
        val tracker = SignificantLiveUpdateTracker()
        tracker.changedActivity(listOf(activity("Your order is on the way", "Arrives in 5 minutes")))
        assertNull(tracker.changedActivity(listOf(activity("Your order is en route", "Arrives in 4 minutes"))))
        assertEquals("task", tracker.changedActivity(listOf(activity("Your order has arrived"))))
    }

    @Test fun downloadTelemetryStaysQuietButCompletionAndPauseExpand() {
        val tracker = SignificantLiveUpdateTracker()
        val download = activity("archive.zip", "120 MB of 500 MB").copy(progress = .24f)
        tracker.changedActivity(listOf(download))
        assertNull(tracker.changedActivity(listOf(download.copy(text = "200 MB of 500 MB", progress = .4f))))
        assertEquals("task", tracker.changedActivity(listOf(download.copy(text = "Paused", progress = .4f))))
        assertEquals("task", tracker.changedActivity(listOf(download.copy(progress = 1f))))
        assertNull(tracker.changedActivity(listOf(download.copy(progress = 1f))))
    }

    @Test fun supportingDetailsDoNotReopenAnOtherwiseUnchangedActivity() {
        val tracker = SignificantLiveUpdateTracker()
        val update = activity("Sync task", "Connected").copy(details = "120 files scanned")
        tracker.changedActivity(listOf(update))
        assertNull(tracker.changedActivity(listOf(update.copy(details = "140 files scanned"))))
        assertEquals("task", tracker.changedActivity(listOf(update.copy(title = "Sync unavailable"))))
    }

    @Test fun unknownOrNegatedStagesAreNotInvented() {
        assertNull(activityStage(activity("Not completed")))
        assertNull(activityStage(activity("Order", "Will be ready for pickup soon")))
        assertEquals("preparing", activityStage(activity("Preparing your order", "Ready for pickup notification enabled")))
        assertNull(activityStage(activity("Synchronisation en cours")))
        assertEquals("failed", activityStage(activity("Upload failed", "Uploading file")))
    }
}
