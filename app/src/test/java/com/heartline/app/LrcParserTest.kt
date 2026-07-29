package com.heartline.app

import com.heartline.app.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test fun parsesAndSortsMultipleTimestamps() {
        val lines = LrcParser.parse("[00:02.50][00:04.500]hello\n[00:01.00]first")
        assertEquals(listOf(1000L, 2500L, 4500L), lines.map { it.timestampMs })
    }

    @Test fun findsCurrentLineUsingBinarySearch() {
        val lines = LrcParser.parse("[00:01.00]one\n[00:02.00]two\n[00:03.00]three")
        assertEquals(1, LrcParser.currentIndex(lines, 2500))
    }

    @Test fun appliesOffsetEvenWhenTagAppearsAfterLyrics() {
        val lines = LrcParser.parse("[00:01.00]one\n[offset:+500]")
        assertEquals(1500L, lines.single().timestampMs)
    }

    @Test fun rejectsInvalidSecondValues() {
        val lines = LrcParser.parse("[00:99.00]invalid\n[01:00.00]valid")
        assertEquals(listOf(60000L), lines.map { it.timestampMs })
    }
}
