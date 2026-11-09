package com.island.dynamicisland.lyrics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsDiskCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun persistsRawTimingAcrossCacheInstancesAndKeepsItFor180Days() {
        val directory = temporary.newFolder()
        val now = System.currentTimeMillis()
        val entry = LyricsDiskCache.Entry("lrc", "[00:01.00]Hello\n[00:02.00]World", now)
        LyricsDiskCache(directory).write("song", entry)
        val loaded = LyricsDiskCache(directory).read("song")!!
        assertEquals(entry, loaded)
        assertTrue(loaded.isFresh(now + 179L * 24 * 60 * 60 * 1000))
        assertFalse(loaded.isFresh(now + LyricsDiskCache.RETENTION_MS))
        // Expiry requests a refresh; it doesn't remove the offline fallback.
        assertNotNull(LyricsDiskCache(directory).read("song"))
    }

    @Test fun evictsByBytesAndLeastRecentUseRatherThanSongCount() {
        val directory = temporary.newFolder()
        val entry = LyricsDiskCache.Entry("ttml", "x".repeat(100), 1L)
        val cache = LyricsDiskCache(directory)
        cache.write("old", entry)
        java.io.File(directory, "old.json").setLastModified(1L)
        val oneEntryBytes = java.io.File(directory, "old.json").length()
        LyricsDiskCache(directory, oneEntryBytes + 1).write("new", entry)
        assertNull(cache.read("old"))
        assertEquals(entry, cache.read("new"))
    }

    @Test fun corruptEntryDoesNotPreventOtherCacheReads() {
        val directory = temporary.newFolder()
        java.io.File(directory, "bad.json").writeText("broken")
        val cache = LyricsDiskCache(directory)
        assertNull(cache.read("bad"))
        val entry = LyricsDiskCache.Entry("lrc", "[00:01]Hello", 1L)
        cache.write("bad", entry)
        assertEquals(entry, cache.read("bad"))
    }
}
