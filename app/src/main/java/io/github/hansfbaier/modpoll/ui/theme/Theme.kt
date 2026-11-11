package io.github.hansfbaier.modpoll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentCyan = Color(0xFF38BDF8)
val AccentLime = Color(0xFFA3E635)
val AccentAmber = Color(0xFFFBBF24)
val AccentRose = Color(0xFFFB7185)
val AccentViolet = Color(0xFFA78BFA)
val AccentTeal = Color(0xFF2DD4BF)
val AccentBlue = Color(0xFF60A5FA)

val MonoFamily = FontFamily.Monospace

private val DarkColors =
    darkColorScheme(
        primary = AccentCyan,
        onPrimary = Color(0xFF04121F),
        primaryContainer = Color(0xFF0B3A55),
        onPrimaryContainer = Color(0xFFC7EEFF),
        secondary = AccentLime,
        onSecondary = Color(0xFF152000),
        secondaryContainer = Color(0xFF2A3D0B),
        onSecondaryContainer = Color(0xFFE6FFB3),
        tertiary = AccentAmber,
        onTertiary = Color(0xFF2A1A00),
        tertiaryContainer = Color(0xFF4A3200),
        onTertiaryContainer = Color(0xFFFFE7B8),
        background = Color(0xFF070D18),
        onBackground = Color(0xFFE7EEF9),
        surface = Color(0xFF0E1828),
        onSurface = Color(0xFFE7EEF9),
        surfaceVariant = Color(0xFF17263C),
        onSurfaceVariant = Color(0xFF9DB0C9),
        surfaceContainer = Color(0xFF122033),
        surfaceContainerHigh = Color(0xFF17283F),
        surfaceContainerHighest = Color(0xFF1C3049),
        surfaceContainerLow = Color(0xFF0B1626),
        surfaceContainerLowest = Color(0xFF060C16),
        outline = Color(0xFF2B3F5C),
        outlineVariant = Color(0xFF1D2E45),
        error = AccentRose,
        onError = Color(0xFF3A000E),
        errorContainer = Color(0xFF54101F),
        onErrorContainer = Color(0xFFFFD9DF),
        inverseSurface = Color(0xFFE7EEF9),
        inverseOnSurface = Color(0xFF122033),
        scrim = Color(0xFF000000),
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0369A1),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDEBFF),
        onPrimaryContainer = Color(0xFF001E30),
        secondary = Color(0xFF4D7C0F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4F8C4),
        onSecondaryContainer = Color(0xFF142400),
        tertiary = Color(0xFF9A6700),
        onTertiary = Color.White,
        background = Color(0xFFF5F8FC),
        onBackground = Color(0xFF101A26),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF101A26),
        surfaceVariant = Color(0xFFE6EDF6),
        onSurfaceVariant = Color(0xFF516175),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8FAFD),
        surfaceContainer = Color(0xFFF1F5FA),
        surfaceContainerHigh = Color(0xFFE9F0F8),
        surfaceContainerHighest = Color(0xFFDFE8F3),
        outline = Color(0xFFB6C4D5),
        outlineVariant = Color(0xFFD9E3EF),
        inverseSurface = Color(0xFF1B2736),
        inverseOnSurface = Color(0xFFF1F5FA),
        error = Color(0xFFC0293F),
        onError = Color.White,
        errorContainer = Color(0xFFFFDADF),
        onErrorContainer = Color(0xFF41000B),
    )

private val AppTypography =
    Typography(
        headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 14.sp),
        bodySmall = TextStyle(fontSize = 12.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
    )

@Composable
fun ModPollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
