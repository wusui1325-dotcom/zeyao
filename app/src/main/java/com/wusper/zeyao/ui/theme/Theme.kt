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

val Ink = Color(0xFF1C1410L)
val InkSoft = Color(0xFF3A3028L)
val Paper = Color(0xFFF6F0E6L)
val PaperDim = Color(0xFFE9E1D3L)
val Muted = Color(0xFF7A6E62L)
val Night = Color(0xFF161210L)
val NightElevated = Color(0xFF221C18L)
val Cream = Color(0xFFEDE6DAL)
val DefaultAccent = Color(0xFFB33A2BL)

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
    val dark = when (themeStyle) {
        ThemeStyle.FOLLOW_SYSTEM -> systemDark
        ThemeStyle.DARK -> true
        ThemeStyle.LIGHT -> false
        ThemeStyle.HIGH_CONTRAST -> true
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = Cream,
            onSecondary = Night,
            background = Night,
            onBackground = Cream,
            surface = NightElevated,
            onSurface = Cream,
            surfaceVariant = Color(0xFF2C241FL),
            onSurfaceVariant = Color(0xFFC9BDB0L),
            outline = Color(0xFF5A4E46L),
            error = Color(0xFFE07070L),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = InkSoft,
            onSecondary = Paper,
            background = Paper,
            onBackground = Ink,
            surface = Color(0xFFFFFBF4L),
            onSurface = Ink,
            surfaceVariant = PaperDim,
            onSurfaceVariant = InkSoft,
            outline = Color(0xFFC9BBA8L),
            error = Color(0xFF9B2C22L),
            onError = Color.White,
        )
    }
    val compact = typographyStyle == TypographyStyle.COMPACT
    val typography = if (compact) compactTypography() else editorialTypography()
    CompositionLocalProvider(LocalZeyaoColors provides ZeyaoColors(accent, dark)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun editorialTypography(): Typography {
    val serif = FontFamily.Serif
    val sans = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 56.sp),
        displayMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp),
        headlineLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    )
}

private fun compactTypography(): Typography {
    val base = editorialTypography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
        headlineLarge = base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        titleLarge = base.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
    )
}
