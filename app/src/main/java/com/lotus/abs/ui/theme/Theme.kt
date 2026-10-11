package com.lotus.abs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1f6feb),
    onPrimary = Color(0xFFffffff),
    primaryContainer = Color(0xFFd4e4ff),
    onPrimaryContainer = Color(0xFF001a42),
    secondary = Color(0xFF565f7e),
    onSecondary = Color(0xFFffffff),
    secondaryContainer = Color(0xFFd9e2f8),
    onSecondaryContainer = Color(0xFF131d36),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFffffff),
    tertiaryContainer = Color(0xFFffd8f4),
    onTertiaryContainer = Color(0xFF2a132c),
    error = Color(0xFFb3261e),
    onError = Color(0xFFffffff),
    errorContainer = Color(0xFFf9dedc),
    onErrorContainer = Color(0xFF410e0b),
    background = Color(0xFffffbfe),
    onBackground = Color(0xFF1c1b1f),
    surface = Color(0xFffffbfe),
    onSurface = Color(0xFF1c1b1f),
    surfaceVariant = Color(0xFFece9f1),
    onSurfaceVariant = Color(0xFF49454f),
    outline = Color(0xFF79747e),
    inverseOnSurface = Color(0xFFf4eff4),
    inverseSurface = Color(0xFF313033),
    inversePrimary = Color(0xFFb3d9ff),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFb3d9ff),
    onPrimary = Color(0xFF003d82),
    primaryContainer = Color(0xFF0b4ca9),
    onPrimaryContainer = Color(0xFFd4e4ff),
    secondary = Color(0xFFbcc7ee),
    onSecondary = Color(0xFF293049),
    secondaryContainer = Color(0xFF3e4761),
    onSecondaryContainer = Color(0xFFd9e2f8),
    tertiary = Color(0xFFe0bde8),
    onTertiary = Color(0xFF422442),
    tertiaryContainer = Color(0xFF5a3d5b),
    onTertiaryContainer = Color(0xFFffd8f4),
    error = Color(0xFFf2b8b5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8c1d18),
    onErrorContainer = Color(0xFFf9dedc),
    background = Color(0xFF1c1b1f),
    onBackground = Color(0xFFe6e1e6),
    surface = Color(0xFF1c1b1f),
    onSurface = Color(0xFFe6e1e6),
    surfaceVariant = Color(0xFF49454f),
    onSurfaceVariant = Color(0xFFcac7d0),
    outline = Color(0xFF938f99),
    inverseOnSurface = Color(0xFF1c1b1f),
    inverseSurface = Color(0xFFe6e1e6),
    inversePrimary = Color(0xFF1f6feb),
)

@Composable
fun LotusABSAndroidTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
