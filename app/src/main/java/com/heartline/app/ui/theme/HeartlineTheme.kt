package com.heartline.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private data class Palette(
    val bg: Color, val surface: Color, val raised: Color, val ink: Color,
    val muted: Color, val accent: Color, val accent2: Color, val outline: Color
)

private val palettes = mapOf(
    "Bubblegum" to Palette(Color(0xFFB79AC8), Color(0xFFF8EDF2), Color(0xFFEADFBF), Color(0xFF3A213E), Color(0xFF755A79), Color(0xFFC28AD2), Color(0xFF9AC9C1), Color(0xFF2E1B32)),
    "Cyber Angel" to Palette(Color(0xFF1A1B2E), Color(0xFF252846), Color(0xFF31355C), Color(0xFFF2F0FF), Color(0xFFA9A8CB), Color(0xFF7EE8FA), Color(0xFFFF79C6), Color(0xFF080914)),
    "Cherry Soda" to Palette(Color(0xFF5E1D2C), Color(0xFFFFE8E8), Color(0xFFFFC7D1), Color(0xFF351118), Color(0xFF86525A), Color(0xFFE44764), Color(0xFFFFD36E), Color(0xFF260A10)),
    "Haunted CRT" to Palette(Color(0xFF111A14), Color(0xFF17261C), Color(0xFF243D2B), Color(0xFFB9FFBE), Color(0xFF74B67A), Color(0xFF67FF79), Color(0xFFB5A0FF), Color(0xFF050A06)),
    "Peach Dream" to Palette(Color(0xFFF1B39E), Color(0xFFFFF2E8), Color(0xFFFFD8B8), Color(0xFF4B2A2B), Color(0xFF8E6862), Color(0xFFFF9E87), Color(0xFF9BCAC3), Color(0xFF352023))
)

@Composable
fun HeartlineTheme(name: String, content: @Composable () -> Unit) {
    val p = palettes[name] ?: palettes.getValue("Bubblegum")
    val scheme = lightColorScheme(
        primary = p.accent, onPrimary = p.ink, secondary = p.accent2,
        background = p.bg, onBackground = p.ink, surface = p.surface,
        onSurface = p.ink, surfaceVariant = p.raised, onSurfaceVariant = p.muted,
        outline = p.outline, error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
