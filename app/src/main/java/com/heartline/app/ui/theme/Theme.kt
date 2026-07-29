package com.heartline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accent2: Color,
    val outline: Color,
    val glow: Color
)

private val palettes = mapOf(
    "Bubblegum" to Palette(
        false, Color(0xFFF7EDF5), Color(0xFFFFFBFE), Color(0xFFF2E8F0), Color(0xFF2D1732),
        Color(0xFF785D7C), Color(0xFFB54BC7), Color.White, Color(0xFFCEEDE7), Color(0xFFDDC9D8), Color(0xFFF5D9F2)
    ),
    "Cyber Angel" to Palette(
        true, Color(0xFF111321), Color(0xFF1B1E31), Color(0xFF282D48), Color(0xFFF7F5FF),
        Color(0xFFB5B6D2), Color(0xFF75DFF5), Color(0xFF061318), Color(0xFFFF77BE), Color(0xFF39405D), Color(0xFF233A52)
    ),
    "Cherry Soda" to Palette(
        true, Color(0xFF3A101D), Color(0xFF511827), Color(0xFF6A2032), Color(0xFFFFF6F7),
        Color(0xFFD5AEB6), Color(0xFFF05473), Color.White, Color(0xFFFFCB6B), Color(0xFF7A3B4B), Color(0xFF7A253C)
    ),
    "Haunted CRT" to Palette(
        true, Color(0xFF0C120E), Color(0xFF141D17), Color(0xFF1D2B21), Color(0xFFD7FFD9),
        Color(0xFF91BD95), Color(0xFF70F27E), Color(0xFF08120A), Color(0xFFB7A4FF), Color(0xFF304735), Color(0xFF1D4024)
    ),
    "Peach Dream" to Palette(
        false, Color(0xFFFFEFE8), Color(0xFFFFFBF8), Color(0xFFFFE5D8), Color(0xFF48282A),
        Color(0xFF8B655F), Color(0xFFE87565), Color.White, Color(0xFFBFE5DE), Color(0xFFE7C7BC), Color(0xFFFFD9CF)
    )
)

private val HeartlineTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun HeartlineTheme(name: String, content: @Composable () -> Unit) {
    val p = palettes[name] ?: palettes.getValue("Bubblegum")
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.glow,
            onPrimaryContainer = p.ink,
            secondary = p.accent2,
            onSecondary = p.onAccent,
            secondaryContainer = p.raised,
            onSecondaryContainer = p.ink,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.raised,
            onSurfaceVariant = p.muted,
            outline = p.outline,
            outlineVariant = p.outline.copy(alpha = 0.7f),
            error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
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
            outlineVariant = p.outline.copy(alpha = 0.7f),
            error = Color(0xFFB3261E)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = HeartlineTypography, content = content)
}
