package com.example.facedetector

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0D2B5C)
val Blue = Color(0xFF2F6FED)
val SkyBlue = Color(0xFF4FC3F7)
val BgLight = Color(0xFFF4F7FC)
val BgDark = Color(0xFF0A192F)
val SurfaceDark = Color(0xFF112240)
val StatusGreen = Color(0xFF10B981)
val StatusGray = Color(0xFF94A3B8)
val StatusRed = Color(0xFFEF4444)
val StatusAmber = Color(0xFFF59E0B)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Navy,
    secondary = SkyBlue,
    onSecondary = Color.Black,
    background = BgLight,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF334155),
    error = StatusRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = SkyBlue,
    onSecondary = Color.Black,
    background = BgDark,
    onBackground = Color(0xFFE2E8F0),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = StatusRed,
    onError = Color.White
)

@Composable
fun FaceDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally OFF per requirements to maintain brand navy/blue look
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
