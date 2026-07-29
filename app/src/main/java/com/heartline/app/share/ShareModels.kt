package com.heartline.app.share

import android.graphics.Color

enum class ShareCardFormat(val label: String, val width: Int, val height: Int) {
    POST("Post", 1080, 1350),
    STORY("Story", 1080, 1920),
    SQUARE("Square", 1080, 1080)
}

enum class ShareTextAlignment { LEFT, CENTER }
enum class ShareDecoration { GLOW, POLAROID, EDITORIAL, CRT, LOVE_LETTER, CYBER, CLEAN }

data class ShareCardTheme(
    val id: String,
    val name: String,
    val colors: IntArray,
    val mainText: Int,
    val secondaryText: Int,
    val alignment: ShareTextAlignment,
    val decoration: ShareDecoration,
    val description: String
)

object ShareCardRegistry {
    val all = listOf(
        ShareCardTheme("blush", "Blush", intArrayOf(Color.rgb(255, 179, 200), Color.rgb(236, 111, 158)), Color.WHITE, Color.argb(220, 255, 255, 255), ShareTextAlignment.CENTER, ShareDecoration.GLOW, "Soft pink gradient"),
        ShareCardTheme("midnight", "Midnight", intArrayOf(Color.rgb(31, 23, 45), Color.rgb(95, 43, 82)), Color.WHITE, Color.argb(210, 255, 255, 255), ShareTextAlignment.CENTER, ShareDecoration.GLOW, "Deep violet night"),
        ShareCardTheme("cream", "Cream", intArrayOf(Color.rgb(255, 246, 225), Color.rgb(247, 202, 170)), Color.rgb(58, 32, 43), Color.rgb(113, 73, 82), ShareTextAlignment.CENTER, ShareDecoration.CLEAN, "Warm editorial cream"),
        ShareCardTheme("polaroid", "Polaroid", intArrayOf(Color.rgb(242, 235, 224), Color.rgb(216, 196, 180)), Color.rgb(38, 31, 29), Color.rgb(102, 87, 81), ShareTextAlignment.LEFT, ShareDecoration.POLAROID, "Off-white photo print"),
        ShareCardTheme("editorial", "Editorial", intArrayOf(Color.rgb(247, 241, 229), Color.rgb(226, 218, 205)), Color.rgb(55, 31, 38), Color.rgb(114, 82, 88), ShareTextAlignment.LEFT, ShareDecoration.EDITORIAL, "Magazine-inspired layout"),
        ShareCardTheme("crt", "CRT", intArrayOf(Color.rgb(7, 18, 11), Color.rgb(18, 42, 24)), Color.rgb(122, 255, 135), Color.rgb(116, 188, 123), ShareTextAlignment.LEFT, ShareDecoration.CRT, "Ghostly terminal green"),
        ShareCardTheme("love_letter", "Love Letter", intArrayOf(Color.rgb(249, 237, 224), Color.rgb(236, 211, 201)), Color.rgb(105, 38, 52), Color.rgb(139, 91, 98), ShareTextAlignment.LEFT, ShareDecoration.LOVE_LETTER, "Burgundy ink on paper"),
        ShareCardTheme("cyber", "Cyber", intArrayOf(Color.rgb(4, 12, 29), Color.rgb(26, 20, 64)), Color.rgb(234, 246, 255), Color.rgb(122, 224, 255), ShareTextAlignment.CENTER, ShareDecoration.CYBER, "Neon blue and violet")
    )

    fun byId(id: String): ShareCardTheme = all.firstOrNull { it.id == id } ?: all.first()
}

data class ShareCardOptions(
    val format: ShareCardFormat = ShareCardFormat.POST,
    val theme: ShareCardTheme = ShareCardRegistry.all.first(),
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showBranding: Boolean = true,
    val textScale: Float = 1f,
    val backgroundIntensity: Float = 1f
)
