package com.pathhub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Obsidian & Atmospheric Base Tones
val Obsidian950 = Color(0xFF050811)
val Obsidian900 = Color(0xFF090D1A)
val Obsidian850 = Color(0xFF0E1526)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)

// Neon & Jewel Accents
val Emerald400 = Color(0xFF34D399)
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val AuroraCyan = Color(0xFF06B6D4)
val ElectricViolet = Color(0xFF8B5CF6)
val Rose500 = Color(0xFFF43F5E)
val Rose600 = Color(0xFFE11D48)
val Blue500 = Color(0xFF3B82F6)

// Liquid Glass Surfaces
val GlassBackground = Color(0xE6090D1A)
val GlassSurface = Color(0xCC111827)
val GlassSurfaceElevated = Color(0xE61E293B)
val GlassHighlight = Color(0x40FFFFFF)
val GlassSubtle = Color(0x0DFFFFFF)

// Gradients
val GlassBorderGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x66FFFFFF),
        Color(0x1AFFFFFF),
        Color(0x05FFFFFF)
    )
)

val GlassCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x4DFFFFFF),
        Color(0x14FFFFFF),
        Color(0x02FFFFFF)
    )
)

val EmeraldGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF10B981),
        Color(0xFF059669)
    )
)

val AuroraGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF10B981),
        Color(0xFF06B6D4)
    )
)

val RoseGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF43F5E),
        Color(0xFFBE123C)
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = Emerald400,
    secondary = AuroraCyan,
    onSecondary = Color.White,
    background = Obsidian950,
    onBackground = Color.White,
    surface = GlassSurface,
    onSurface = Color.White,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    error = Rose500
)

@Composable
fun PathHubTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
