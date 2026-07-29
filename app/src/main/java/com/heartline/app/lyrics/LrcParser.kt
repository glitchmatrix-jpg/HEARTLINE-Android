package com.heartline.app.lyrics

import com.heartline.app.data.LyricLine

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val metadata = Regex("^\\[[a-zA-Z]+:.*]$")
    private val offsetTag = Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<LyricLine> {
        val lines = raw.lineSequence().toList()
        val fileOffset = lines.firstNotNullOfOrNull { line ->
            offsetTag.matchEntire(line.trim())?.groupValues?.get(1)?.toLongOrNull()
        } ?: 0L

        val result = mutableListOf<LyricLine>()
        lines.forEach { original ->
            val line = original.trimEnd()
            if (offsetTag.matches(line.trim()) || metadata.matches(line)) return@forEach
            val stamps = timestamp.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val text = line.replace(timestamp, "").trim().ifEmpty { "♪" }
            stamps.forEach stampLoop@ { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                if (seconds > 59) return@stampLoop
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100
                    2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.take(3).padEnd(3, '0').toLong()
                }
                val time = (minutes * 60_000 + seconds * 1_000 + millis + fileOffset)
                    .coerceAtLeast(0)
                result += LyricLine(time, text)
            }
        }
        return result
            .distinctBy { it.timestampMs to it.text }
            .sortedBy { it.timestampMs }
    }

    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
