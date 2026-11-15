package app.selene.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Single dark theme, no Material You dynamic colours.
private val SeleneColorScheme = darkColorScheme(
    primary = SeleneColors.Period,
    onPrimary = SeleneColors.OnPeriod,
    primaryContainer = SeleneColors.SurfaceHigh,
    onPrimaryContainer = SeleneColors.TextPrimary,
    secondary = SeleneColors.Fertile,
    onSecondary = SeleneColors.Background,
    tertiary = SeleneColors.Pms,
    onTertiary = SeleneColors.TextPrimary,
    background = SeleneColors.Background,
    onBackground = SeleneColors.TextPrimary,
    surface = SeleneColors.Surface,
    onSurface = SeleneColors.TextPrimary,
    surfaceVariant = SeleneColors.SurfaceHigh,
    onSurfaceVariant = SeleneColors.TextSecondary,
    surfaceContainerLowest = SeleneColors.Background,
    surfaceContainerLow = SeleneColors.BottomBar,
    surfaceContainer = SeleneColors.Surface,
    surfaceContainerHigh = SeleneColors.SurfaceHigh,
    surfaceContainerHighest = SeleneColors.SurfaceHigh,
    inverseSurface = SeleneColors.TextPrimary,
    inverseOnSurface = SeleneColors.Background,
    inversePrimary = SeleneColors.Period,
    outline = SeleneColors.Border,
    outlineVariant = SeleneColors.Divider,
    error = SeleneColors.Period,
    onError = SeleneColors.OnPeriod,
    scrim = SeleneColors.Background,
)

@Composable
fun SeleneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SeleneColorScheme,
        typography = SeleneTypography,
        shapes = SeleneMaterialShapes,
        content = content,
    )
}
