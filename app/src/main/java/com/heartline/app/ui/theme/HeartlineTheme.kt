package com.heartline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import com.heartline.app.data.AppSettings

data class HeartlinePalette(
    val dark: Boolean,
    val background: Color,
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

data class HeartlineThemeDefinition(
    val id: String,
    val displayName: String,
    val palette: HeartlinePalette,
    val description: String
)

object HeartlineThemeRegistry {
    val all = listOf(
        theme("Bubblegum", false, 0xFFF7EDF5, 0xFFFFFBFE, 0xFFF2E8F0, 0xFF2D1732, 0xFF785D7C, 0xFFB54BC7, 0xFFFFFFFF, 0xFFCEEDE7, 0xFFDDC9D8, 0xFFF5D9F2, "Soft pink and lilac"),
        theme("Cyber Angel", true, 0xFF111321, 0xFF1B1E31, 0xFF282D48, 0xFFF7F5FF, 0xFFB5B6D2, 0xFF75DFF5, 0xFF061318, 0xFFFF77BE, 0xFF39405D, 0xFF233A52, "Neon cyan and pink"),
        theme("Cherry Soda", true, 0xFF3A101D, 0xFF511827, 0xFF6A2032, 0xFFFFF6F7, 0xFFD5AEB6, 0xFFF05473, 0xFFFFFFFF, 0xFFFFCB6B, 0xFF7A3B4B, 0xFF7A253C, "Deep cherry and coral"),
        theme("Haunted CRT", true, 0xFF0C120E, 0xFF141D17, 0xFF1D2B21, 0xFFD7FFD9, 0xFF91BD95, 0xFF70F27E, 0xFF08120A, 0xFFB7A4FF, 0xFF304735, 0xFF1D4024, "Ghostly terminal green"),
        theme("Peach Dream", false, 0xFFFFEFE8, 0xFFFFFBF8, 0xFFFFE5D8, 0xFF48282A, 0xFF8B655F, 0xFFE87565, 0xFFFFFFFF, 0xFFBFE5DE, 0xFFE7C7BC, 0xFFFFD9CF, "Warm peach and cream"),
        theme("Moonlit Lavender", true, 0xFF171322, 0xFF211A31, 0xFF302641, 0xFFF6F0FF, 0xFFC2B4D3, 0xFFB98CFF, 0xFF170D23, 0xFFFF8DCB, 0xFF4B3D62, 0xFF392B50, "Violet night and silver pink"),
        theme("Ocean Static", true, 0xFF071A25, 0xFF0D2633, 0xFF153746, 0xFFE9FAFF, 0xFFA3C5D0, 0xFF5ED8F2, 0xFF00161C, 0xFF70E6C0, 0xFF28505E, 0xFF123F50, "Deep navy and sea glass"),
        theme("Paper Heart", false, 0xFFF7F0E5, 0xFFFFFCF6, 0xFFF0E3D5, 0xFF4A2028, 0xFF85676B, 0xFF9B344C, 0xFFFFFFFF, 0xFFC67A87, 0xFFD8C4B8, 0xFFF1D7D7, "Editorial cream and burgundy"),
        theme("Electric Blue", true, 0xFF070B14, 0xFF0D1423, 0xFF17233B, 0xFFF6FAFF, 0xFF9FB2D1, 0xFF3478FF, 0xFFFFFFFF, 0xFF7BE0FF, 0xFF2A3D61, 0xFF17386F, "Cobalt light on near-black"),
        theme("Matcha Diary", false, 0xFFF1F4E8, 0xFFFBFDF6, 0xFFE6EBD8, 0xFF243827, 0xFF667264, 0xFF6E9C68, 0xFFFFFFFF, 0xFFD8A58D, 0xFFC8D0BC, 0xFFDCE8D6, "Matcha green and warm ivory")
    )

    fun byName(name: String): HeartlineThemeDefinition = all.firstOrNull { it.displayName == name } ?: all.first()

    private fun theme(
        name: String,
        dark: Boolean,
        bg: Long,
        surface: Long,
        raised: Long,
        ink: Long,
        muted: Long,
        accent: Long,
        onAccent: Long,
        accent2: Long,
        outline: Long,
        glow: Long,
        description: String
    ) = HeartlineThemeDefinition(
        id = name.lowercase().replace(" ", "_"),
        displayName = name,
        palette = HeartlinePalette(dark, Color(bg), Color(surface), Color(raised), Color(ink), Color(muted), Color(accent), Color(onAccent), Color(accent2), Color(outline), Color(glow)),
        description = description
    )
}

private val HeartlineTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp)
)

@Composable
fun HeartlineTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val selectedName = if (settings.followSystemTheme) {
        if (isSystemInDarkTheme()) settings.systemDarkTheme else settings.systemLightTheme
    } else settings.theme
    val definition = HeartlineThemeRegistry.byName(selectedName)
    val p = definition.palette
    val background = if (settings.oledBlack && p.dark) Color.Black else p.background
    val surface = if (settings.oledBlack && p.dark) Color(0xFF080808) else p.surface
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            primaryContainer = p.glow, onPrimaryContainer = p.ink,
            secondary = p.accent2, onSecondary = p.onAccent,
            secondaryContainer = p.raised, onSecondaryContainer = p.ink,
            background = background, onBackground = p.ink,
            surface = surface, onSurface = p.ink,
            surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            outline = p.outline, outlineVariant = p.outline.copy(alpha = 0.7f),
            error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            primaryContainer = p.glow, onPrimaryContainer = p.ink,
            secondary = p.accent2, onSecondary = p.ink,
            secondaryContainer = p.raised, onSecondaryContainer = p.ink,
            background = background, onBackground = p.ink,
            surface = surface, onSurface = p.ink,
            surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            outline = p.outline, outlineVariant = p.outline.copy(alpha = 0.7f),
            error = Color(0xFFB3261E)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = HeartlineTypography, content = content)
}

@Composable
fun HeartlineTheme(name: String, content: @Composable () -> Unit) {
    val fallback = AppSettings(name, true, 20, true, true, true, 0, true, "hide_lyrics_locked", true, false, null)
    HeartlineTheme(fallback, content)
}
