package com.hackerdjakodjo.mypdfwriter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InkBlue = Color(0xFF1F4E79)
val StudyOrange = Color(0xFF9A562F)
val Paper = Color(0xFFF7F5F0)

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6F3),
    onPrimaryContainer = Color(0xFF0A2236),
    secondary = StudyOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E3D4),
    onSecondaryContainer = Color(0xFF3E1D07),
    tertiary = Color(0xFF3F6B54),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFDFCF8),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFEAE5DB),
    onSurfaceVariant = Color(0xFF4B4A44),
    surfaceDim = Color(0xFFDDD8CE),
    outline = Color(0xFF7B786F),
    outlineVariant = Color(0xFFCBC6BA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C9E8),
    onPrimary = Color(0xFF0C314F),
    primaryContainer = Color(0xFF17466B),
    onPrimaryContainer = Color(0xFFD8E6F3),
    secondary = Color(0xFFE0AF8B),
    onSecondary = Color(0xFF43220C),
    secondaryContainer = Color(0xFF5E3920),
    onSecondaryContainer = Color(0xFFF6E3D4),
    tertiary = Color(0xFF9CC7AC),
    onTertiary = Color(0xFF103524),
    background = Color(0xFF11151A),
    onBackground = Color(0xFFE5E2DC),
    surface = Color(0xFF171C22),
    onSurface = Color(0xFFE5E2DC),
    surfaceVariant = Color(0xFF242B33),
    onSurfaceVariant = Color(0xFFC4C0B8),
    surfaceDim = Color(0xFF0E1216),
    outline = Color(0xFF8F8A80),
    outlineVariant = Color(0xFF3B4148),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)

@Composable
fun MyPdfWriterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
