package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class TransferActivityTest {
    @Test fun cleanDuplicateProgressText() {
        assertEquals("Downloading", stripTransferPercentage("Downloading · 99%"))
        assertEquals("Receiving from Mac", stripTransferPercentage("Receiving from Mac · 29%"))
        assertEquals("Uploading photos", stripTransferPercentage("Uploading photos (42%)"))
        assertEquals("", stripTransferPercentage("100%"))
        assertEquals("99%.zip", stripTransferPercentage("99%.zip"))
        assertEquals("Uploading 50%off.pdf", stripTransferPercentage("Uploading 50%off.pdf"))
        assertTrue(transferStatusIsRedundant("Downloading", "Downloading", "Clipboard file from Mac"))
        assertTrue(transferStatusIsRedundant("Uploading", "Photos", "Uploading 3 files"))
        assertFalse(transferStatusIsRedundant("Paused", "Downloading", "Clipboard file from Mac"))
    }

    @Test fun directionsAcrossSources() {
        listOf(
            "Uploading 3 photos" to TransferDirection.UPLOAD,
            "Sending file archive.zip" to TransferDirection.UPLOAD,
            "Backing up photos" to TransferDirection.UPLOAD,
            "Downloading update" to TransferDirection.DOWNLOAD,
            "Receiving from Mac · 29%" to TransferDirection.DOWNLOAD,
            "Syncing clipboard file" to TransferDirection.SYNC,
            "Copying 4 files" to TransferDirection.SYNC,
        ).forEach { (text, direction) ->
            assertEquals(text, direction, detectTransferActivity(listOf(text), true)?.direction)
        }
    }
    @Test fun progressAndPausedStates() {
        assertEquals(.29f, detectTransferActivity(listOf("Receiving from Mac · 29%"), true)!!.progress!!, .001f)
        assertEquals(.7f, detectTransferActivity(listOf("Uploading · 29%"), true, .7f)!!.progress!!, .001f)
        assertTrue(detectTransferActivity(listOf("Download paused"), true, .4f)!!.paused)
        assertNotNull(detectTransferActivity(listOf("Uploading"), false, indeterminate = true))
        assertNull(detectTransferActivity(listOf("Downloading 30%"), true, .3f, indeterminate = true)!!.progress)
        assertNull(detectTransferActivity(listOf("Uploading · 999%"), true)!!.progress)
    }
    @Test fun rejectUnrelatedAndFinishedNotices() {
        listOf("Upload complete", "Download failed", "Download finished", "Transfer cancelled",
            "Download our app", "Upload photos", "Syncing messages", "Sending you a message",
            "Battery 29%").forEach { text ->
            // Ordinary chat text has no ongoing/progress evidence.
            assertNull(text, detectTransferActivity(listOf(text), false))
        }
        assertNull(detectTransferActivity(listOf("Download complete"), true, 1f))
        assertNull(detectTransferActivity(listOf("Syncing messages"), true))
        assertNull(detectTransferActivity(listOf("Sending you a message"), true))
        assertNull(detectTransferActivity(listOf("Your ride is arriving"), true, .5f))
    }
}
