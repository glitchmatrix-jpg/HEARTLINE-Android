package com.heartline.app

import com.heartline.app.lyrics.MetadataNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataNormalizerTest {
    @Test fun removesVideoNoise() {
        assertEquals("birds of a feather", MetadataNormalizer.clean("BIRDS OF A FEATHER (Official Music Video)"))
    }

    @Test fun identicalTitlesScorePerfectly() {
        assertTrue(MetadataNormalizer.similarity("vampire", "Vampire") > .99)
    }

    @Test fun fingerprintIsStableAndCollisionResistantLength() {
        val first = MetadataNormalizer.fingerprint("Song", "Artist", "Album", 180000)
        val second = MetadataNormalizer.fingerprint("Song", "Artist", "Album", 180000)
        assertEquals(first, second)
        assertEquals(32, first.length)
    }
}
