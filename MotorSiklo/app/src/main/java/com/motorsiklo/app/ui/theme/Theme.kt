package com.motorsiklo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = Amber400,
    onPrimary          = NavyDark,
    primaryContainer   = Color(0x22F5A623),
    onPrimaryContainer = Amber300,
    secondary          = Amber300,
    onSecondary        = NavyDark,
    background         = NavyDark,
    onBackground       = White,
    surface            = NavyCard,
    onSurface          = White,
    surfaceVariant     = NavyCardAlt,
    onSurfaceVariant   = WhiteMuted,
    outline            = BorderSubtle,
    outlineVariant     = BorderInput,
    error              = Color(0xFFFF5252),
    onError            = White,
    errorContainer     = Color(0x33FF5252),
    onErrorContainer   = Color(0xFFFF5252),
)

// ── Light scheme (Professional Olive Green) ──────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = OlivePrimary,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFDDE6C6),
    onPrimaryContainer = Color(0xFF161D09),
    secondary          = OliveAccent,
    onSecondary        = Color.White,
    background         = OliveBg,
    onBackground       = OliveText,
    surface            = OliveSurface,
    onSurface          = OliveText,
    surfaceVariant     = Color(0xFFE2E4D9),
    onSurfaceVariant   = OliveMuted,
    outline            = OliveBorder,
    outlineVariant     = Color(0xFFD1D5C8),
    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),
)

// ── Theme composable ──────────────────────────────────────────────────────────
@Composable
fun MotorSikloTheme(content: @Composable () -> Unit) {
    val colorScheme = if (ThemeManager.isDarkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
