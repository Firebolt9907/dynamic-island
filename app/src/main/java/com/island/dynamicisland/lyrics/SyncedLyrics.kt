package com.island.dynamicisland.lyrics

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.Normalizer
import java.util.Locale

internal data class LyricSegment(val startMs: Long, val endMs: Long, val from: Int, val until: Int)
internal data class LyricLine(val startMs: Long, val endMs: Long, val text: String, val segments: List<LyricSegment> = emptyList())
internal data class SyncedLyrics(val lines: List<LyricLine>, val source: String, val durationMs: Long? = null) {
    val wordSynced get() = lines.any { it.segments.isNotEmpty() }
    fun lineAt(positionMs: Long): Int = lines.indexOfLast { positionMs >= it.startMs }
        .takeIf { it >= 0 && positionMs < lines[it].endMs } ?: -1
    val boundaries: List<Long> by lazy {
        lines.flatMap { line -> listOf(line.startMs, line.endMs) + line.segments.flatMap { listOf(it.startMs, it.endMs) } }.distinct().sorted()
    }
}

internal object LyricsParser {
    internal fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun timeMs(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val seconds = when {
                value.endsWith("ms") -> value.dropLast(2).toDouble() / 1000
                value.endsWith("s") -> value.dropLast(1).toDouble()
                else -> value.split(':').fold(0.0) { total, part -> total * 60 + part.toDouble() }
            }
            require(seconds.isFinite() && seconds >= 0 && seconds <= 24 * 3600)
            (seconds * 1000).toLong()
        }.getOrNull()
    }

    fun lrc(raw: String, durationMs: Long): SyncedLyrics? {
        val timestamp = Regex("\\[(\\d+:\\d+(?:\\.\\d+)?)\\]")
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val rows = mutableListOf<Pair<Long, String>>()
        raw.lineSequence().forEach { row ->
            val stamps = timestamp.findAll(row).toList()
            val text = row.substring(stamps.lastOrNull()?.range?.last?.plus(1) ?: row.length).trim()
            stamps.forEach { match -> timeMs(match.groupValues[1])?.let { rows += (it - offset).coerceAtLeast(0) to text } }
        }
        val sorted = rows.distinct().sortedBy { it.first }
        val lines = sorted.mapIndexedNotNull { index, (start, text) ->
            val end = sorted.drop(index + 1).firstOrNull { it.first > start }?.first ?: durationMs
            if (text.isBlank() || end <= start) null else enhancedLine(start, end, text, offset)
        }
        return lines.takeIf { it.isNotEmpty() }?.let { SyncedLyrics(it, "LRCLIB", durationMs) }
    }

    private fun enhancedLine(start: Long, end: Long, raw: String, offset: Long): LyricLine {
        val markers = Regex("<(\\d+:\\d+(?:\\.\\d+)?)>").findAll(raw).toList()
        if (markers.isEmpty()) return LyricLine(start, end, raw)
        val text = StringBuilder(raw.substring(0, markers.first().range.first))
        val segments = mutableListOf<LyricSegment>()
        markers.forEachIndexed { index, marker ->
            val next = markers.getOrNull(index + 1)
            val from = text.length
            text.append(raw.substring(marker.range.last + 1, next?.range?.first ?: raw.length))
            val begin = timeMs(marker.groupValues[1])?.minus(offset)
            val finish = next?.let { timeMs(it.groupValues[1])?.minus(offset) } ?: end
            if (begin != null && finish > begin && text.length > from)
                segments += LyricSegment(begin.coerceAtLeast(0), finish, from, text.length)
        }
        return LyricLine(start, end, text.toString(), segments)
    }

    fun ttml(raw: String): SyncedLyrics? {
        if (raw.contains("<!DOCTYPE", ignoreCase = true)) return null
        return runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(raw))
            }
            data class Span(val depth: Int, val from: Int, val start: Long?, val end: Long?)
            val spans = mutableListOf<Span>()
            val lines = mutableListOf<LyricLine>()
            val segments = mutableListOf<LyricSegment>()
            val text = StringBuilder()
            var pDepth = -1
            var start: Long? = null
            var end: Long? = null
            var duration: Long? = null
            var ignoreDepth = -1
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.DOCDECL -> error("DTD is not supported")
                    XmlPullParser.START_TAG -> {
                        val role = (0 until parser.attributeCount).firstOrNull { parser.getAttributeName(it) == "role" }
                            ?.let { parser.getAttributeValue(it) }.orEmpty()
                        if (role == "x-translation" || role == "x-roman" || role == "x-bg") {
                            if (ignoreDepth < 0) ignoreDepth = parser.depth
                        }
                        if (parser.name == "body") duration = timeMs(parser.getAttributeValue(null, "dur"))
                        if (parser.name == "p" && pDepth < 0) {
                            pDepth = parser.depth
                            start = timeMs(parser.getAttributeValue(null, "begin"))
                            end = timeMs(parser.getAttributeValue(null, "end"))
                            text.clear(); segments.clear(); spans.clear()
                        } else if (pDepth >= 0 && ignoreDepth < 0 && parser.name == "span") {
                            spans += Span(parser.depth, text.length, timeMs(parser.getAttributeValue(null, "begin")), timeMs(parser.getAttributeValue(null, "end")))
                        } else if (pDepth >= 0 && ignoreDepth < 0 && parser.name == "br") text.append('\n')
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                        if (pDepth >= 0 && ignoreDepth < 0) text.append(parser.text.orEmpty())
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.depth == ignoreDepth) ignoreDepth = -1
                        if (parser.name == "span" && spans.lastOrNull()?.depth == parser.depth) {
                            val span = spans.removeAt(spans.lastIndex)
                            if (span.start != null && span.end != null && span.end > span.start && text.length > span.from) {
                                // A parent timing span does not replace finer child timings.
                                if (segments.none { it.from >= span.from && it.until <= text.length })
                                    segments += LyricSegment(span.start, span.end, span.from, text.length)
                            }
                        }
                        if (parser.name == "p" && parser.depth == pDepth) {
                            val begin = start ?: segments.minOfOrNull { it.startMs }
                            val finish = end ?: segments.maxOfOrNull { it.endMs }
                            if (begin != null && finish != null && finish > begin && text.isNotBlank()) {
                                lines += LyricLine(begin, finish, text.toString(), segments.toList())
                            }
                            pDepth = -1
                        }
                    }
                }
                parser.nextToken()
            }
            lines.sortedBy { it.startMs }.takeIf { it.isNotEmpty() }?.let { SyncedLyrics(it, "AMLL TTML DB", duration) }
        }.getOrNull()
    }
}
