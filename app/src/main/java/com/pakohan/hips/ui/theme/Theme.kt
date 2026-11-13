package com.pakohan.hips.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = NavyTint,
    onPrimaryContainer = BrandNavyDeep,
    secondary = BrandAmber,
    onSecondary = BrandNavyDeep,
    secondaryContainer = AmberTint,
    onSecondaryContainer = AmberInk,
    tertiary = BrandAmber,
    onTertiary = BrandNavyDeep,
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = PaperShade,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = PaperCard,
    surfaceContainer = PaperShade,
    surfaceContainerHigh = PaperShade,
    surfaceContainerHighest = PaperShade,
    outline = InkSoft,
    outlineVariant = PaperEdge,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandAmber,
    onPrimary = BrandNavyDeep,
    primaryContainer = NavyGlow,
    onPrimaryContainer = NavyTint,
    secondary = BrandAmber,
    onSecondary = BrandNavyDeep,
    secondaryContainer = AmberDeep,
    onSecondaryContainer = AmberTint,
    tertiary = BrandAmber,
    onTertiary = BrandNavyDeep,
    background = Night,
    onBackground = Moonlight,
    surface = NightCard,
    onSurface = Moonlight,
    surfaceVariant = NightShade,
    onSurfaceVariant = MoonlightSoft,
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = NightCard,
    surfaceContainer = NightShade,
    surfaceContainerHigh = NightShade,
    surfaceContainerHighest = NightShade,
    outline = MoonlightSoft,
    outlineVariant = NightEdge,
)

private val HipsShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/**
 * The app wears the colours of its icon in both themes. Dynamic colour (the wallpaper's palette on
 * Android 12 and later) is deliberately not used: it would replace exactly those colours.
 */
@Composable
fun HIPSTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = HipsShapes,
        content = content,
    )
}
