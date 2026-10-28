package com.island.dynamicisland.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

internal data class AppleArtwork(val image: Bitmap?, val motionUrl: String?, val accentColor: Int? = null)

/** Public catalog lookup; no account credentials or music playback are involved. */
internal object AppleMusicArtwork {
    private const val MAX_RESPONSE = 4 * 1024 * 1024

    fun lookup(title: String, artist: String, album: String, durationMs: Long): AppleArtwork {
        if (title.isBlank() || artist.isBlank() || title == "Unknown") return AppleArtwork(null, null)
        val term = URLEncoder.encode("$artist $title $album", "UTF-8")
        val results = JSONObject(String(read("https://itunes.apple.com/search?term=$term&entity=song&limit=15&country=us"), Charsets.UTF_8))
            .getJSONArray("results")
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
        val match = candidates.firstOrNull {
            matches(title, artist, album, durationMs, it.optString("trackName"), it.optString("artistName"),
                it.optString("collectionName"), it.optLong("trackTimeMillis"))
        } ?: return AppleArtwork(null, null)
        val image = runCatching {
            val url = match.getString("artworkUrl100").replace("100x100bb", "300x300bb")
            val bytes = read(url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        val motion = runCatching {
            val id = match.getLong("collectionId")
            val html = String(read("https://music.apple.com/us/album/$id"), Charsets.UTF_8)
            motionUrl(html)
        }.getOrNull()
        return AppleArtwork(image, motion, image?.let(ArtworkColors::fromBitmap))
    }

    internal fun matches(title: String, artist: String, album: String, duration: Long,
                         candidateTitle: String, candidateArtist: String, candidateAlbum: String,
                         candidateDuration: Long): Boolean =
        normalize(title) == normalize(candidateTitle) && normalize(artist) == normalize(candidateArtist) &&
            (album.isBlank() || normalize(album) == normalize(candidateAlbum)) &&
            (duration <= 0 || candidateDuration <= 0 || kotlin.math.abs(duration - candidateDuration) < 5000)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    internal fun motionUrl(html: String): String? {
        val json = Regex("<script[^>]*id=[\"']serialized-server-data[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        // Only the album header owns cover motion. Never borrow a recommended album's video.
        val header = JSONObject(json).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("data")
            ?.optJSONArray("sections")?.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(0)
        return header?.optJSONObject("videoArtwork")?.optJSONObject("dictionary")
            ?.optJSONObject("motionDetailSquare")?.optString("video")
            ?.takeIf { it.isNotBlank() && trustedUrl(it) }
    }

    private fun trustedUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty()
        uri.scheme == "https" && (host == "music.apple.com" || host == "itunes.apple.com" ||
            host.endsWith(".itunes.apple.com") || host.endsWith(".mzstatic.com"))
    }.getOrDefault(false)

    private fun read(url: String): ByteArray {
        require(trustedUrl(url))
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (connection.responseCode in 300..399) {
                val target = URL(URL(url), connection.getHeaderField("Location")).toString()
                require(trustedUrl(target) && target != url)
                return readRedirect(target)
            }
            check(connection.responseCode == 200)
            return connection.inputStream.use { stream ->
                readBounded(stream)
            }
        } finally { connection.disconnect() }
    }

    private fun readRedirect(url: String): ByteArray {
        // Apple's numeric album URLs redirect to their canonical slug once.
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            check(connection.responseCode == 200)
            return connection.inputStream.use { readBounded(it) }
        } finally { connection.disconnect() }
    }
    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            check(output.size() + count <= MAX_RESPONSE)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

}
