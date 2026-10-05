package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared palette state keeps the existing color tokens responsive to appearance settings. */
object AppThemeColors {
    var isDark by mutableStateOf(true)
        private set
    var accent by mutableStateOf(Color(0xFF3399FF))
        private set

    fun update(dark: Boolean = isDark, accentColor: Color = accent) {
        isDark = dark
        accent = accentColor
    }
}

// Immersive UI Palette (Deep Obsidian Slate & Electric Azure)
val DarkBackground get() = if (AppThemeColors.isDark) Color(0xFF0E1116) else Color(0xFFF4F6FA)
val DarkSurface get() = if (AppThemeColors.isDark) Color(0xFF161A20) else Color(0xFFFFFFFF)
val DarkSurfaceVariant get() = if (AppThemeColors.isDark) Color(0xFF1C2026) else Color(0xFFEBEFF5)
val DarkSurfaceElevated get() = if (AppThemeColors.isDark) Color(0xFF1C2026) else Color(0xFFFFFFFF)
val DarkBorder get() = if (AppThemeColors.isDark) Color(0xFF2D323A) else Color(0xFFD8DEE8)
val RadialGradientTop get() = if (AppThemeColors.isDark) Color(0xFF1E3A5F) else Color(0xFFE5EEF9)

// Accent & Glowing Highlights
val ElectricAzure get() = AppThemeColors.accent
val ElectricAzureGlow get() = AppThemeColors.accent.copy(alpha = 0.25f)
val NeonCyan get() = AppThemeColors.accent
val NeonCyanDim get() = AppThemeColors.accent.copy(alpha = 0.72f)
val AccentContent: Color
    get() {
        val color = AppThemeColors.accent
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return if (luminance > 0.58f) Color(0xFF101318) else Color.White
    }
val NeonPurple = Color(0xFF818CF8)
val NeonViolet = Color(0xFF6366F1)
val NeonPink = Color(0xFFEC4899)
val NeonAmber = Color(0xFFF59E0B)
val NeonGreen = Color(0xFF22C55E)

// Immersive Typography Palette
val TextPrimary get() = if (AppThemeColors.isDark) Color(0xFFE2E2E6) else Color(0xFF20242B)
val TextSecondary get() = if (AppThemeColors.isDark) Color(0xFF8E9299) else Color(0xFF596273)
val TextMuted get() = if (AppThemeColors.isDark) Color(0xFF707782) else Color(0xFF788293)
val TextHighlight get() = AppThemeColors.accent

val HiResGold get() = AppThemeColors.accent
val CardGradientStart get() = if (AppThemeColors.isDark) Color(0xFF1C2026) else Color(0xFFFFFFFF)
val CardGradientEnd get() = if (AppThemeColors.isDark) Color(0xFF0E1116) else Color(0xFFF4F6FA)

val PlayButtonBackground get() = if (AppThemeColors.isDark) Color(0xFFE2E2E6) else Color(0xFF20242B)
val PlayButtonContent get() = if (AppThemeColors.isDark) Color(0xFF0E1116) else Color(0xFFFFFFFF)

