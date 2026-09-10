package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Cyan80,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004E5B),
    onSecondaryContainer = Color(0xFFA6EEFF),
    tertiary = AccentGreen,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005326),
    onTertiaryContainer = Color(0xFF72FBA0),
    background = DarkBackground,
    onBackground = Color(0xFFE2E2E6),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF3B4E68)
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = CyanGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6F1),
    onSecondaryContainer = Color(0xFF0E1E26),
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA4F2DC),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7FBFD),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFEDF4F8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70797D)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
