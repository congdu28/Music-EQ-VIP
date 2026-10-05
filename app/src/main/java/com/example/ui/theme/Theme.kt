package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = AppThemeColors.isDark,
    accentColor: androidx.compose.ui.graphics.Color = AppThemeColors.accent,
    dynamicColor: Boolean = false, // Use our handcrafted rich neon audiophile scheme
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = DarkBackground.toArgb()
                it.navigationBarColor = DarkBackground.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(
            primary = accentColor,
            onPrimary = AccentContent,
            primaryContainer = NeonViolet,
            onPrimaryContainer = TextPrimary,
            secondary = NeonPurple,
            onSecondary = AccentContent,
            secondaryContainer = DarkSurfaceElevated,
            onSecondaryContainer = TextPrimary,
            tertiary = NeonPink,
            onTertiary = AccentContent,
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = TextSecondary,
            outline = DarkBorder
        ) else lightColorScheme(
            primary = accentColor,
            onPrimary = AccentContent,
            primaryContainer = accentColor.copy(alpha = 0.14f),
            onPrimaryContainer = TextPrimary,
            secondary = NeonPurple,
            onSecondary = AccentContent,
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = TextPrimary,
            tertiary = NeonPink,
            onTertiary = AccentContent,
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = TextSecondary,
            outline = DarkBorder
        ),
        typography = Typography,
        content = content
    )
}

