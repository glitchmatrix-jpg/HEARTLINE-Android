package com.heartline.app.lyrics

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object MetadataNormalizer {
    private val noise = listOf(
        Regex("\\((official|lyrics?|audio|video|visuali[sz]er).*?\\)", RegexOption.IGNORE_CASE),
        Regex("\\[(official|lyrics?|audio|video|visuali[sz]er).*?]", RegexOption.IGNORE_CASE),
        Regex("\\((?:19|20)\\d{2} remaster(?:ed)?\\)", RegexOption.IGNORE_CASE),
        Regex("\\b(?:official music video|official audio|lyrics? video)\\b", RegexOption.IGNORE_CASE)
    )

    fun clean(value: String): String {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('’', '\'')
            .replace('–', '-')
            .replace('—', '-')
        noise.forEach { normalized = normalized.replace(it, " ") }
        return normalized.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Stable SHA-256-derived identifier; avoids the collision risk of a 32-bit hash. */
    fun fingerprint(title: String, artist: String, album: String?, durationMs: Long): String {
        val durationBucket = if (durationMs > 0) durationMs / 2_000 else 0
        val raw = "${clean(title)}|${clean(artist)}|${clean(album.orEmpty())}|$durationBucket"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    fun similarity(a: String, b: String): Double {
        val left = clean(a)
        val right = clean(b)
        if (left == right) return 1.0
        if (left.isBlank() || right.isBlank()) return 0.0
        val x = left.split(' ').toSet()
        val y = right.split(' ').toSet()
        val intersection = x.intersect(y).size.toDouble()
        val jaccard = intersection / x.union(y).size.coerceAtLeast(1)
        val containment = intersection / minOf(x.size, y.size).coerceAtLeast(1)
        return 0.55 * jaccard + 0.45 * containment
    }
}
