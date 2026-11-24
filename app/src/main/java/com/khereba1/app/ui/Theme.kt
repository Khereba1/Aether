package com.khereba1.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.khereba1.app.R

val TerracottaLight = Color(0xFFC96442)
val TerracottaDark = Color(0xFFD97757)

val AetherFont = FontFamily(
    Font(R.font.noto_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_sans_arabic_bold, FontWeight.Medium),
    Font(R.font.noto_sans_arabic_bold, FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic_bold, FontWeight.Bold)
)

private val LightColors = lightColorScheme(
    primary = TerracottaLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E3DA),
    onPrimaryContainer = Color(0xFF4A1D0E),
    secondary = Color(0xFF6B655C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBE6DC),
    onSecondaryContainer = Color(0xFF2B2620),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF0ECE2),
    onSurfaceVariant = Color(0xFF5C574F),
    outline = Color(0xFFDCD6C9),
    outlineVariant = Color(0xFFE8E3D9),
    error = Color(0xFFB9382C),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = Color(0xFF1C1B18),
    primaryContainer = Color(0xFF4B281B),
    onPrimaryContainer = Color(0xFFF8D8C9),
    secondary = Color(0xFFA6A095),
    onSecondary = Color(0xFF1C1B18),
    secondaryContainer = Color(0xFF312F29),
    onSecondaryContainer = Color(0xFFE6E1D6),
    background = Color(0xFF1C1B18),
    onBackground = Color(0xFFF5F2EB),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFF5F2EB),
    surfaceVariant = Color(0xFF282621),
    onSurfaceVariant = Color(0xFFA6A095),
    outline = Color(0xFF3E3B34),
    outlineVariant = Color(0xFF2F2D27),
    error = Color(0xFFE57368),
    onError = Color(0xFF1C1B18)
)

private val AetherTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AetherFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun AetherTheme(
    themeMode: String = "system",
    language: String = "en",
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val direction = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AetherTypography,
            content = content
        )
    }
}
