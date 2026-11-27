package com.wusper.zeyao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wusper.zeyao.data.ThemeStyle
import com.wusper.zeyao.data.TypographyStyle

// Modern Black & White Flat Streamlined Palette
val MonoBlack = Color(0xFF0A0A0AL)
val MonoCharcoal = Color(0xFF1A1A1AL)
val MonoWhite = Color(0xFFFFFFFFL)
val MonoSurfaceLight = Color(0xFFFAFAFAL)
val MonoPillLight = Color(0xFFF1F1F1L)
val MonoMutedLight = Color(0xFF666666L)
val MonoBorderLight = Color(0xFFE4E4E4L)

val MonoNight = Color(0xFF090909L)
val MonoSurfaceDark = Color(0xFF121212L)
val MonoPillDark = Color(0xFF1D1D1DL)
val MonoMutedDark = Color(0xFF9E9E9EL)
val MonoBorderDark = Color(0xFF2B2B2BL)

val DefaultAccent = MonoBlack

data class ZeyaoColors(
    val accent: Color,
    val isDark: Boolean,
)

val LocalZeyaoColors = staticCompositionLocalOf { ZeyaoColors(DefaultAccent, false) }

@Composable
fun ZeyaoTheme(
    themeStyle: ThemeStyle = ThemeStyle.FOLLOW_SYSTEM,
    typographyStyle: TypographyStyle = TypographyStyle.EDITORIAL,
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val highContrast = themeStyle == ThemeStyle.HIGH_CONTRAST
    val dark = when (themeStyle) {
        ThemeStyle.FOLLOW_SYSTEM -> systemDark
        ThemeStyle.DARK, ThemeStyle.HIGH_CONTRAST -> true
        ThemeStyle.LIGHT -> false
    }
    val scheme = when {
        highContrast -> darkColorScheme(
            primary = MonoWhite,
            onPrimary = Color.Black,
            secondary = MonoWhite,
            onSecondary = Color.Black,
            background = Color.Black,
            onBackground = MonoWhite,
            surface = Color.Black,
            onSurface = MonoWhite,
            surfaceVariant = Color(0xFF141414L),
            onSurfaceVariant = MonoWhite,
            outline = MonoWhite,
            error = Color(0xFFFF6B6BL),
            onError = Color.Black,
        )
        dark -> darkColorScheme(
            primary = MonoWhite,
            onPrimary = MonoBlack,
            secondary = MonoWhite,
            onSecondary = MonoBlack,
            background = MonoNight,
            onBackground = MonoWhite,
            surface = MonoSurfaceDark,
            onSurface = MonoWhite,
            surfaceVariant = MonoPillDark,
            onSurfaceVariant = MonoMutedDark,
            outline = MonoBorderDark,
            error = Color(0xFFFF6B6BL),
            onError = MonoBlack,
        )
        else -> lightColorScheme(
            primary = MonoBlack,
            onPrimary = MonoWhite,
            secondary = MonoCharcoal,
            onSecondary = MonoWhite,
            background = MonoWhite,
            onBackground = MonoBlack,
            surface = MonoSurfaceLight,
            onSurface = MonoBlack,
            surfaceVariant = MonoPillLight,
            onSurfaceVariant = MonoMutedLight,
            outline = MonoBorderLight,
            error = Color(0xFFC62828L),
            onError = MonoWhite,
        )
    }
    val compact = typographyStyle == TypographyStyle.COMPACT
    val typography = if (compact) compactTypography() else streamlinedTypography()
    val effectiveAccent = if (dark) MonoWhite else MonoBlack
    CompositionLocalProvider(LocalZeyaoColors provides ZeyaoColors(effectiveAccent, dark)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun streamlinedTypography(): Typography {
    val sans = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.0).sp),
        displayMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
        headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
        labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
    )
}

private fun compactTypography(): Typography {
    val base = streamlinedTypography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
        headlineLarge = base.headlineLarge.copy(fontSize = 25.sp, lineHeight = 30.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        titleLarge = base.titleLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    )
}
