package com.example.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
)

data class ParsedLyrics(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offsetMs: Long = 0,
    val lines: List<LyricLine> = emptyList()
)

object LrcParser {
    private val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3})|:(\d{2,3}))?\]""")
    private val tagRegex = Regex("""\[([a-zA-Z]+):([^\]]+)\]""")

    fun parse(lrcContent: String?, globalOffsetMs: Long = 0): ParsedLyrics {
        if (lrcContent.isNullOrBlank()) {
            return ParsedLyrics()
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lrcTagOffset: Long = 0
        val lines = mutableListOf<LyricLine>()

        val rawLines = lrcContent.lines()
        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Metadata tags e.g. [ti:Song Title], [ar:Artist], [al:Album], [offset:+500]
            val tagMatch = tagRegex.matchEntire(trimmed)
            if (tagMatch != null && !timeRegex.containsMatchIn(trimmed)) {
                val tagKey = tagMatch.groupValues[1].lowercase()
                val tagValue = tagMatch.groupValues[2].trim()
                when (tagKey) {
                    "ti" -> title = tagValue
                    "ar" -> artist = tagValue
                    "al" -> album = tagValue
                    "offset" -> lrcTagOffset = tagValue.toLongOrNull() ?: 0
                }
                continue
            }

            // Time tagged lines: could have multiple time stamps on same line e.g. [00:12.30][00:45.10]Chorus line
            val matches = timeRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val text = trimmed.substring(matches.last().range.last + 1).trim()
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val msGroup = match.groupValues[3].ifEmpty { match.groupValues[4] }
                    val ms = when (msGroup.length) {
                        2 -> (msGroup.toLongOrNull() ?: 0L) * 10
                        3 -> msGroup.toLongOrNull() ?: 0L
                        1 -> (msGroup.toLongOrNull() ?: 0L) * 100
                        else -> 0L
                    }
                    val totalMs = (min * 60 * 1000) + (sec * 1000) + ms + lrcTagOffset + globalOffsetMs
                    lines.add(LyricLine(timeMs = maxOf(0L, totalMs), text = text))
                }
            } else {
                // Plain text line without timestamp
                lines.add(LyricLine(timeMs = -1L, text = trimmed))
            }
        }

        val sortedLines = lines.sortedBy { if (it.timeMs >= 0) it.timeMs else Long.MAX_VALUE }
        return ParsedLyrics(
            title = title,
            artist = artist,
            album = album,
            offsetMs = lrcTagOffset + globalOffsetMs,
            lines = sortedLines
        )
    }

    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val timedLines = lines.filter { it.timeMs >= 0 }
        if (timedLines.isEmpty()) return 0

        var activeIndex = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (line.timeMs in 0..currentPositionMs) {
                activeIndex = i
            } else if (line.timeMs > currentPositionMs) {
                break
            }
        }
        return activeIndex
    }

    fun formatLrc(lines: List<LyricLine>): String {
        return buildString {
            for (line in lines) {
                if (line.timeMs >= 0) {
                    val totalSeconds = line.timeMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    val hundredths = (line.timeMs % 1000) / 10
                    append(String.format("[%02d:%02d.%02d] %s\n", minutes, seconds, hundredths, line.text))
                } else {
                    append(line.text).append("\n")
                }
            }
        }
    }

    /**
     * Converts plain text lyrics without timestamps into synced LRC format
     * by distributing timestamps evenly across the song's duration.
     */
    fun convertPlainTextToSyncedLrc(plainText: String, totalDurationMs: Long): String {
        val nonBlankLines = plainText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[ti:") && !it.startsWith("[ar:") && !it.startsWith("[al:") }

        if (nonBlankLines.isEmpty()) return plainText

        val effectiveDuration = if (totalDurationMs > 10000) totalDurationMs else 180000L
        val startOffsetMs = 3000L // 3 seconds intro
        val endOffsetMs = 3000L   // 3 seconds outro
        val playableDuration = maxOf(5000L, effectiveDuration - startOffsetMs - endOffsetMs)
        val intervalMs = playableDuration / nonBlankLines.size

        return buildString {
            nonBlankLines.forEachIndexed { index, line ->
                val timeMs = startOffsetMs + (index * intervalMs)
                val totalSeconds = timeMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val hundredths = (timeMs % 1000) / 10
                append(String.format("[%02d:%02d.%02d] %s\n", minutes, seconds, hundredths, line))
            }
        }
    }
}
