package com.solovision.openclawagents.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import com.solovision.openclawagents.model.AppThemeMode

enum class AppThemeCategory {
    Dark,
    Light,
    Special
}

data class AppThemePalette(
    val id: AppThemeMode,
    val name: String,
    val description: String,
    val category: AppThemeCategory,
    val colors: ThemeColors
)

data class ThemeColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val bgCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentLight: Color,
    val accentSoft: Color,
    val border: Color,
    val borderLight: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color
)

val appThemes = listOf(
    AppThemePalette(
        id = AppThemeMode.Midnight,
        name = "Midnight",
        description = "Deep slate grays",
        category = AppThemeCategory.Dark,
        colors = ThemeColors(
            bgPrimary = Color(0xFF0F1115),
            bgSecondary = Color(0xFF161922),
            bgTertiary = Color(0xFF1E212B),
            bgCard = Color(0xFF252A36),
            textPrimary = Color(0xFFF0F1F5),
            textSecondary = Color(0xFFA0A3B1),
            textMuted = Color(0xFF5A5E6E),
            accent = Color(0xFF60A5FA),
            accentLight = Color(0xFF93C5FD),
            accentSoft = Color(0x2660A5FA),
            border = Color(0xFF2A2E3A),
            borderLight = Color(0xFF3A3F4D),
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            error = Color(0xFFFB7185),
            info = Color(0xFF60A5FA)
        )
    ),
    AppThemePalette(AppThemeMode.Obsidian, "Obsidian", "Pure black elegance", AppThemeCategory.Dark, ThemeColors(Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF1A1A1A), White, Color(0xFFA3A3A3), Color(0xFF737373), White, Color(0xFFE5E5E5), Color(0x1AFFFFFF), Color(0xFF262626), Color(0xFF404040), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF3B82F6))),
    AppThemePalette(AppThemeMode.Nord, "Nord", "Nordic blue-gray", AppThemeCategory.Dark, ThemeColors(Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E), Color(0xFF4C566A), Color(0xFFECEFF4), Color(0xFFD8DEE9), Color(0xFF81A1C1), Color(0xFF88C0D0), Color(0xFF8FBCBB), Color(0x3388C0D0), Color(0xFF434C5E), Color(0xFF4C566A), Color(0xFFA3BE8C), Color(0xFFEBCB8B), Color(0xFFBF616A), Color(0xFF5E81AC))),
    AppThemePalette(AppThemeMode.Dracula, "Dracula", "Purple & pink", AppThemeCategory.Dark, ThemeColors(Color(0xFF282A36), Color(0xFF44475A), Color(0xFF6272A4), Color(0xFF44475A), Color(0xFFF8F8F2), Color(0xFFBD93F9), Color(0xFF6272A4), Color(0xFFFF79C6), Color(0xFFFFB86C), Color(0x33FF79C6), Color(0xFF44475A), Color(0xFF6272A4), Color(0xFF50FA7B), Color(0xFFF1FA8C), Color(0xFFFF5555), Color(0xFF8BE9FD))),
    AppThemePalette(AppThemeMode.TokyoNight, "Tokyo Night", "Neon blue accents", AppThemeCategory.Dark, ThemeColors(Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFF2A2F45), Color(0xFF343A52), Color(0xFFC0CAF5), Color(0xFF7AA2F7), Color(0xFF565F89), Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0x337AA2F7), Color(0xFF2A2F45), Color(0xFF343A52), Color(0xFF9ECE6A), Color(0xFFE0AF68), Color(0xFFF7768E), Color(0xFF73DACA))),
    AppThemePalette(AppThemeMode.Monokai, "Monokai", "Warm & vibrant", AppThemeCategory.Dark, ThemeColors(Color(0xFF272822), Color(0xFF383830), Color(0xFF49483E), Color(0xFF57584F), Color(0xFFF8F8F2), Color(0xFFA6E22E), Color(0xFF75715E), Color(0xFFFD971F), Color(0xFFF4BF75), Color(0x33FD971F), Color(0xFF49483E), Color(0xFF57584F), Color(0xFFA6E22E), Color(0xFFE6DB74), Color(0xFFF92672), Color(0xFF66D9EF))),
    AppThemePalette(AppThemeMode.Catppuccin, "Catppuccin Mocha", "Soft pastels", AppThemeCategory.Dark, ThemeColors(Color(0xFF1E1E2E), Color(0xFF302D41), Color(0xFF414358), Color(0xFF45475A), Color(0xFFCDD6F4), Color(0xFFB4BEFE), Color(0xFF7F849C), Color(0xFFF38BA8), Color(0xFFFAB387), Color(0x33F38BA8), Color(0xFF414358), Color(0xFF585B70), Color(0xFFA6E3A1), Color(0xFFF9E2AF), Color(0xFFF38BA8), Color(0xFF89DCEB))),
    AppThemePalette(AppThemeMode.Snow, "Snow", "Clean & minimal", AppThemeCategory.Light, ThemeColors(Color(0xFFF8FAFC), White, Color(0xFFF1F5F9), White, Color(0xFF0F172A), Color(0xFF475569), Color(0xFF94A3B8), Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0x1F3B82F6), Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF3B82F6))),
    AppThemePalette(AppThemeMode.Latte, "Latte", "Warm cream tones", AppThemeCategory.Light, ThemeColors(Color(0xFFFAF5F0), White, Color(0xFFF5EDE4), White, Color(0xFF2D2A26), Color(0xFF6B6560), Color(0xFF9A928C), Color(0xFFF97316), Color(0xFFFB923C), Color(0x1FF97316), Color(0xFFE8E0D8), Color(0xFFF0EAE3), Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFF60A5FA))),
    AppThemePalette(AppThemeMode.RosePine, "Rose Pine Dawn", "Soft pink warmth", AppThemeCategory.Light, ThemeColors(Color(0xFFFAF4ED), Color(0xFFFFFAF3), Color(0xFFF2E9E1), Color(0xFFFFFAF3), Color(0xFF575279), Color(0xFF797593), Color(0xFF9893A5), Color(0xFFD7827E), Color(0xFFEA9D34), Color(0x26D7827E), Color(0xFFF2E9E1), Color(0xFFDFDAD9), Color(0xFF56949F), Color(0xFFF6C177), Color(0xFFB4637A), Color(0xFF286983))),
    AppThemePalette(AppThemeMode.Solarized, "Solarized Light", "Classic warm", AppThemeCategory.Light, ThemeColors(Color(0xFFFDF6E3), Color(0xFFEEE8D5), Color(0xFFE4DCC9), Color(0xFFEEE8D5), Color(0xFF073642), Color(0xFF586E75), Color(0xFF93A1A1), Color(0xFFCB4B16), Color(0xFFDC322F), Color(0x26CB4B16), Color(0xFFE4DCC9), Color(0xFFD3CBB8), Color(0xFF859900), Color(0xFFB58900), Color(0xFFDC322F), Color(0xFF268BD2))),
    AppThemePalette(AppThemeMode.Paper, "Paper", "Ultra minimal", AppThemeCategory.Light, ThemeColors(White, Color(0xFFFAFAFA), Color(0xFFF5F5F5), White, Color(0xFF171717), Color(0xFF525252), Color(0xFFA3A3A3), Color(0xFF171717), Color(0xFF404040), Color(0x14171717), Color(0xFFE5E5E5), Color(0xFFD4D4D4), Color(0xFF16A34A), Color(0xFFCA8A04), Color(0xFFDC2626), Color(0xFF2563EB))),
    AppThemePalette(AppThemeMode.SoLoVision, "SoLoVision Red", "Brand essence", AppThemeCategory.Special, ThemeColors(Color(0xFF0F0505), Color(0xFF1A0A0A), Color(0xFF2A1515), Color(0xFF351515), Color(0xFFFFE4E4), Color(0xFFFF9999), Color(0xFFB36666), Color(0xFFDC2626), Color(0xFFEF4444), Color(0x33DC2626), Color(0xFF451515), Color(0xFF552525), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFFF4444), Color(0xFF3B82F6))),
    AppThemePalette(AppThemeMode.Cyberpunk, "Cyberpunk", "Neon future", AppThemeCategory.Special, ThemeColors(Color(0xFF0D0221), Color(0xFF1A0B2E), Color(0xFF261447), Color(0xFF2E1A5E), Color(0xFFF0E6FF), Color(0xFFC77DFF), Color(0xFF7A3DB8), Color(0xFF00FF41), Color(0xFF39FF14), Color(0x3300FF41), Color(0xFF3D1F7A), Color(0xFF4D2799), Color(0xFF00FF41), Color(0xFFFFEE00), Color(0xFFFF0066), Color(0xFF00CCFF))),
    AppThemePalette(AppThemeMode.Ocean, "Ocean", "Deep teal waters", AppThemeCategory.Special, ThemeColors(Color(0xFF0C4A6E), Color(0xFF075985), Color(0xFF0369A1), Color(0xFF0EA5E9), Color(0xFFF0F9FF), Color(0xFF7DD3FC), Color(0xFF38BDF8), Color(0xFF06B6D4), Color(0xFF22D3EE), Color(0x3306B6D4), Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF38BDF8)))
)

fun getAppTheme(themeMode: AppThemeMode): AppThemePalette {
    return appThemes.firstOrNull { it.id == themeMode } ?: appThemes.first()
}

private fun ThemeColors.toColorScheme(isLight: Boolean): ColorScheme {
    return if (isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = White,
            primaryContainer = accentSoft,
            onPrimaryContainer = textPrimary,
            secondary = accentLight,
            onSecondary = White,
            tertiary = info,
            background = bgPrimary,
            onBackground = textPrimary,
            surface = bgSecondary,
            onSurface = textPrimary,
            surfaceVariant = bgTertiary,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = borderLight,
            error = error,
            onError = White
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = White,
            primaryContainer = accentSoft,
            onPrimaryContainer = textPrimary,
            secondary = accentLight,
            onSecondary = Color(0xFF0B1020),
            tertiary = info,
            background = bgPrimary,
            onBackground = textPrimary,
            surface = bgSecondary,
            onSurface = textPrimary,
            surfaceVariant = bgTertiary,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = borderLight,
            error = error,
            onError = White
        )
    }
}

@Composable
fun OpenClawAgentsTheme(
    themeMode: AppThemeMode = AppThemeMode.Midnight,
    content: @Composable () -> Unit
) {
    val theme = getAppTheme(themeMode)

    MaterialTheme(
        colorScheme = theme.colors.toColorScheme(isLight = theme.category == AppThemeCategory.Light),
        typography = Typography,
        content = content
    )
}
