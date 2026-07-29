package com.heartline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private data class Palette(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val outline: Color,
    val glow: Color
)

private val palettes = mapOf(
    "Bubblegum" to Palette(
        Color(0xFFB69AC8), Color(0xFFFFF7FB), Color(0xFFF3E6F1), Color(0xFF2D1732),
        Color(0xFF785D7C), Color(0xFFD58BE2), Color(0xFF8ED4C8), Color(0xFF2B1830), Color(0xFFFFD7F2)
    ),
    "Cyber Angel" to Palette(
        Color(0xFF17182B), Color(0xFF242744), Color(0xFF30365A), Color(0xFFF7F5FF),
        Color(0xFFAAA9CA), Color(0xFF7EE8FA), Color(0xFFFF79C6), Color(0xFF080914), Color(0xFF344F74)
    ),
    "Cherry Soda" to Palette(
        Color(0xFF641F31), Color(0xFFFFF2F4), Color(0xFFFFD8DF), Color(0xFF321017),
        Color(0xFF86525A), Color(0xFFE94B6A), Color(0xFFFFD36E), Color(0xFF260A10), Color(0xFFFFB8C6)
    ),
    "Haunted CRT" to Palette(
        Color(0xFF101912), Color(0xFF17251B), Color(0xFF233B29), Color(0xFFC8FFCB),
        Color(0xFF78B97E), Color(0xFF6BFF7A), Color(0xFFB5A0FF), Color(0xFF050A06), Color(0xFF244A2B)
    ),
    "Peach Dream" to Palette(
        Color(0xFFF1B49F), Color(0xFFFFF6EF), Color(0xFFFFE0C8), Color(0xFF48282A),
        Color(0xFF8B655F), Color(0xFFFF9C86), Color(0xFF91D0C5), Color(0xFF352023), Color(0xFFFFD5C7)
    )
)

private val HeartlineTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 19.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp
    )
)

@Composable
fun HeartlineTheme(name: String, content: @Composable () -> Unit) {
    val p = palettes[name] ?: palettes.getValue("Bubblegum")
    val scheme = lightColorScheme(
        primary = p.accent,
        onPrimary = p.ink,
        primaryContainer = p.glow,
        onPrimaryContainer = p.ink,
        secondary = p.accent2,
        onSecondary = p.ink,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.ink,
        background = p.bg,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        outline = p.outline,
        outlineVariant = p.muted.copy(alpha = 0.35f),
        error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = scheme, typography = HeartlineTypography, content = content)
}
