package com.lotus.abs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Reference Lotus Theme Colors
val LotusYellow = Color(0xFFFFCC00)
val DarkBackground = Color(0xFF0D0D0D)
val CardBackground = Color(0xFF1A1A1A)
val CardBorder = Color(0xFF333333)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val TerminalBg = Color(0xFF080808)

// Status Colors
val StatusGreen = Color(0xFF00E676)
val StatusRed = Color(0xFFE53935)
val StatusBlue = Color(0xFF2196F3)

// Terminal Colors
val TermTime = Color(0xFF666666)
val TermSent = Color(0xFFFF9800) // Orange/Yellow
val TermRecv = Color(0xFF00E676) // Green
val TermSys = Color(0xFFBDBDBD)  // Light Grey
val TermErr = Color(0xFFE53935)  // Red

private val LotusDarkColorScheme = darkColorScheme(
    primary = LotusYellow,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF332A00),
    onPrimaryContainer = LotusYellow,
    
    secondary = StatusBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF112338),
    onSecondaryContainer = StatusBlue,
    
    tertiary = StatusGreen,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF00331A),
    onTertiaryContainer = StatusGreen,
    
    error = StatusRed,
    onError = Color.White,
    errorContainer = Color(0xFF330D0C),
    onErrorContainer = StatusRed,
    
    background = DarkBackground,
    onBackground = TextPrimary,
    
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = TextSecondary,
    
    outline = CardBorder
)

@Composable
fun LotusABSAndroidTheme(
    content: @Composable () -> Unit
) {
    // Force dark theme for the Lotus OBD app
    MaterialTheme(
        colorScheme = LotusDarkColorScheme,
        content = content
    )
}
