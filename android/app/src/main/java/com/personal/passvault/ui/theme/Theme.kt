package com.personal.passvault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.passvault.data.repo.ThemeMode

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFF8B85FF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E2FF),
    onPrimaryContainer = Color(0xFF17125C),
    secondary = Color(0xFF0E9F8E),
    secondaryContainer = Color(0xFFCDF3EC),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFE08A00),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEEF0F5),
    onSurfaceVariant = Color(0xFF5D6170),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F2F7),
    surfaceContainerHighest = Color(0xFFE8EAF0),
    outline = Color(0xFFC8CBD6),
    outlineVariant = Color(0xFFE3E5EC),
    error = Color(0xFFD93A3A),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF14104A),
    primaryContainer = Color(0xFF2E2A7A),
    onPrimaryContainer = Color(0xFFE4E2FF),
    secondary = Color(0xFF4FD6C3),
    secondaryContainer = Color(0xFF00463B),
    onSecondaryContainer = Color(0xFFB7F2E8),
    tertiary = Color(0xFFFFB648),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EF),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE6E8EF),
    surfaceVariant = Color(0xFF20242D),
    onSurfaceVariant = Color(0xFF9DA3B4),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1D2129),
    surfaceContainerHighest = Color(0xFF252A34),
    outline = Color(0xFF3A404D),
    outlineVariant = Color(0xFF2A2F39),
    error = Color(0xFFFF6B6B),
)

private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color(0xFF0B0C0F),
    surfaceContainer = Color(0xFF0B0C0F),
    surfaceContainerHigh = Color(0xFF121418),
    surfaceContainerHighest = Color(0xFF191C21),
    surfaceVariant = Color(0xFF15171C),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Monospace style for passwords and recovery codes (easy to tell 0/O and l/1 apart). */
val SecretTextStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontSize = 16.sp,
    letterSpacing = 0.5.sp,
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun PassVaultTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    var scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (themeMode == ThemeMode.AMOLED) scheme = scheme.amoled()

    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

fun isDarkScheme(scheme: ColorScheme): Boolean {
    val bg = scheme.background
    return (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue) < 0.5
}
