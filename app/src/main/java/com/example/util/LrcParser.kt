package com.example.util

import java.util.regex.Pattern

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val lrcPattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)")

    fun parse(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val matcher = lrcPattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                try {
                    val minutes = matcher.group(1)?.toLong() ?: 0L
                    val seconds = matcher.group(2)?.toLong() ?: 0L
                    val centiseconds = matcher.group(3)?.toLong() ?: 0L
                    val text = matcher.group(4)?.trim() ?: ""

                    val timeMs = (minutes * 60 + seconds) * 1000 + centiseconds * 10
                    lines.add(LrcLine(timeMs, text))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Finds the index of the line that should be displayed at [positionMs].
     * Uses a binary search approach (timestamp <= positionMs).
     */
    fun findCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines.first().timeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = lines[mid].timeMs

            if (midTime <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
