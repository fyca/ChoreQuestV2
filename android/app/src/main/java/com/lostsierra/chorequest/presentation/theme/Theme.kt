package com.lostsierra.chorequest.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    secondary = Purple,
    tertiary = Pink,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = LightBackground,
    onSecondary = LightBackground,
    onTertiary = LightBackground,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = SkyBlue,
    secondary = Purple,
    tertiary = Pink,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightBackground,
    onSecondary = LightBackground,
    onTertiary = LightBackground,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface
)

// Colorful theme for children
private val ColorfulColorScheme = lightColorScheme(
    primary = SkyBlue,
    secondary = SunshineYellow,
    tertiary = GrassGreen,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightBackground,
    onSecondary = LightOnBackground,
    onTertiary = LightOnBackground,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    primaryContainer = SkyBlue.copy(alpha = 0.1f),
    secondaryContainer = SunshineYellow.copy(alpha = 0.1f),
    tertiaryContainer = GrassGreen.copy(alpha = 0.1f),
    error = CoralOrange,
    onError = LightBackground
)

enum class AppTheme {
    LIGHT, DARK, COLORFUL, SYSTEM
}

/**
 * Convert domain ThemeMode to AppTheme
 */
fun com.lostsierra.chorequest.domain.models.ThemeMode.toAppTheme(): AppTheme {
    return when (this) {
        com.lostsierra.chorequest.domain.models.ThemeMode.LIGHT -> AppTheme.LIGHT
        com.lostsierra.chorequest.domain.models.ThemeMode.DARK -> AppTheme.DARK
        com.lostsierra.chorequest.domain.models.ThemeMode.COLORFUL -> AppTheme.COLORFUL
    }
}

@Composable
fun ChoreQuestTheme(
    themeMode: AppTheme = AppTheme.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == AppTheme.COLORFUL -> ColorfulColorScheme
        themeMode == AppTheme.DARK -> DarkColorScheme
        themeMode == AppTheme.LIGHT -> LightColorScheme
        themeMode == AppTheme.SYSTEM && darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
