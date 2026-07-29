package com.heartline.app.lyrics

import android.net.Uri
import com.heartline.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class LrclibResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

class LrclibClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun search(query: String): List<LrclibResult> = withContext(Dispatchers.IO) {
        val safeQuery = query.trim().take(220)
        if (safeQuery.isBlank()) return@withContext emptyList()

        val url = URL("${BuildConfig.LRCLIB_BASE_URL}/api/search?q=${Uri.encode(safeQuery)}")
        require(url.protocol == "https") { "Lyrics requests must use HTTPS" }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HEARTLINE-Android/${BuildConfig.VERSION_NAME} (contact: local-app)")
            instanceFollowRedirects = false
        }
        try {
            val status = connection.responseCode
            if (status == 429) throw LyricsRateLimitedException()
            if (status !in 200..299) throw LyricsNetworkException("Lyrics service returned HTTP $status")

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_RESPONSE_BYTES) throw LyricsNetworkException("Lyrics response was unexpectedly large")
            val body = BufferedInputStream(connection.inputStream).use { input ->
                val output = ByteArrayOutputStream(minOf(declaredLength.coerceAtLeast(0), 64 * 1024).toInt())
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        throw LyricsNetworkException("Lyrics response exceeded safety limit")
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
            json.decodeFromString<List<LrclibResult>>(body).take(MAX_RESULTS)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_RESULTS = 100
    }
}

class LyricsRateLimitedException : Exception("Lyrics provider rate limit reached")
class LyricsNetworkException(message: String) : Exception(message)
