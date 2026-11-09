package com.island.dynamicisland.lyrics

import org.json.JSONObject
import java.io.File

/** Raw documents preserve both line and word timing across process restarts. */
internal class LyricsDiskCache(private val directory: File, private val maxBytes: Long = 32L * 1024 * 1024) {
    data class Entry(val format: String, val raw: String, val fetchedAt: Long) {
        fun isFresh(now: Long) = now - fetchedAt in 0 until RETENTION_MS
    }

    fun read(key: String): Entry? = runCatching {
        val file = File(directory, "$key.json")
        val json = JSONObject(file.readText())
        Entry(json.getString("format"), json.getString("raw"), json.optLong("fetchedAt", file.lastModified()))
            .also { file.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    fun write(key: String, entry: Entry) {
        directory.mkdirs()
        val file = File(directory, "$key.json")
        val temporary = File(directory, "$key.tmp")
        try {
            temporary.writeText(JSONObject().put("format", entry.format).put("raw", entry.raw)
                .put("fetchedAt", entry.fetchedAt).toString())
            check(temporary.renameTo(file))
        } finally { temporary.delete() }
        var bytes = 0L
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.forEach { cached ->
                bytes += cached.length()
                if (bytes > maxBytes) cached.delete()
            }
    }

    companion object {
        const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000
    }
}
