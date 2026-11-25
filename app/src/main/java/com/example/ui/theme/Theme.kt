package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GlassColors(
  val isDark: Boolean,
  val bgTop: Color,
  val bgMid: Color,
  val bgBottom: Color,
  val orbPrimary: Color,
  val orbSecondary: Color,
  val orbTertiary: Color,
  val orbAccent: Color,
  val cardFillTop: Color,
  val cardFillBottom: Color,
  val cardBorderStart: Color,
  val cardBorderEnd: Color,
  val cardInnerHighlight: Color,
  val pillFill: Color,
  val pillSelectedFill: Color,
  val headerFillTop: Color,
  val headerFillBottom: Color,
  val sheetFill: Color
)

private val DarkGlassColors = GlassColors(
  isDark = true,
  bgTop = ObsidianBgTop,
  bgMid = ObsidianBgMid,
  bgBottom = ObsidianBgBottom,
  orbPrimary = Color(0xFFEF233C).copy(alpha = 0.32f),
  orbSecondary = Color(0xFF4F46E5).copy(alpha = 0.36f),
  orbTertiary = Color(0xFF06B6D4).copy(alpha = 0.25f),
  orbAccent = Color(0xFF9333EA).copy(alpha = 0.28f),
  cardFillTop = Color(0xFFFFFFFF).copy(alpha = 0.11f),
  cardFillBottom = Color(0xFFFFFFFF).copy(alpha = 0.04f),
  cardBorderStart = Color(0xFFFFFFFF).copy(alpha = 0.34f),
  cardBorderEnd = Color(0xFFFFFFFF).copy(alpha = 0.07f),
  cardInnerHighlight = Color(0xFFFFFFFF).copy(alpha = 0.22f),
  pillFill = Color(0xFFFFFFFF).copy(alpha = 0.08f),
  pillSelectedFill = Color(0xFFFF3B5C).copy(alpha = 0.24f),
  headerFillTop = Color(0xFF141C33).copy(alpha = 0.82f),
  headerFillBottom = Color(0xFF0F1629).copy(alpha = 0.68f),
  sheetFill = Color(0xFF11192E).copy(alpha = 0.92f)
)

private val LightGlassColors = GlassColors(
  isDark = false,
  bgTop = PearlBgTop,
  bgMid = PearlBgMid,
  bgBottom = PearlBgBottom,
  orbPrimary = Color(0xFFFF4D6D).copy(alpha = 0.24f),
  orbSecondary = Color(0xFF6366F1).copy(alpha = 0.24f),
  orbTertiary = Color(0xFF0EA5E9).copy(alpha = 0.22f),
  orbAccent = Color(0xFFA855F7).copy(alpha = 0.20f),
  cardFillTop = Color(0xFFFFFFFF).copy(alpha = 0.78f),
  cardFillBottom = Color(0xFFFFFFFF).copy(alpha = 0.44f),
  cardBorderStart = Color(0xFFFFFFFF).copy(alpha = 0.95f),
  cardBorderEnd = Color(0xFFCBD5E1).copy(alpha = 0.45f),
  cardInnerHighlight = Color(0xFFFFFFFF).copy(alpha = 0.92f),
  pillFill = Color(0xFFFFFFFF).copy(alpha = 0.60f),
  pillSelectedFill = Color(0xFFFF3B5C).copy(alpha = 0.14f),
  headerFillTop = Color(0xFFFFFFFF).copy(alpha = 0.82f),
  headerFillBottom = Color(0xFFF8FAFC).copy(alpha = 0.70f),
  sheetFill = Color(0xFFF8FAFC).copy(alpha = 0.94f)
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlassColors }

private val DarkColorScheme = darkColorScheme(
  primary = BrandCrimson,
  secondary = BrandIndigo,
  tertiary = BrandCyan,
  background = ObsidianBgTop,
  surface = ObsidianSurface,
  onPrimary = Color.White,
  onSecondary = Color.White,
  onBackground = TextOnDark,
  onSurface = TextOnDark,
  onSurfaceVariant = TextSecondaryOnDark,
  outline = Color.White.copy(alpha = 0.18f)
)

private val LightColorScheme = lightColorScheme(
  primary = BrandCrimsonDeep,
  secondary = BrandIndigo,
  tertiary = BrandCyan,
  background = PearlBgTop,
  surface = PearlSurface,
  onPrimary = Color.White,
  onSecondary = Color.White,
  onBackground = TextOnLight,
  onSurface = TextOnLight,
  onSurfaceVariant = TextSecondaryOnLight,
  outline = Color(0xFF94A3B8).copy(alpha = 0.35f)
)

private val BlackWorkspaceGlassColors = GlassColors(
  isDark = true,
  bgTop = Color.Black,
  bgMid = Color.Black,
  bgBottom = Color.Black,
  orbPrimary = Color.Transparent,
  orbSecondary = Color.Transparent,
  orbTertiary = Color.Transparent,
  orbAccent = Color.Transparent,
  cardFillTop = Color(0xFF121216),
  cardFillBottom = Color(0xFF0A0A0D),
  cardBorderStart = Color.White.copy(alpha = 0.28f),
  cardBorderEnd = Color.White.copy(alpha = 0.12f),
  cardInnerHighlight = Color.White.copy(alpha = 0.16f),
  pillFill = Color(0xFF18181E),
  pillSelectedFill = Color(0xFF272732),
  headerFillTop = Color(0xFF0E0E12),
  headerFillBottom = Color(0xFF08080A),
  sheetFill = Color(0xFF0C0C10)
)

private val BlackWorkspaceColorScheme = darkColorScheme(
  primary = Color.White,
  onPrimary = Color.Black,
  secondary = Color.White,
  onSecondary = Color.Black,
  tertiary = Color.White,
  onTertiary = Color.Black,
  background = Color.Black,
  onBackground = Color.White,
  surface = Color(0xFF0A0A0D),
  onSurface = Color.White,
  surfaceVariant = Color(0xFF14141A),
  onSurfaceVariant = Color.White,
  surfaceContainer = Color(0xFF0E0E12),
  surfaceContainerLow = Color(0xFF08080A),
  surfaceContainerHigh = Color(0xFF16161C),
  surfaceContainerHighest = Color(0xFF1F1F28),
  outline = Color.White.copy(alpha = 0.42f),
  outlineVariant = Color.White.copy(alpha = 0.22f)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val glassColors = if (darkTheme) DarkGlassColors else LightGlassColors

  CompositionLocalProvider(LocalGlassColors provides glassColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}

/**
 * Dedicated high-contrast Black Background + White Text theme used across all PDF tool
 * function screens (Merge PDF, Edit PDF, Split, Compress, Sign, OCR, Protect, etc.).
 */
@Composable
fun BlackWorkspaceTheme(
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(
    LocalGlassColors provides BlackWorkspaceGlassColors,
    LocalContentColor provides Color.White
  ) {
    MaterialTheme(
      colorScheme = BlackWorkspaceColorScheme,
      typography = Typography,
      content = content
    )
  }
}
