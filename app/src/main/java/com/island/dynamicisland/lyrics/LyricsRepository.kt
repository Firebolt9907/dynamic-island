package com.island.dynamicisland.lyrics

import android.content.Context
import com.island.dynamicisland.media.NowPlaying
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

internal data class LyricsTrack(val title: String, val artist: String, val album: String, val durationMs: Long) {
    val key: String get() = MessageDigest.getInstance("SHA-256").digest(
        listOf(title, artist, album, durationMs.toString()).joinToString("\u0000").toByteArray()
    ).joinToString("") { "%02x".format(it) }
    companion object {
        fun from(np: NowPlaying?) = np?.takeIf { it.title.isNotBlank() && it.title != "Unknown" && it.artist.isNotBlank() && it.durationMs > 0 }
            ?.let { LyricsTrack(it.title, it.artist, it.album, it.durationMs) }
    }
}

/** Fetch only for expanded music. Cache by recording metadata, never by a mutable session id. */
internal object LyricsRepository {
    private const val DB = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main"
    private const val DAY = 24 * 60 * 60 * 1000L
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Pair<Long, SyncedLyrics?>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, SyncedLyrics?>>?) = size > 64
    }
    private data class Document(val format: String, val raw: String) {
        fun parse(duration: Long) = if (format == "ttml") LyricsParser.ttml(raw) else LyricsParser.lrc(raw, duration)
    }

    suspend fun lookup(context: Context, track: LyricsTrack): SyncedLyrics? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            cache[track.key]?.takeIf { it.second != null && now - it.first < 7 * DAY }?.let { return@withLock it.second }
            val dir = File(context.cacheDir, "synced-lyrics").apply { mkdirs() }
            val file = File(dir, track.key + ".json")
            val disk = runCatching {
                if (!file.exists()) null else {
                    val json = JSONObject(file.readText())
                    Document(json.getString("format"), json.getString("raw")).parse(track.durationMs)
                }
            }.getOrNull()
            if (disk != null && now - file.lastModified() < if (disk.wordSynced) 7 * DAY else DAY) {
                cache[track.key] = now to disk
                return@withLock disk
            }
            val document = coroutineScope {
                val lines = async { safely { lineLyrics(track) } }
                val words = async { safely { wordLyrics(dir, track) } }
                val wordResult = words.await()
                val lineResult = lines.await()
                wordResult?.takeIf { it.parse(track.durationMs)?.wordSynced == true } ?: lineResult ?: wordResult
            }
            ensureActive()
            val result = document?.parse(track.durationMs) ?: disk ?: cache[track.key]?.second
            if (result != null) cache[track.key] = now to result
            if (document != null && result != null) runCatching {
                file.writeText(JSONObject().put("format", document.format).put("raw", document.raw).toString())
                dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
                    ?.drop(64)?.forEach { it.delete() }
            }
            result
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (com.island.dynamicisland.BuildConfig.DEBUG) android.util.Log.d("IslandLyrics", "Lookup failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

    private suspend fun lineLyrics(track: LyricsTrack): Document? {
        fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
        val query = "track_name=${encode(track.title)}&artist_name=${encode(track.artist)}"
        fun document(row: JSONObject): Document? {
            if (!matchesLineRecording(track, row.optString("trackName"), row.optString("artistName"),
                    row.optString("albumName"), (row.optDouble("duration", 0.0) * 1000).toLong()) || row.optBoolean("instrumental")) return null
            return row.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                ?.let { Document("lrc", it) }?.takeIf { it.parse(track.durationMs) != null }
        }
        if (track.album.isNotBlank()) {
            val exact = safely {
                JSONObject(fetch("https://lrclib.net/api/get?$query&album_name=${encode(track.album)}&duration=${track.durationMs / 1000.0}", 1024 * 1024))
            }
            exact?.let { document(it) }?.let { return it }
        }
        // Exact catalog lookups can miss otherwise identical deluxe/standard editions.
        val results = JSONArray(fetch("https://lrclib.net/api/search?$query", 1024 * 1024))
        return (0 until results.length()).map { results.getJSONObject(it) }
            .sortedWith(compareBy<JSONObject> { if (same(track.album, it.optString("albumName"))) 0 else 1 }
                .thenBy { kotlin.math.abs(it.optDouble("duration", 0.0) * 1000 - track.durationMs) })
            .firstNotNullOfOrNull(::document)
    }

    internal fun matchesLineRecording(track: LyricsTrack, title: String, artist: String, album: String, durationMs: Long): Boolean =
        same(track.title, title) && same(track.artist, artist) && durationMs > 0 &&
            kotlin.math.abs(durationMs - track.durationMs) <= 2000 &&
            (track.album.isBlank() || same(track.album, album) || same(albumBase(track.album), albumBase(album)))

    private fun albumBase(album: String): String = album.replace(
        Regex("[\\[(]\\s*(?:deluxe(?:\\s+(?:edition|version))?|expanded(?:\\s+edition)?|standard(?:\\s+edition)?)\\s*[\\])]", RegexOption.IGNORE_CASE), "")

    private suspend fun wordLyrics(dir: File, track: LyricsTrack): Document? {
        val indexFile = File(dir, "amll-index.jsonl")
        val index = if (indexFile.exists() && System.currentTimeMillis() - indexFile.lastModified() < DAY) indexFile.readText()
            else safely { fetch("$DB/metadata/raw-lyrics-index.jsonl", 8 * 1024 * 1024) }?.also { value ->
                runCatching { indexFile.writeText(value) }
            } ?: indexFile.takeIf { it.exists() }?.readText() ?: return null
        val candidates = index.lineSequence().mapNotNull { row ->
            runCatching {
                val json = JSONObject(row)
                val metadata = json.getJSONArray("metadata")
                fun values(key: String): List<String> {
                    for (i in 0 until metadata.length()) {
                        val pair = metadata.getJSONArray(i)
                        if (pair.getString(0) == key) return pair.getJSONArray(1).let { a -> (0 until a.length()).map { a.getString(it) } }
                    }
                    return emptyList()
                }
                val artists = values("artists")
                if (values("musicName").none { same(track.title, it) } ||
                    (artists.none { same(track.artist, it) } && !same(track.artist, artists.joinToString(" "))) ||
                    (track.album.isNotBlank() && values("album").none { same(track.album, it) })) null
                else json.getString("rawLyricFile").takeIf { it.matches(Regex("[A-Za-z0-9_-]+\\.ttml")) }
            }.getOrNull()
        }.sortedByDescending { it.substringBefore('-').toLongOrNull() ?: 0 }.take(3).toList()
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val document = safely { Document("ttml", fetch("$DB/raw-lyrics/$candidate", 1024 * 1024)) } ?: continue
            val parsed = document.parse(track.durationMs) ?: continue
            // TTML body duration often stops at the final vocal, before the audio outro.
            if (!fitsRecording(parsed, track.durationMs)) continue
            if (parsed.wordSynced) return document
        }
        return null
    }

    internal fun fitsRecording(lyrics: SyncedLyrics, durationMs: Long): Boolean = durationMs > 0 &&
        (lyrics.durationMs == null || lyrics.durationMs <= durationMs + 2000) &&
        lyrics.lines.all { it.endMs <= durationMs + 2000 }

    private fun same(a: String, b: String) = LyricsParser.normalized(a) == LyricsParser.normalized(b)

    private suspend fun fetch(url: String, limit: Int): String {
        currentCoroutineContext().ensureActive()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "DynamicIsland/1.0 (Android; synced lyrics)")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} from ${URL(url).host}" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= limit)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
