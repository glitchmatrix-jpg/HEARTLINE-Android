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
    private val editionNoise = Regex(
        "\\s*(?:[-–—]|\\(|\\[)\\s*(?:deluxe(?: edition)?|expanded(?: edition)?|anniversary(?: edition)?|remaster(?:ed)?(?: \\d{4})?|single version|album version|radio edit|clean|explicit|bonus track|sped up|slowed(?: and reverb)?|live(?: at| from)?[^)\\]]*)\\s*[)\\]]?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val featureSuffix = Regex(
        "\\s*(?:\\(|\\[)?(?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]+(?:\\)|\\])?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val versionSuffix = Regex(
        "\\s*(?:[-–—]|\\(|\\[)\\s*(?:acoustic|instrumental|karaoke|demo|edit|mix|version|remix)[^)\\]]*[)\\]]?\\s*$",
        RegexOption.IGNORE_CASE
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

    fun titleVariants(value: String): List<String> = buildList {
        fun addUseful(candidate: String) {
            candidate.trim().takeIf(String::isNotBlank)?.let { if (none { old -> clean(old) == clean(it) }) add(it) }
        }
        addUseful(value)
        addUseful(value.replace(featureSuffix, ""))
        addUseful(value.replace(editionNoise, ""))
        addUseful(value.replace(versionSuffix, ""))
        addUseful(value.replace(featureSuffix, "").replace(editionNoise, "").replace(versionSuffix, ""))
        addUseful(clean(value))
    }

    fun artistVariants(value: String): List<String> = buildList {
        fun addUseful(candidate: String) {
            candidate.trim().takeIf(String::isNotBlank)?.let { if (none { old -> clean(old) == clean(it) }) add(it) }
        }
        addUseful(value)
        addUseful(value.substringBefore(',').substringBefore(" & ").substringBefore(" feat.").substringBefore(" ft."))
        addUseful(clean(value))
    }

    fun albumVariants(value: String?): List<String> = buildList {
        if (value.isNullOrBlank()) return@buildList
        fun addUseful(candidate: String) {
            candidate.trim().takeIf(String::isNotBlank)?.let { if (none { old -> clean(old) == clean(it) }) add(it) }
        }
        addUseful(value)
        addUseful(value.replace(editionNoise, ""))
        addUseful(value.replace(Regex("\\s*[-–—]\\s*(?:single|ep)$", RegexOption.IGNORE_CASE), ""))
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
        val prefix = if (left.startsWith(right) || right.startsWith(left)) 0.08 else 0.0
        return (0.52 * jaccard + 0.40 * containment + prefix).coerceAtMost(1.0)
    }
}