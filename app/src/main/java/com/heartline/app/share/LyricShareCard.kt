package com.heartline.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.FileProvider
import com.heartline.app.R
import java.io.File
import java.io.FileOutputStream

internal enum class ShareCardStyle(val label: String) {
    BLUSH("Blush"),
    MIDNIGHT("Midnight"),
    CREAM("Cream")
}

internal object LyricShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun share(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        style: ShareCardStyle
    ): Boolean = runCatching {
        require(lyrics.isNotEmpty())
        val bitmap = render(context, title, artist, lyrics, style)
        val directory = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach(File::delete)
        val file = File(directory, "heartline_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$title — $artist")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share lyrics"))
        true
    }.getOrDefault(false)

    private fun render(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        style: ShareCardStyle
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = when (style) {
            ShareCardStyle.BLUSH -> intArrayOf(Color.rgb(255, 179, 200), Color.rgb(236, 111, 158))
            ShareCardStyle.MIDNIGHT -> intArrayOf(Color.rgb(31, 23, 45), Color.rgb(95, 43, 82))
            ShareCardStyle.CREAM -> intArrayOf(Color.rgb(255, 246, 225), Color.rgb(247, 202, 170))
        }
        val lightText = style != ShareCardStyle.CREAM
        val mainText = if (lightText) Color.WHITE else Color.rgb(58, 32, 43)
        val secondaryText = if (lightText) Color.argb(210, 255, 255, 255) else Color.rgb(113, 73, 82)

        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        }

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 255, 255, 255) }
        canvas.drawCircle(930f, 180f, 330f, glow)
        canvas.drawCircle(120f, 1240f, 300f, glow)

        val logo = context.getDrawable(R.drawable.heartline_logo)
        logo?.setBounds(72, 72, 192, 192)
        logo?.draw(canvas)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainText
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("HEARTLINE", 220f, 145f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainText
            textSize = 58f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        canvas.drawText(ellipsize(title, titlePaint, 880f), 72f, 270f, titlePaint)
        canvas.drawText(ellipsize(artist, artistPaint, 880f), 72f, 322f, artistPaint)

        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainText
            textSize = when (lyrics.size) {
                1 -> 78f
                2 -> 70f
                3 -> 62f
                else -> 54f
            }
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val maxWidth = 880f
        val wrapped = lyrics.flatMap { wrap(it, lyricPaint, maxWidth) }
        val lineHeight = lyricPaint.textSize * 1.28f
        val blockHeight = wrapped.size * lineHeight
        var y = (HEIGHT / 2f - blockHeight / 2f).coerceAtLeast(420f) + lyricPaint.textSize
        wrapped.forEach { line ->
            canvas.drawText(line, WIDTH / 2f, y, lyricPaint)
            y += lineHeight
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 27f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        canvas.drawText("the words follow the music", WIDTH / 2f, 1265f, footerPaint)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(45, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(26f, 26f, WIDTH - 26f, HEIGHT - 26f), 42f, 42f, border)
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return listOf("…")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) current = candidate
            else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && paint.measureText("$value…") > maxWidth) value = value.dropLast(1)
        return "$value…"
    }
}
