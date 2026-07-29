package com.heartline.app.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.heartline.app.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@Deprecated("Use ShareCardTheme")
internal enum class ShareCardStyle(val label: String, val themeId: String) {
    BLUSH("Blush", "blush"), MIDNIGHT("Midnight", "midnight"), CREAM("Cream", "cream")
}

internal object LyricShareCard {
    fun share(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        style: ShareCardStyle
    ): Boolean = share(context, title, artist, lyrics, ShareCardOptions(theme = ShareCardRegistry.byId(style.themeId)))

    fun share(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        options: ShareCardOptions
    ): Boolean = runCatching {
        require(lyrics.any(String::isNotBlank))
        val bitmap = render(context, title, artist, lyrics, options)
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

    fun save(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        options: ShareCardOptions
    ): Boolean = runCatching {
        require(lyrics.any(String::isNotBlank))
        val bitmap = render(context, title, artist, lyrics, options)
        val name = "HEARTLINE_${sanitize(title)}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HEARTLINE")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("Could not open image destination")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "HEARTLINE").apply { mkdirs() }
            FileOutputStream(File(directory, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    fun renderPreview(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        options: ShareCardOptions,
        maxWidth: Int = 420
    ): Bitmap {
        val full = render(context, title, artist, lyrics.ifEmpty { listOf("Select a lyric to preview") }, options)
        val width = maxWidth.coerceAtMost(full.width)
        val height = (full.height * (width / full.width.toFloat())).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(full, width, height, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    private fun render(
        context: Context,
        title: String,
        artist: String,
        lyrics: List<String>,
        options: ShareCardOptions
    ): Bitmap {
        val width = options.format.width
        val height = options.format.height
        val theme = options.theme
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, height, theme, options.backgroundIntensity)
        drawDecoration(canvas, width, height, theme)

        val margin = 72f
        val logoTop = if (options.format == ShareCardFormat.STORY) 110f else 72f
        if (options.showBranding) {
            context.getDrawable(R.drawable.heartline_logo)?.apply {
                setBounds(margin.toInt(), logoTop.toInt(), (margin + 120).toInt(), (logoTop + 120).toInt())
                draw(canvas)
            }
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.mainText
                textSize = 34f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("HEARTLINE", margin + 148f, logoTop + 73f, brand)
        }

        var metaY = if (options.showBranding) logoTop + 210f else logoTop + 80f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.mainText
            textSize = 58f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.secondaryText
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        if (options.showTitle) {
            canvas.drawText(ellipsize(title, titlePaint, width - margin * 2), margin, metaY, titlePaint)
            metaY += 58f
        }
        if (options.showArtist) {
            canvas.drawText(ellipsize(artist, artistPaint, width - margin * 2), margin, metaY, artistPaint)
            metaY += 52f
        }

        val footerSpace = if (options.showBranding) 130f else 80f
        val lyricTop = max(metaY + 55f, height * 0.30f)
        val lyricBottom = height - footerSpace
        val fit = fitLyrics(lyrics.filter(String::isNotBlank), width - margin * 2, lyricBottom - lyricTop, options.textScale, theme.alignment)
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.mainText
            textSize = fit.textSize
            textAlign = if (theme.alignment == ShareTextAlignment.CENTER) Paint.Align.CENTER else Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(
                if (theme.decoration == ShareDecoration.CRT) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val x = if (theme.alignment == ShareTextAlignment.CENTER) width / 2f else margin
        val blockHeight = fit.lines.size * fit.lineHeight
        var y = lyricTop + ((lyricBottom - lyricTop - blockHeight) / 2f).coerceAtLeast(0f) + fit.textSize
        fit.lines.forEach { line ->
            canvas.drawText(line, x, y, lyricPaint)
            y += fit.lineHeight
        }

        if (options.showBranding) {
            val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.secondaryText
                textSize = 27f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
            }
            canvas.drawText("the words follow the music", width / 2f, height - 62f, footer)
        }
        drawBorder(canvas, width, height, theme)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, theme: ShareCardTheme, intensity: Float) {
        val adjusted = theme.colors.map { color ->
            val factor = intensity.coerceIn(0.45f, 1.4f)
            Color.rgb(
                (Color.red(color) * factor).toInt().coerceIn(0, 255),
                (Color.green(color) * factor).toInt().coerceIn(0, 255),
                (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            )
        }.toIntArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), adjusted, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawDecoration(canvas: Canvas, width: Int, height: Int, theme: ShareCardTheme) {
        when (theme.decoration) {
            ShareDecoration.GLOW -> {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
                canvas.drawCircle(width * .86f, height * .13f, width * .31f, glow)
                canvas.drawCircle(width * .10f, height * .90f, width * .28f, glow)
            }
            ShareDecoration.POLAROID -> {
                val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(75, 255, 255, 255) }
                canvas.drawRoundRect(RectF(48f, 44f, width - 48f, height - 58f), 22f, 22f, paper)
            }
            ShareDecoration.EDITORIAL -> {
                val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.mainText; strokeWidth = 4f }
                canvas.drawLine(72f, height * .24f, width - 72f, height * .24f, rule)
            }
            ShareDecoration.CRT -> {
                val scan = Paint().apply { color = Color.argb(20, 130, 255, 140); strokeWidth = 2f }
                var y = 0f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, scan); y += 8f }
            }
            ShareDecoration.LOVE_LETTER -> {
                val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 130, 45, 60); style = Paint.Style.STROKE; strokeWidth = 7f }
                canvas.drawCircle(width - 145f, 150f, 66f, stamp)
                val path = Path().apply { moveTo(width - 185f, 150f); lineTo(width - 145f, 188f); lineTo(width - 105f, 150f) }
                canvas.drawPath(path, stamp)
            }
            ShareDecoration.CYBER -> {
                val neon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 80, 220, 255); style = Paint.Style.STROKE; strokeWidth = 6f }
                canvas.drawRoundRect(RectF(34f, 34f, width - 34f, height - 34f), 36f, 36f, neon)
                canvas.drawLine(34f, height * .72f, width * .42f, height * .72f, neon)
            }
            ShareDecoration.CLEAN -> Unit
        }
    }

    private fun drawBorder(canvas: Canvas, width: Int, height: Int, theme: ShareCardTheme) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (theme.decoration == ShareDecoration.CYBER) 2f else 3f
            color = Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(26f, 26f, width - 26f, height - 26f), 42f, 42f, border)
    }

    private data class Fit(val textSize: Float, val lineHeight: Float, val lines: List<String>)

    private fun fitLyrics(lyrics: List<String>, maxWidth: Float, maxHeight: Float, scale: Float, alignment: ShareTextAlignment): Fit {
        var size = (when (lyrics.size) { 1 -> 82f; 2 -> 74f; 3 -> 66f; else -> 58f } * scale.coerceIn(.75f, 1.3f)).coerceAtMost(96f)
        while (size >= 34f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            val lines = lyrics.flatMap { wrapTokenSafe(it, paint, maxWidth) }
            val lineHeight = size * if (alignment == ShareTextAlignment.LEFT) 1.25f else 1.28f
            if (lines.size * lineHeight <= maxHeight) return Fit(size, lineHeight, lines)
            size -= 2f
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val lines = lyrics.flatMap { wrapTokenSafe(it, paint, maxWidth) }
        val maxLines = (maxHeight / 42f).toInt().coerceAtLeast(1)
        val clipped = if (lines.size <= maxLines) lines else lines.take(maxLines).toMutableList().also { it[it.lastIndex] = ellipsize(it.last(), paint, maxWidth) }
        return Fit(34f, 42f, clipped)
    }

    private fun wrapTokenSafe(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return listOf("…")
        val normalized = words.flatMap { word -> splitOversizedToken(word, paint, maxWidth) }
        val lines = mutableListOf<String>()
        var current = ""
        normalized.forEach { word ->
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

    private fun splitOversizedToken(token: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(token) <= maxWidth) return listOf(token)
        val parts = mutableListOf<String>()
        var current = ""
        token.codePoints().toArray().forEach { point ->
            val char = String(Character.toChars(point))
            if (current.isNotEmpty() && paint.measureText(current + char) > maxWidth) {
                parts += current
                current = char
            } else current += char
        }
        if (current.isNotEmpty()) parts += current
        return parts
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && paint.measureText("$value…") > maxWidth) value = value.dropLast(1)
        return "$value…"
    }

    private fun sanitize(value: String) = value.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { "lyrics" }
}
